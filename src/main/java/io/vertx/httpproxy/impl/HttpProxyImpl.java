/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.httpproxy.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.httpproxy.HttpProxy;
import io.vertx.httpproxy.ProxyOptions;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;
import io.vertx.httpproxy.ProxyEvent;
import io.vertx.httpproxy.cache.CacheOptions;
import io.vertx.httpproxy.spi.cache.Cache;

import java.util.Date;
import java.util.function.BiFunction;
import java.util.function.Function;

public class HttpProxyImpl implements HttpProxy {

  private static final BiFunction<String, Resource, Resource> CACHE_GET_AND_VALIDATE = (key, resource) -> {
    long now = System.currentTimeMillis();
    long val = resource.timestamp + resource.maxAge;
    return val < now ? null : resource;
  };

  private final HttpClient client;
  private final boolean supportWebSocket;
  private Function<HttpServerRequest, Future<SocketAddress>> selector = req -> Future.failedFuture("No origin available");
  private final Cache<String, Resource> cache;
  private Handler<ProxyEvent> proxyRequestHandler;
  private Handler<ProxyEvent> proxyResponseHandler;

  public HttpProxyImpl(ProxyOptions options, HttpClient client) {
    CacheOptions cacheOptions = options.getCacheOptions();
    if (cacheOptions != null) {
      cache = cacheOptions.newCache();
    } else {
      cache = null;
    }
    this.client = client;
    this.supportWebSocket = options.getSupportWebSocket();

    this.proxyRequestHandler = ar -> {};
    this.proxyResponseHandler = ar -> {};
  }

  @Override
  public HttpProxy originSelector(Function<HttpServerRequest, Future<SocketAddress>> selector) {
    this.selector = selector;
    return this;
  }

  @Override
  public HttpProxy onProxyRequestCompletion(Handler<ProxyEvent> handler) {
    this.proxyRequestHandler = handler;
    return this;
  }

  @Override
  public HttpProxy onProxyResponseCompletion(Handler<ProxyEvent> handler) {
    this.proxyResponseHandler = handler;
    return this;
  }

  @Override
  public void handle(HttpServerRequest outboundRequest) {
    ProxyRequest proxyRequest = ProxyRequest.reverseProxy(outboundRequest);

    // Encoding sanity check
    Boolean chunked = HttpUtils.isChunked(outboundRequest.headers());
    if (chunked == null) {
      end(proxyRequest, 400);
      return;
    }

    // WebSocket upgrade tunneling
    if (supportWebSocket &&
        outboundRequest.version() == HttpVersion.HTTP_1_1 &&
        outboundRequest.method() == HttpMethod.GET &&
        outboundRequest.headers().contains(HttpHeaders.CONNECTION, HttpHeaders.UPGRADE, true)) {
      handleWebSocketUpgrade(proxyRequest);
      return;
    }

    ProxyContext bh;
    if (cache != null) {
      Proxy logic = new Proxy();
      CachingFilter caching = new CachingFilter();
      caching.context = logic;
      logic.context = caching;
      bh = caching;
    } else {
      bh = new Proxy();
    }

    bh.handleProxyRequest(proxyRequest);
  }

  private void handleWebSocketUpgrade(ProxyRequest proxyRequest) {
    HttpServerRequest outboundRequest = proxyRequest.outboundRequest();
    resolveOrigin(outboundRequest).onComplete(ar -> {
      if (ar.succeeded()) {
        HttpClientRequest inboundRequest = ar.result();
        inboundRequest.setMethod(HttpMethod.GET);
        inboundRequest.setURI(outboundRequest.uri());
        inboundRequest.headers().addAll(outboundRequest.headers());
        Future<HttpClientResponse> fut2 = inboundRequest.connect();
        outboundRequest.handler(inboundRequest::write);
        outboundRequest.endHandler(v -> inboundRequest.end());
        outboundRequest.resume();
        fut2.onComplete(ar2 -> {
          if (ar2.succeeded()) {
            HttpClientResponse inboundResponse = ar2.result();
            if (inboundResponse.statusCode() == 101) {
              HttpServerResponse outboundResponse = outboundRequest.response();
              outboundResponse.setStatusCode(101);
              outboundResponse.headers().addAll(inboundResponse.headers());
              Future<NetSocket> otherso = outboundRequest.toNetSocket();
              otherso.onComplete(ar3 -> {
                if (ar3.succeeded()) {
                  NetSocket outboundSocket = ar3.result();
                  NetSocket inboundSocket = inboundResponse.netSocket();
                  outboundSocket.handler(inboundSocket::write);
                  inboundSocket.handler(outboundSocket::write);
                  outboundSocket.closeHandler(v -> inboundSocket.close());
                  inboundSocket.closeHandler(v -> outboundSocket.close());
                } else {
                  // Find reproducer
                  System.err.println("Handle this case");
                  ar3.cause().printStackTrace();
                }
              });
            } else {
              // Rejection
              outboundRequest.resume();
              end(proxyRequest, inboundResponse.statusCode());
            }
          } else {
            outboundRequest.resume();
            end(proxyRequest, 502);
          }
        });
      } else {
        outboundRequest.resume();
        end(proxyRequest, 502);
      }
    });
  }

