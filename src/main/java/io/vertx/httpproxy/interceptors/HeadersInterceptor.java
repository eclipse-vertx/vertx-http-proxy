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

package io.vertx.httpproxy.interceptors;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.interceptors.impl.HeadersInterceptorImpl;

import java.util.Set;

import static io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE;

/**
 * Used to create interceptors to modify request and response headers.
 */
@VertxGen
@Unstable
public interface HeadersInterceptor {

  /**
   * Apply callbacks to change the request and response headers when the proxy receives them.
   *
   * @param changeRequestHeaders the operation to apply to the request headers
   * @param changeResponseHeaders the operation to apply to the response headers
   * @return the created interceptor
   */
  static ProxyInterceptor changeHeaders(Handler<MultiMap> changeRequestHeaders, Handler<MultiMap> changeResponseHeaders) {
    return new HeadersInterceptorImpl(changeRequestHeaders, changeResponseHeaders);
  }

  /**
   * Filter the request headers in the given set.
   *
   * @param requestHeaders a set of the headers that need to be filtered
   * @return the created interceptor
   */
  @GenIgnore(PERMITTED_TYPE)
  static ProxyInterceptor filterRequestHeaders(Set<CharSequence> requestHeaders) {
    return HeadersInterceptorImpl.filter(requestHeaders, null);
  }

  /**
   * Filter the response headers in the given set.
   *
   * @param responseHeaders a set of the headers that need to be filtered
   * @return the created interceptor
   */
  @GenIgnore(PERMITTED_TYPE)
  static ProxyInterceptor filterResponseHeaders(Set<CharSequence> responseHeaders) {
    return HeadersInterceptorImpl.filter(null, responseHeaders);
  }

  /**
   * Filter the request and response headers in the given sets.
   *
   * @param requestHeaders a set of the request headers that need to be filtered
   * @param responseHeaders a set of the response headers that need to be filtered
   * @return the created interceptor
   */
  @GenIgnore(PERMITTED_TYPE)
  static ProxyInterceptor filterHeaders(Set<CharSequence> requestHeaders, Set<CharSequence> responseHeaders) {
    return HeadersInterceptorImpl.filter(requestHeaders, responseHeaders);
  }
}
