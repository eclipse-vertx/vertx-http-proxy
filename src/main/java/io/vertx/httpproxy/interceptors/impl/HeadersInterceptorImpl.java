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

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;

import java.util.function.Consumer;

/**
 * The general interceptor for headers. Extended by other implementations.
 */
public class HeadersInterceptorImpl implements ProxyInterceptor {
  Consumer<MultiMap> changeRequestHeaders;
  Consumer<MultiMap> changeResponseHeaders;

  public HeadersInterceptorImpl(Consumer<MultiMap> changeRequestHeaders, Consumer<MultiMap> changeResponseHeaders) {
    this.changeRequestHeaders = changeRequestHeaders;
    this.changeResponseHeaders = changeResponseHeaders;
  }

  @Override
  public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
    ProxyRequest request = context.request();
    changeRequestHeaders.accept(request.headers());
    return context.sendRequest();
  }

  @Override
  public Future<Void> handleProxyResponse(ProxyContext context) {
    ProxyResponse response = context.response();
    changeResponseHeaders.accept(response.headers());
    return context.sendResponse();
  }
}