  private Future<HttpClientRequest> resolveOrigin(HttpServerRequest outboundRequest) {
    return selector.apply(outboundRequest).flatMap(server -> {
      RequestOptions requestOptions = new RequestOptions();
      requestOptions.setServer(server);
      return client.request(requestOptions);
    });
  }

  boolean revalidateResource(ProxyResponse response, Resource resource) {
    if (resource.etag != null && response.etag() != null) {
      return resource.etag.equals(response.etag());
    }
    return true;
  }

  private void end(ProxyRequest proxyRequest, int sc) {
    proxyRequest
      .response()
      .release()
      .setStatusCode(sc)
      .putHeader(HttpHeaders.CONTENT_LENGTH, "0")
      .setBody(null)
      .send();
  }

  private interface ProxyContext {

    void handleProxyRequest(ProxyRequest request);
    void handleProxyResponse(ProxyResponse response);

    void onHandleProxyRequest(ProxyEvent event);
    void onHandleProxyResponse(ProxyEvent event);

  }

  private class CachingFilter implements ProxyContext {

    private ProxyContext context;
    private Resource cached;

    @Override
    public void handleProxyRequest(ProxyRequest request) {
      if (tryHandleProxyRequestFromCache(request)) {
        return;
      }
      context.handleProxyRequest(request);
    }

    @Override
    public void handleProxyResponse(ProxyResponse response) {
      sendAndTryCacheProxyResponse(response);
    }

    @Override
    public void onHandleProxyRequest(ProxyEvent res) {
      context.onHandleProxyRequest(res);
    }

    @Override
    public void onHandleProxyResponse(ProxyEvent res) {
      context.onHandleProxyResponse(res);
    }

    private void sendAndTryCacheProxyResponse(ProxyResponse response) {

      if (cached != null && response.getStatusCode() == 304) {
        // Warning: this relies on the fact that HttpServerRequest will not send a body for HEAD
        response.release();
        cached.sendTo(response.request().response());
        onHandleProxyResponse(ProxyEvent.fromResponse(response)); // Use sendTo result
        return;
      }

      ProxyRequest request = response.request();
      if (response.publicCacheControl() && response.maxAge() > 0) {
        if (request.getMethod() == HttpMethod.GET) {
          String absoluteUri = request.absoluteURI();
          Resource res = new Resource(
            absoluteUri,
            response.getStatusCode(),
            response.getStatusMessage(),
            response.headers(),
            System.currentTimeMillis(),
            response.maxAge());
          response.bodyFilter(s -> new BufferingReadStream(s, res.content));
          // Note: we must manually call this instead of context.handleProxyResponse as we
          // have to do additional work based on the response instead of handing off
          // directly to the handler.
          ((ProxyResponseImpl)response).send(
            ar3 -> {
              if (ar3.succeeded()) {
                cache.put(absoluteUri, res);
                onHandleProxyResponse(ProxyEvent.fromResponse(response));
              } else {
                onHandleProxyResponse(ProxyEvent.failedResponse(response));
              }
            }
          );
          return;
        } else {
          if (request.getMethod() == HttpMethod.HEAD) {
            Resource resource = (Resource) cache.get(request.absoluteURI());
            if (resource != null) {
              if (!revalidateResource(response, resource)) {
                // Invalidate cache
                cache.remove(request.absoluteURI());
              }
            }
          }
        }
      }
      context.handleProxyResponse(response);
    }

