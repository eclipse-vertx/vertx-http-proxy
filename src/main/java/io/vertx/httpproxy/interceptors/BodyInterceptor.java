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
import io.vertx.httpproxy.interceptors.impl.BodyInterceptorImpl;

/**
 * Used to create interceptors to modify request and response bodies.
 */
@VertxGen
@Unstable
public interface BodyInterceptor {

  /**
   * Apply callbacks to change the request and response bodies when the proxy receives them.
   *
   * @param requestTransformer the operation to apply to the request body
   * @param responseTransformer the operation to apply to the response body
   * @return the created interceptor
   */
  static ProxyInterceptor modifyBody(BodyTransformer requestTransformer, BodyTransformer responseTransformer) {
    return new BodyInterceptorImpl(requestTransformer, responseTransformer);
  }

  /**
   * Apply callbacks to change the request body when the proxy receives it.
   *
   * @param requestTransformer the operation to apply to the request body
   * @return the created interceptor
   */
  static ProxyInterceptor modifyRequestBody(BodyTransformer requestTransformer) {
    return new BodyInterceptorImpl(requestTransformer, null);
  }

  /**
   * Apply callbacks to change the response body when the proxy receives it.
   *
   * @param responseTransformer the operation to apply to the response body
   * @return the created interceptor
   */
  static ProxyInterceptor modifyResponseBody(BodyTransformer responseTransformer) {
    return new BodyInterceptorImpl(null, responseTransformer);
  }
}
