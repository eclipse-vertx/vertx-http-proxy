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

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.core.internal.CloseFuture;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.http.*;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.streams.ReadStream;
import io.vertx.httpproxy.*;
import io.vertx.httpproxy.cache.CacheOptions;
import io.vertx.httpproxy.spi.cache.Cache;

import java.util.*;
import java.util.function.BiFunction;

public class ReverseProxy implements HttpProxy {

  private final static Logger log = LoggerFactory.getLogger(ReverseProxy.class);
  private final HttpClient client;
  private final boolean supportWebSocket;
  private BiFunction<HttpServerRequest, HttpClient, Future<HttpClientRequest>> selector = (req, client) -> Future.failedFuture("No origin available");
  private final List<ProxyInterceptor> interceptors = new ArrayList<>();

  public ReverseProxy(ProxyOptions options, HttpClient client) {
    CacheOptions cacheOptions = options.getCacheOptions();
    if (cacheOptions != null) {
      Cache cache = newCache(cacheOptions, ((HttpClientInternal) client).vertx());
      addInterceptor(new CachingFilter(cache));
    }
    this.client = client;
    this.supportWebSocket = options.getSupportWebSocket();
  }

  public Cache newCache(CacheOptions options, Vertx vertx) {
    if (options.getShared()) {
      CloseFuture closeFuture = new CloseFuture();
      return ((VertxInternal) vertx).createSharedResource("__vertx.shared.proxyCache", options.getName(), closeFuture, (cf_) -> {
        Cache cache = new CacheImpl(options);
        cf_.add(completion -> {
          cache.close().onComplete(completion);
        });
        return cache;
      });
    }
    return new CacheImpl(options);
  }

  @Override
  public HttpProxy originRequestProvider(BiFunction<HttpServerRequest, HttpClient, Future<HttpClientRequest>> provider) {
    selector = provider;
    return this;
  }

  @Override
  public HttpProxy addInterceptor(ProxyInterceptor interceptor) {
    interceptors.add(interceptor);
    return this;
  }


  @Override
  public void handle(HttpServerRequest request) {
    ProxyRequest proxyRequest = ProxyRequest.reverseProxy(request);

    // Encoding sanity check
    Boolean chunked = HttpUtils.isChunked(request.headers());
    if (chunked == null) {
      end(proxyRequest, 400);
      return;
    }

    boolean isWebSocket = supportWebSocket && request.canUpgradeToWebSocket();
    Proxy proxy = new Proxy(proxyRequest, isWebSocket);
    proxy.filters = interceptors.listIterator();
    proxy.sendRequest()
      .recover(throwable -> {
        log.trace("Error in sending the request", throwable);
        return Future.succeededFuture(proxyRequest.release().response().setStatusCode(502));
      })
      .compose(proxy::sendProxyResponse)
      .recover(throwable -> {
        log.trace("Error in sending the response", throwable);
        return proxy.response().release().setStatusCode(502).send();
      });
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

  private Future<HttpClientRequest> resolveOrigin(HttpServerRequest proxiedRequest) {
    return selector.apply(proxiedRequest, client);
  }

  private class Proxy implements ProxyContext {

    private final ProxyRequest request;
    private ProxyResponse response;
    private final Map<String, Object> attachments = new HashMap<>();
    private ListIterator<ProxyInterceptor> filters;
    private final boolean isWebSocket;

    private Proxy(ProxyRequest request, boolean isWebSocket) {
      this.request = request;
      this.isWebSocket = isWebSocket;
    }

    @Override
    public boolean isWebSocket() {
      return isWebSocket;
    }

    @Override
    public void set(String name, Object value) {
      attachments.put(name, value);
    }

    @Override
    public <T> T get(String name, Class<T> type) {
      Object o = attachments.get(name);
      return type.isInstance(o) ? type.cast(o) : null;
    }

    @Override
    public ProxyRequest request() {
      return request;
    }

    @Override
    public Future<ProxyResponse> sendRequest() {
      if (filters.hasNext()) {
        ProxyInterceptor next = filters.next();
        if (isWebSocket && !next.allowApplyToWebSocket()) {
          return sendRequest();
        }
        return next.handleProxyRequest(this);
      } else {
        if (isWebSocket) {
          HttpServerRequest proxiedRequest = request().proxiedRequest();
          return resolveOrigin(proxiedRequest).compose(request -> {
            request.setMethod(request().getMethod());
            request.setURI(request().getURI());
            request.headers().addAll(request().headers());
            Future<HttpClientResponse> responseFuture = request.connect();
            ReadStream<Buffer> readStream = request().getBody().stream();
            readStream.handler(request::write);
            readStream.resume();
            proxiedRequest.resume();
            return responseFuture;
          }).map(response -> new ProxiedResponse((ProxiedRequest) request(), request().proxiedRequest().response(), response));
        }
        return sendProxyRequest(request);
      }
    }

    @Override
    public ProxyResponse response() {
      return response;
    }

    @Override
    public Future<Void> sendResponse() {
      if (filters.hasPrevious()) {
        ProxyInterceptor filter = filters.previous();
        if (isWebSocket && !filter.allowApplyToWebSocket()) {
          return sendResponse();
        }
        return filter.handleProxyResponse(this);
      } else {
        if (isWebSocket) {
          HttpClientResponse proxiedResponse = response().proxiedResponse();
          if (response.getStatusCode() == 101) {
            HttpServerResponse clientResponse = request().proxiedRequest().response();
            clientResponse.setStatusCode(101);
            clientResponse.headers().addAll(response.headers());
            Future<NetSocket> otherso = request.proxiedRequest().toNetSocket();
            otherso.onComplete(ar3 -> {
              if (ar3.succeeded()) {
                NetSocket responseSocket = ar3.result();
                NetSocket proxyResponseSocket = proxiedResponse.netSocket();
                responseSocket.handler(proxyResponseSocket::write);
                proxyResponseSocket.handler(responseSocket::write);
                responseSocket.closeHandler(v -> proxyResponseSocket.close());
                proxyResponseSocket.closeHandler(v -> responseSocket.close());
              } else {
                // Find reproducer
                System.err.println("Handle this case");
                ar3.cause().printStackTrace();
              }
            });
          } else {
            request().proxiedRequest().resume();
            end(request(), proxiedResponse.statusCode());
          }
          return Future.succeededFuture();
        }
        return response.send();
      }
    }

    private Future<ProxyResponse> sendProxyRequest(ProxyRequest proxyRequest) {
      return resolveOrigin(proxyRequest.proxiedRequest()).compose(proxyRequest::send);
    }

    private Future<Void> sendProxyResponse(ProxyResponse response) {

      this.response = response;

      // Check validity
      Boolean chunked = HttpUtils.isChunked(response.headers());
      if (chunked == null) {
        // response.request().release(); // Is it needed ???
        end(response.request(), 501);
        return Future.succeededFuture(); // should use END future here ???
      }

      return sendResponse();
    }
  }
}