    private boolean tryHandleProxyRequestFromCache(ProxyRequest proxyRequest) {

      HttpServerRequest outboundRequest = proxyRequest.outboundRequest();

      Resource resource;
      HttpMethod method = outboundRequest.method();
      if (method == HttpMethod.GET || method == HttpMethod.HEAD) {
        String cacheKey = proxyRequest.absoluteURI();
        resource = cache.computeIfPresent(cacheKey, CACHE_GET_AND_VALIDATE);
        if (resource == null) {
          return false;
        }
      } else {
        return false;
      }

      String cacheControlHeader = outboundRequest.getHeader(HttpHeaders.CACHE_CONTROL);
      if (cacheControlHeader != null) {
        CacheControl cacheControl = new CacheControl().parse(cacheControlHeader);
        if (cacheControl.maxAge() >= 0) {
          long now = System.currentTimeMillis();
          long currentAge = now - resource.timestamp;
          if (currentAge > cacheControl.maxAge() * 1000) {
            String etag = resource.headers.get(HttpHeaders.ETAG);
            if (etag != null) {
              proxyRequest.headers().set(HttpHeaders.IF_NONE_MATCH, resource.etag);
              cached = resource;
              context.handleProxyRequest(proxyRequest);
              return true;
            } else {
              return false;
            }
          }
        }
      }

      String ifModifiedSinceHeader = outboundRequest.getHeader(HttpHeaders.IF_MODIFIED_SINCE);
      if ((outboundRequest.method() == HttpMethod.GET || outboundRequest.method() == HttpMethod.HEAD) && ifModifiedSinceHeader != null && resource.lastModified != null) {
        Date ifModifiedSince = ParseUtils.parseHeaderDate(ifModifiedSinceHeader);
        if (resource.lastModified.getTime() <= ifModifiedSince.getTime()) {
          outboundRequest.response().setStatusCode(304).end();
          onHandleProxyRequest(ProxyEvent.fromRequest(proxyRequest).withStatusCode(304));
          return true;
        }
      }

      resource.sendTo(proxyRequest.response());
      onHandleProxyRequest(ProxyEvent.fromRequest(proxyRequest));
      return true;
    }
  }

  private class Proxy implements ProxyContext {

    private ProxyContext context = this;

    @Override
    public void handleProxyRequest(ProxyRequest request) {
      sendProxyRequest(request, ar -> {
        if (ar.succeeded()) {
          // Allow consumers to handle the event first
          onHandleProxyRequest(ProxyEvent.fromRequest(request));
          sendProxyResponse(ar.result());
        } else {
          onHandleProxyRequest(ProxyEvent.failedRequest(request));
        }
      });
    }

    private void sendProxyRequest(ProxyRequest proxyRequest, Handler<AsyncResult<ProxyResponse>> handler) {
      Future<HttpClientRequest> f = resolveOrigin(proxyRequest.outboundRequest());
      f.onComplete(ar -> {
        if (ar.succeeded()) {
          sendProxyRequest(proxyRequest, ar.result(), handler);
        } else {
          HttpServerRequest outboundRequest = proxyRequest.outboundRequest();
          outboundRequest.resume();
          Promise<Void> promise = Promise.promise();
          outboundRequest.exceptionHandler(promise::tryFail);
          outboundRequest.endHandler(promise::tryComplete);
          promise.future().onComplete(ar2 -> {
            end(proxyRequest, 502);
          });
          handler.handle(Future.failedFuture(ar.cause()));
        }
      });
    }

    private void sendProxyRequest(ProxyRequest proxyRequest, HttpClientRequest inboundRequest, Handler<AsyncResult<ProxyResponse>> handler) {
      ((ProxyRequestImpl)proxyRequest).send(inboundRequest, ar2 -> {
        if (ar2.succeeded()) {
          handler.handle(ar2);
        } else {
          proxyRequest.outboundRequest().response().setStatusCode(502).end();
          handler.handle(Future.failedFuture(ar2.cause()));
        }
      });
    }

    private void sendProxyResponse(ProxyResponse response) {

      // Check validity
      Boolean chunked = HttpUtils.isChunked(response.headers());
      if (chunked == null) {
        // response.request().release(); // Is it needed ???
        end(response.request(), 501);
        onHandleProxyResponse(ProxyEvent.fromResponse(response).withStatusCode(501)); // should use END future here
        return;
      }

      context.handleProxyResponse(response);
    }

    @Override
    public void handleProxyResponse(ProxyResponse response) {
      ((ProxyResponseImpl)response).send(ar -> {
        if (ar.succeeded()) this.onHandleProxyResponse(ProxyEvent.fromResponse(response));
        else this.onHandleProxyResponse(ProxyEvent.failedResponse(response));
      });
    }

    @Override
    public void onHandleProxyRequest(ProxyEvent res) {
      proxyRequestHandler.handle(res);
    }

    @Override
    public void onHandleProxyResponse(ProxyEvent res) {
      proxyResponseHandler.handle(res);
    }
  }
}
