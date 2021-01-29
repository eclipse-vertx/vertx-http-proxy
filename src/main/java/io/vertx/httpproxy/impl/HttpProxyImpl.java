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
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

public class HttpProxyImpl implements HttpProxy {

  private static final BiFunction<String, Resource, Resource> CACHE_GET_AND_VALIDATE = (key, resource) -> {
    long now = System.currentTimeMillis();
    long val = resource.timestamp + resource.maxAge;
    return val < now ? null : resource;
  };

  private final HttpClient client;
  private Function<HttpServerRequest, Future<SocketAddress>> selector = req -> Future.failedFuture("No target available");
  private final Map<String, Resource> cache = new HashMap<>();

  public HttpProxyImpl(HttpClient client) {
    this.client = client;
  }

  @Override
  public HttpProxy selector(Function<HttpServerRequest, Future<SocketAddress>> selector) {
    this.selector = selector;
    return this;
  }

  @Override
  public void handle(HttpServerRequest outboundRequest) {
    handleProxyRequest(outboundRequest);
  }

  private Future<HttpClientRequest> resolveTarget(HttpServerRequest outboundRequest) {
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
      .send(ar -> {});

  }

  private void handleProxyRequest(HttpServerRequest outboundRequest) {
    ProxyRequest proxyRequest = ProxyRequest.reverseProxy(outboundRequest);

    // Encoding check
    Boolean chunked = HttpUtils.isChunked(outboundRequest.headers());
    if (chunked == null) {
      end(proxyRequest, 400);
      return;
    }

    // Handle from cache
    HttpMethod method = outboundRequest.method();
    if (method == HttpMethod.GET || method == HttpMethod.HEAD) {
      String cacheKey = proxyRequest.absoluteURI();
      Resource resource = cache.computeIfPresent(cacheKey, CACHE_GET_AND_VALIDATE);
      if (resource != null) {
        if (tryHandleProxyRequestFromCache(proxyRequest, resource)) {
          return;
        }
      }
    }
    handleProxyRequestAndProxyResponse(proxyRequest);
  }

  private void handleProxyRequestAndProxyResponse(ProxyRequest proxyRequest) {
    handleProxyRequest(proxyRequest, ar -> {
      if (ar.succeeded()) {
        handleProxyResponse(ar.result(), ar2 -> {});
      } else {
        // TODO ???
      }
    });
  }

  private void handleProxyRequest(ProxyRequest proxyRequest, Handler<AsyncResult<ProxyResponse>> handler) {
    Future<HttpClientRequest> f = resolveTarget(proxyRequest.outboundRequest());
    f.onComplete(ar -> {
      if (ar.succeeded()) {
        handleProxyRequest(proxyRequest, ar.result(), handler);
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

  private void handleProxyRequest(ProxyRequest proxyRequest, HttpClientRequest inboundRequest, Handler<AsyncResult<ProxyResponse>> handler) {
    proxyRequest.send(inboundRequest, ar2 -> {
      if (ar2.succeeded()) {
        handler.handle(ar2);
      } else {
        proxyRequest.outboundRequest().response().setStatusCode(502).end();
        handler.handle(Future.failedFuture(ar2.cause()));
      }
    });
  }

  private void handleProxyResponse(ProxyResponse response, Handler<AsyncResult<Void>> completionHandler) {

    // Check validity
    Boolean chunked = HttpUtils.isChunked(response.headers());
    if (chunked == null) {
      // response.request().release(); // Is it needed ???
      end(response.request(), 501);
      completionHandler.handle(Future.failedFuture("TODO"));
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
            continueHandleResponse(response, completionHandler);
          } else {
            System.out.println("Not implemented");
          }
        });
        return;
      }
    }
    continueHandleResponse(response, completionHandler);
  }

  private void continueHandleResponse(ProxyResponse response, Handler<AsyncResult<Void>> completionHandler) {
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
          completionHandler.handle(ar3);
          if (ar3.succeeded()) {
            cache.put(absoluteUri, res);
          }
        };
      } else {
        if (request.getMethod() == HttpMethod.HEAD) {
          Resource resource = cache.get(request.absoluteURI());
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

    response.send(handler);
  }

  private boolean tryHandleProxyRequestFromCache(ProxyRequest proxyRequest, Resource resource) {
    HttpServerRequest outboundRequest = proxyRequest.outboundRequest();
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
            handleProxyRequest(proxyRequest, ar -> {
              if (ar.succeeded()) {
                ProxyResponse proxyResp = ar.result();
                int sc = proxyResp.getStatusCode();
                switch (sc) {
                  case 200:
                    handleProxyResponse(proxyResp, ar2 -> {});
                    break;
                  case 304:
                    // Warning: this relies on the fact that HttpServerRequest will not send a body for HEAD
                    proxyResp.release();
                    resource.sendTo(proxyRequest.response());
                    break;
                  default:
                    System.out.println("Not implemented");
                    break;
                }
              } else {
                System.out.println("Not implemented");
              }
            });
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
        return true;
      }
    }

    resource.sendTo(proxyRequest.response());
    return true;
  }
}
