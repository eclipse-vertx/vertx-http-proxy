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
import static io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE;

import io.vertx.codegen.annotations.Unstable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.interceptors.impl.BodyInterceptorImpl;
import io.vertx.httpproxy.interceptors.impl.PathInterceptorImpl;

import java.util.Objects;
import java.util.function.Function;

/**
 * Used to create interceptors to modify request and response bodies.
 */
@VertxGen
@Unstable
public interface BodyInterceptor {

  /**
   * Apply callbacks to change the request and response bodies when the proxy receives them.
   *
   * @param modifyRequestBody the operation to apply to the request body
   * @param modifyResponseBody the operation to apply to the response body
   * @param inputRequestType the expected class of the pre-transform request
   * @param inputResponseType the expected class of the pre-transform response
   * @return the created interceptor
   * @param <T> pre-transform request type
   * @param <U> pre-transform response type
   */
  static <T, U> ProxyInterceptor modifyBody(
    Function<T, Object> modifyRequestBody, Function<U, Object> modifyResponseBody,
    Class<T> inputRequestType, Class<U> inputResponseType) {
    Objects.requireNonNull(modifyRequestBody);
    Objects.requireNonNull(inputRequestType);
    Objects.requireNonNull(modifyResponseBody);
    Objects.requireNonNull(inputResponseType);
    return new BodyInterceptorImpl<T, U>(modifyRequestBody, modifyResponseBody, inputRequestType, inputResponseType);
  }

  /**
   * Apply callbacks to change the request body when the proxy receives them.
   *
   * @param modifyRequestBody the operation to apply to the request body
   * @param inputRequestType the expected class of the pre-transform request
   * @return the created interceptor
   * @param <T> pre-transform request type
   */
  static <T> ProxyInterceptor modifyRequestBody(
    Function<T, Object> modifyRequestBody, Class<T> inputRequestType) {
    Objects.requireNonNull(modifyRequestBody);
    Objects.requireNonNull(inputRequestType);
    return new BodyInterceptorImpl<T, Object>(modifyRequestBody, null, inputRequestType, Object.class);
  }

  /**
   * Apply callbacks to change the  response body when the proxy receives them.
   *
   * @param modifyResponseBody the operation to apply to the response body
   * @param inputResponseType the expected class of the pre-transform response
   * @return the created interceptor
   * @param <U> pre-transform response type
   */
  static <U> ProxyInterceptor modifyResponseBody(
    Function<U, Object> modifyResponseBody, Class<U> inputResponseType) {
    Objects.requireNonNull(modifyResponseBody);
    Objects.requireNonNull(inputResponseType);
    return new BodyInterceptorImpl<Object, U>(null, modifyResponseBody, Object.class, inputResponseType);
  }

}
