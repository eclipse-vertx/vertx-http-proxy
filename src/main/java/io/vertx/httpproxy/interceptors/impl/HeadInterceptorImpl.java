/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.httpproxy.interceptors.impl;

import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.QueryStringEncoder;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;
import io.vertx.httpproxy.interceptors.HeadInterceptor;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

class HeadInterceptorImpl implements HeadInterceptor {

  private final List<Handler<MultiMap>> queryUpdaters;
  private final List<Function<String, String>> pathUpdaters;
  private final List<Handler<MultiMap>> requestHeadersUpdaters;
  private final List<Handler<MultiMap>> responseHeadersUpdaters;

  HeadInterceptorImpl(List<Handler<MultiMap>> queryUpdaters, List<Function<String, String>> pathUpdaters, List<Handler<MultiMap>> requestHeadersUpdaters, List<Handler<MultiMap>> responseHeadersUpdaters) {
    this.queryUpdaters = Objects.requireNonNull(queryUpdaters);
    this.pathUpdaters = Objects.requireNonNull(pathUpdaters);
    this.requestHeadersUpdaters = Objects.requireNonNull(requestHeadersUpdaters);
    this.responseHeadersUpdaters = Objects.requireNonNull(responseHeadersUpdaters);
  }

  @Override
  public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
    queryHandleProxyRequest(context);
    pathHandleProxyRequest(context);
    headersHandleProxyRequest(context);
    return context.sendRequest();
  }

  @Override
  public Future<Void> handleProxyResponse(ProxyContext context) {
    headersHandleProxyResponse(context);
    return context.sendResponse();
  }

  private void queryHandleProxyRequest(ProxyContext context) {
    String rawUri = context.request().getURI();
    MultiMap params = queryParams(rawUri);
    String cleanedUri = cleanedUri(rawUri);

    for (Handler<MultiMap> queryUpdater : queryUpdaters) {
      queryUpdater.handle(params);
    }

    String newUri = buildUri(cleanedUri, params);
    context.request().setURI(newUri);
  }

  // ref: https://github.com/vert-x3/vertx-web/blob/master/vertx-web-client/src/main/java/io/vertx/ext/web/client/impl/HttpRequestImpl.java
  private static MultiMap queryParams(String uri) {
    MultiMap queryParams = MultiMap.caseInsensitiveMultiMap();
    int idx = uri.indexOf('?');
    if (idx >= 0) {
      QueryStringDecoder dec = new QueryStringDecoder(uri);
      dec.parameters().forEach(queryParams::add);
    }
    return queryParams;
  }

  // ref: https://github.com/vert-x3/vertx-web/blob/master/vertx-web-client/src/main/java/io/vertx/ext/web/client/impl/HttpRequestImpl.java
  private static String cleanedUri(String uri) {
    int idx = uri.indexOf('?');
    if (idx >= 0) {
      uri = uri.substring(0, idx);
    }
    return uri;
  }

  // ref: https://github.com/vert-x3/vertx-web/blob/master/vertx-web-client/src/main/java/io/vertx/ext/web/client/impl/HttpRequestImpl.java
  private static String buildUri(String uri, MultiMap queryParams) {
    QueryStringDecoder decoder = new QueryStringDecoder(uri);
    QueryStringEncoder encoder = new QueryStringEncoder(decoder.rawPath());
    decoder.parameters().forEach((name, values) -> {
      for (String value : values) {
        encoder.addParam(name, value);
      }
    });
    queryParams.forEach(param -> {
      encoder.addParam(param.getKey(), param.getValue());
    });
    uri = encoder.toString();
    return uri;
  }

  private void pathHandleProxyRequest(ProxyContext context) {
    ProxyRequest proxyRequest = context.request();
    for (Function<String, String> pathUpdater : pathUpdaters) {
      proxyRequest.setURI(pathUpdater.apply(proxyRequest.getURI()));
    }
  }

  private void headersHandleProxyRequest(ProxyContext context) {
    ProxyRequest request = context.request();
    for (Handler<MultiMap> requestHeadersUpdater : requestHeadersUpdaters) {
      requestHeadersUpdater.handle(request.headers());
    }
  }

  private void headersHandleProxyResponse(ProxyContext context) {
    ProxyResponse response = context.response();
    for (Handler<MultiMap> responseHeadersUpdater : responseHeadersUpdaters) {
      responseHeadersUpdater.handle(response.headers());
    }
  }
}
