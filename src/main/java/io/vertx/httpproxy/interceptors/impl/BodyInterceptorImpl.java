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
import io.vertx.core.json.JsonObject;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyResponse;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.impl.BufferingWriteStream;
import java.util.function.Function;


public class BodyInterceptorImpl implements ProxyInterceptor {
  Function<Buffer, Buffer> modifyRequestBody;
  Function<Buffer, Buffer> modifyResponseBody;

  private BodyInterceptorImpl() {}

  public static BodyInterceptorImpl forBuffer(Function<Buffer, Buffer> modifyRequestBody, Function<Buffer, Buffer> modifyResponseBody) {
    BodyInterceptorImpl impl = new BodyInterceptorImpl();
    impl.modifyRequestBody = modifyRequestBody;
    impl.modifyResponseBody = modifyResponseBody;
    return impl;
  }

  public static BodyInterceptorImpl forJsonObject(Function<JsonObject, JsonObject> modifyRequestBody, Function<JsonObject, JsonObject> modifyResponseBody) {
    BodyInterceptorImpl impl = new BodyInterceptorImpl();
    impl.modifyRequestBody = buffer -> modifyRequestBody.apply(buffer.toJsonObject()).toBuffer();
    impl.modifyResponseBody = buffer -> modifyResponseBody.apply(buffer.toJsonObject()).toBuffer();
    return impl;
  }

  @Override
  public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
    if (modifyRequestBody == null) return context.sendRequest();

    Body body = context.request().getBody();
    BufferingWriteStream bws = new BufferingWriteStream();
    return body.stream().pipeTo(bws).compose(r -> {
      context.request().setBody(Body.body(modifyRequestBody.apply(bws.content())));
      return context.sendRequest();
    });
  }

  @Override
  public Future<Void> handleProxyResponse(ProxyContext context) {
    if (modifyResponseBody == null) return context.sendResponse();

    Body body = context.response().getBody();
    BufferingWriteStream bws = new BufferingWriteStream();
    return body.stream().pipeTo(bws).compose(r -> {
      context.response().setBody(Body.body(modifyResponseBody.apply(bws.content())));
      return context.sendResponse();
    });
  }
}
