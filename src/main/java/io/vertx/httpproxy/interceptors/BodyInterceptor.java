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
   * @return the created interceptor
   */
  @GenIgnore(PERMITTED_TYPE)
  static ProxyInterceptor modifyBody(Function<Buffer, Buffer> modifyRequestBody, Function<Buffer, Buffer> modifyResponseBody) {
    return BodyInterceptorImpl.forBuffer(modifyRequestBody, modifyResponseBody);
  }

  /**
   * Apply callbacks to change the request body when the proxy receives them.
   *
   * @param modifyRequestBody the operation to apply to the request body
   * @return the created interceptor
   */
  @GenIgnore(PERMITTED_TYPE)
  static ProxyInterceptor modifyRequestBody(Function<Buffer, Buffer> modifyRequestBody) {
    return BodyInterceptorImpl.forBuffer(modifyRequestBody, null);
  }

  /**
   * Apply callbacks to change the response body when the proxy receives them.
   *
   * @param modifyResponseBody the operation to apply to the response body
   * @return the created interceptor
   */
  @GenIgnore(PERMITTED_TYPE)
  static ProxyInterceptor modifyResponseBody(Function<Buffer, Buffer> modifyResponseBody) {
    return BodyInterceptorImpl.forBuffer(null, modifyResponseBody);
  }

  /**
   * Apply callbacks to change the request and response bodies when the proxy receives them. For JsonObject only.
   *
   * @param modifyRequestBody the operation to apply to the request body
   * @param modifyResponseBody the operation to apply to the response body
   * @return the created interceptor
   */
  @GenIgnore(PERMITTED_TYPE)
  static ProxyInterceptor modifyBodyAsJson(Function<JsonObject, JsonObject> modifyRequestBody, Function<JsonObject, JsonObject> modifyResponseBody) {
    return BodyInterceptorImpl.forJsonObject(modifyRequestBody, modifyResponseBody);
  }

  /**
   * Apply callbacks to change the request body when the proxy receives them. For JsonObject only.
   *
   * @param modifyRequestBody the operation to apply to the request body
   * @return the created interceptor
   */
  @GenIgnore(PERMITTED_TYPE)
  static ProxyInterceptor modifyRequestBodyAsJson(Function<JsonObject, JsonObject> modifyRequestBody) {
    return BodyInterceptorImpl.forJsonObject(modifyRequestBody, null);
  }

  /**
   * Apply callbacks to change the response body when the proxy receives them. For JsonObject only.
   *
   * @param modifyResponseBody the operation to apply to the response body
   * @return the created interceptor
   */
  @GenIgnore(PERMITTED_TYPE)
  static ProxyInterceptor modifyResponseBodyAsJson(Function<JsonObject, JsonObject> modifyResponseBody) {
    return BodyInterceptorImpl.forJsonObject(null, modifyResponseBody);
  }

}
