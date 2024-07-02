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
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.interceptors.impl.QueryInterceptorImpl;


/**
 * The general interceptor for query parameters.
 */
@VertxGen
@Unstable
public interface QueryInterceptor {

  /**
   * Apply callbacks to modify the query parameters.
   *
   * @param changeQueries the operation to apply to the request query parameters
   * @return the created interceptor
   */
  static ProxyInterceptor changeQueryParams(Handler<MultiMap> changeQueries) {
    return new QueryInterceptorImpl(changeQueries);
  }

  /**
   * Add a query parameter to the request.
   *
   * @param name the parameter name
   * @param value the parameter value
   * @return the created interceptor
   */
  static ProxyInterceptor setQueryParam(String name, String value) {
    return new QueryInterceptorImpl(mmap -> mmap.set(name, value));
  }

  /**
   * Remove a query parameter to the request.
   *
   * @param name the parameter name
   * @return the created interceptor
   */
  static ProxyInterceptor removeQueryParam(String name) {
    return new QueryInterceptorImpl(mmap -> mmap.remove(name));
  }
}
