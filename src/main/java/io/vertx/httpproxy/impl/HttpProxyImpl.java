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
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.HttpProxy;
import io.vertx.httpproxy.ProxyOptions;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;
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
  private Function<HttpServerRequest, Future<SocketAddress>> selector = req -> Future.failedFuture("No origin available");
  private final Cache<String, Resource> cache;

  public HttpProxyImpl(ProxyOptions options, HttpClient client) {
    CacheOptions cacheOptions = options.getCacheOptions();
    if (cacheOptions != null) {
      cache = cacheOptions.newCache();
    } else {
      cache = null;
    }
    this.client = client;
  }

  @Override
  public HttpProxy originSelector(Function<HttpServerRequest, Future<SocketAddress>> selector) {
    this.selector = selector;
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

    bh.handleProxyRequest(proxyRequest, ar -> {});
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

    void handleProxyRequest(ProxyRequest request, Handler<AsyncResult<Void>> handler);
    void handleProxyResponse(ProxyResponse response, Handler<AsyncResult<Void>> handler);

  }

  private class CachingFilter implements ProxyContext {

    private ProxyContext context;
    private Resource cached;

    @Override
    public void handleProxyRequest(ProxyRequest request, Handler<AsyncResult<Void>> handler) {
      if (tryHandleProxyRequestFromCache(request, handler)) {
        return;
      }
      context.handleProxyRequest(request, handler);
    }

    @Override
    public void handleProxyResponse(ProxyResponse response, Handler<AsyncResult<Void>> handler) {
      sendAndTryCacheProxyResponse(response, handler);
    }

    private void sendAndTryCacheProxyResponse(ProxyResponse response, Handler<AsyncResult<Void>> completionHandler) {

      if (cached != null && response.getStatusCode() == 304) {
        // Warning: this relies on the fact that HttpServerRequest will not send a body for HEAD
        response.release();
        cached.sendTo(response.request().response());
        completionHandler.handle(Future.succeededFuture()); // Use sendTo result
        return;
      }

      ProxyRequest request = response.request();
      Handler<AsyncResult<Void>> handler;
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
          handler = ar3 -> {
            if (ar3.succeeded()) {
              cache.put(absoluteUri, res);
            }
            completionHandler.handle(ar3);
          };
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
          handler = completionHandler;
        }
      } else {
        handler = completionHandler;
      }
      context.handleProxyResponse(response, handler);
    }

    private boolean tryHandleProxyRequestFromCache(ProxyRequest proxyRequest, Handler<AsyncResult<Void>> handler) {

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
              context.handleProxyRequest(proxyRequest, handler);
              return true;
            } else {
              return false;
            }
          }
        }
      }

      //
      String ifModifiedSinceHeader = outboundRequest.getHeader(HttpHeaders.IF_MODIFIED_SINCE);
      if ((outboundRequest.method() == HttpMethod.GET || outboundRequest.method() == HttpMethod.HEAD) && ifModifiedSinceHeader != null && resource.lastModified != null) {
        Date ifModifiedSince = ParseUtils.parseHeaderDate(ifModifiedSinceHeader);
        if (resource.lastModified.getTime() <= ifModifiedSince.getTime()) {
          outboundRequest.response().setStatusCode(304).end();
          handler.handle(Future.succeededFuture());
          return true;
        }
      }

      resource.sendTo(proxyRequest.response());
      handler.handle(Future.succeededFuture());
      return true;
    }
  }

  private class Proxy implements ProxyContext {

    private ProxyContext context = this;

    @Override
    public void handleProxyRequest(ProxyRequest request, Handler<AsyncResult<Void>> handler) {
      sendProxyRequest(request, ar -> {
        if (ar.succeeded()) {
          sendProxyResponse(ar.result(), ar2 -> {});
          handler.handle(Future.succeededFuture());
        } else {
          handler.handle(Future.failedFuture(ar.cause()));
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

    private void sendProxyResponse(ProxyResponse response, Handler<AsyncResult<Void>> handler) {

      // Check validity
      Boolean chunked = HttpUtils.isChunked(response.headers());
      if (chunked == null) {
        // response.request().release(); // Is it needed ???
        end(response.request(), 501);
        handler.handle(Future.succeededFuture()); // should use END future here
        return;
      }

      if (chunked && response.request().version() == HttpVersion.HTTP_1_0) {
        String contentLength = response.headers().get(HttpHeaders.CONTENT_LENGTH);
        if (contentLength == null) {
          // Special handling for HTTP 1.0 clients that cannot handle chunked encoding
          Body body = response.getBody();
          response.release();
          BufferingWriteStream buffer = new BufferingWriteStream();
          body.stream().pipeTo(buffer, ar -> {
            if (ar.succeeded()) {
              Buffer content = buffer.content();
              response.setBody(Body.body(content));
              context.handleProxyResponse(response, handler);
            } else {
              System.out.println("Not implemented");
            }
          });
          return;
        }
      }

      context.handleProxyResponse(response, handler);
    }


    @Override
    public void handleProxyResponse(ProxyResponse response, Handler<AsyncResult<Void>> handler) {
      ((ProxyResponseImpl)response).send(handler);
    }
  }
}
