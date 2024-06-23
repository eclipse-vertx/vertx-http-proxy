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
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyResponse;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.impl.BufferingWriteStream;
import java.util.function.Function;


public class BodyInterceptorImpl<T, U> implements ProxyInterceptor {
  private final Function<T, Object> modifyRequestBody;
  private final Function<U, Object> modifyResponseBody;
  private final Class<T> inputRequestType;
  private final Class<U> inputResponseType;

  public BodyInterceptorImpl(Function<T, Object> modifyRequestBody, Function<U, Object> modifyResponseBody,
                             Class<T> inputRequestType, Class<U> inputResponseType) {
    this.modifyRequestBody = modifyRequestBody;
    this.modifyResponseBody = modifyResponseBody;
    this.inputRequestType = inputRequestType;
    this.inputResponseType = inputResponseType;
  }

  @Override
  public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
    if (modifyRequestBody == null) return context.sendRequest();

    Body body = context.request().getBody();
    BufferingWriteStream bws = new BufferingWriteStream();
    return body.stream().pipeTo(bws).compose(r -> {
      context.request().setBody(
        Body.body(Json.encodeToBuffer(modifyRequestBody.apply(Json.decodeValue(bws.content(), inputRequestType)))));
      return context.sendRequest();
    });
  }

  @Override
  public Future<Void> handleProxyResponse(ProxyContext context) {
    if (modifyResponseBody == null) return context.sendResponse();

    Body body = context.response().getBody();
    BufferingWriteStream bws = new BufferingWriteStream();
    return body.stream().pipeTo(bws).compose(r -> {
      context.response().setBody(
        Body.body(Json.encodeToBuffer(modifyResponseBody.apply(Json.decodeValue(bws.content(), inputResponseType)))));
      return context.sendResponse();
    });
  }
}
