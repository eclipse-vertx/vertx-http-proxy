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
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.interceptors.impl.HeadersInterceptorImpl;

import java.util.Set;

import static io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE;

@VertxGen
public interface HeadersInterceptor {

  @GenIgnore(PERMITTED_TYPE)
  static ProxyInterceptor filterRequestHeaders(Set<CharSequence> requestHeaders) {
    return new HeadersInterceptorImpl(); // FIXME
  }

  @GenIgnore(PERMITTED_TYPE)
  static ProxyInterceptor filterResponseHeaders(Set<CharSequence> responseHeaders) {
    return new HeadersInterceptorImpl(); // FIXME
  }

  @GenIgnore(PERMITTED_TYPE)
  static ProxyInterceptor filterHeaders(Set<CharSequence> requestHeaders, Set<CharSequence> responseHeaders) {
    return new HeadersInterceptorImpl(); // FIXME
  }
}
