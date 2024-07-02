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
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyResponse;

import java.util.Objects;

public class QueryInterceptorImpl implements ProxyInterceptor {
  private final Handler<MultiMap> changeQueryParams;

  @Override
  public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
    String rawUri = context.request().getURI();
    MultiMap params = queryParams(rawUri);
    String cleanedUri = cleanedUri(rawUri);

    changeQueryParams.handle(params);
    String newUri = buildUri(cleanedUri, params);
    context.request().setURI(newUri);
    return context.sendRequest();
  }

  public QueryInterceptorImpl(Handler<MultiMap> changeQueryParams) {
    this.changeQueryParams = Objects.requireNonNull(changeQueryParams);
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
}
