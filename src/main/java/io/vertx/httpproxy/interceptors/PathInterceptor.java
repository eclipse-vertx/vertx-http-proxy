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

import io.vertx.codegen.annotations.Unstable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.interceptors.impl.PathInterceptorImpl;

import java.util.function.Function;

/**
 * Used to create interceptors to modify the request path.
 */
@VertxGen
@Unstable
public interface PathInterceptor {

  /**
   * Apply a callback to change the request URI when the proxy receives it.
   *
   * @param pattern the operation that applied to the path
   * @return the created interceptor
   */
  static ProxyInterceptor changePath(Function<String, String> pattern) {
    return new PathInterceptorImpl(pattern);
  }

  /**
   * Add a prefix to the URI.
   *
   * @param prefix the prefix that need to be added
   * @return the created interceptor
   */
  static ProxyInterceptor removePrefix(String prefix) {
    return PathInterceptorImpl.removePrefix(prefix);
  }

  /**
   * Remove a prefix to the URI. Do nothing if it doesn't exist.
   *
   * @param prefix the prefix that need to be removed
   * @return the created interceptor
   */
  static ProxyInterceptor addPrefix(String prefix) {
    return PathInterceptorImpl.addPrefix(prefix);
  }
}
