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
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;

import java.util.function.Function;

/**
 * The general interceptor for path. Extended by other implementations.
 */
public class PathInterceptorImpl implements ProxyInterceptor {
  private final Function<String, String> pattern;

  public PathInterceptorImpl(Function<String, String> pattern) {
    this.pattern = pattern;
  }

  public static PathInterceptorImpl addPrefix(String prefix) {
    return new PathInterceptorImpl(uri -> prefix + uri);
  }

  public static PathInterceptorImpl removePrefix(String prefix) {
    return new PathInterceptorImpl(uri -> uri.startsWith(prefix) ? uri.substring(prefix.length()) : uri);
  }

  @Override
  public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
    ProxyRequest proxyRequest = context.request();
    proxyRequest.setURI(pattern.apply(proxyRequest.getURI()));
    return context.sendRequest();
  }
}
