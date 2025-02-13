/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
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
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.internal.CloseFuture;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.http.HttpClientInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.streams.ReadStream;
import io.vertx.httpproxy.HttpProxy;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyOptions;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;
import io.vertx.httpproxy.cache.CacheOptions;
import io.vertx.httpproxy.spi.cache.Cache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

import static io.vertx.core.http.HttpHeaders.CONNECTION;
import static io.vertx.core.http.HttpHeaders.UPGRADE;

public class ReverseProxy implements HttpProxy {

  private final static Logger log = LoggerFactory.getLogger(ReverseProxy.class);
  private final HttpClient client;
  private final boolean supportWebSocket;
  private BiFunction<ProxyContext, HttpClient, Future<HttpClientRequest>> selector = (req, client) -> Future.failedFuture("No origin available");
  private final List<ProxyInterceptorEntry> interceptors = new ArrayList<>();

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
    if (options.isShared()) {
      CloseFuture closeFuture = new CloseFuture();
      return ((VertxInternal) vertx).createSharedResource("__vertx.shared.proxyCache", options.getName(), closeFuture, (cf_) -> {
        return new CacheImpl(options);
      });
    }
    return new CacheImpl(options);
  }

  @Override
  public HttpProxy originRequestProvider(BiFunction<ProxyContext, HttpClient, Future<HttpClientRequest>> provider) {
    selector = provider;
    return this;
  }

  @Override
  public HttpProxy addInterceptor(ProxyInterceptor interceptor, boolean supportsWebSocketUpgrade) {
    interceptors.add(new ProxyInterceptorEntry(Objects.requireNonNull(interceptor), supportsWebSocketUpgrade));
    return this;
  }

  @Override
  public void handle(HttpServerRequest request, Map<String, Object> attachments) {
    ProxyRequest proxyRequest = ProxyRequest.reverseProxy(request);

    // Encoding sanity check
    Boolean chunked = HttpUtils.isChunked(request.headers());
    if (chunked == null) {
      end(proxyRequest, 400);
      return;
    }

    boolean isWebSocket = supportWebSocket && request.canUpgradeToWebSocket();
    Proxy proxy = new Proxy(proxyRequest, isWebSocket, attachments);
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

  private Future<HttpClientRequest> resolveOrigin(ProxyContext context) {
    return selector.apply(context, client);
  }

  private class Proxy implements ProxyContext {

    private final ProxyRequest request;
    private ProxyResponse response;
    private final Map<String, Object> attachments;
    private final ListIterator<ProxyInterceptorEntry> filters;
    private final boolean isWebSocket;

    private Proxy(ProxyRequest request, boolean isWebSocket, Map<String, Object> attachments) {
      this.request = request;
      this.isWebSocket = isWebSocket;
      this.filters = interceptors.listIterator();
      this.attachments = attachments;
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
        ProxyInterceptorEntry next = filters.next();
        if (isWebSocket && !next.supportsWebSocketUpgrade) {
          return sendRequest();
        }
        return next.interceptor.handleProxyRequest(this);
      } else {
        if (isWebSocket) {
          HttpServerRequest proxiedRequest = request().proxiedRequest();
          return resolveOrigin(this).compose(request -> {
            request.setMethod(request().getMethod());
            request.setURI(request().getURI());
            // Firefox is known to send an unexpected connection header value
            // Connection=keep-alive, Upgrade
            // It leads to a failure in websocket proxying
            // So we make sure the standard value is sent to the backend
            request.headers().addAll(request().headers()).set(CONNECTION, UPGRADE);
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
        ProxyInterceptorEntry previous = filters.previous();
        if (isWebSocket && !previous.supportsWebSocketUpgrade) {
          return sendResponse();
        }
        return previous.interceptor.handleProxyResponse(this);
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
      return resolveOrigin(this).compose(proxyRequest::send);
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

  private static class ProxyInterceptorEntry {

    final ProxyInterceptor interceptor;
    final boolean supportsWebSocketUpgrade;

    ProxyInterceptorEntry(ProxyInterceptor interceptor, boolean supportsWebSocketUpgrade) {
      this.interceptor = interceptor;
      this.supportsWebSocketUpgrade = supportsWebSocketUpgrade;
    }
  }
}
