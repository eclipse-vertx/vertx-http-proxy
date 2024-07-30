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
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyResponse;
import io.vertx.httpproxy.impl.BufferingWriteStream;

import java.util.Objects;
import java.util.function.Function;


public class BodyInterceptorImpl implements ProxyInterceptor {
  private final Function<Buffer, Buffer> modifyRequestBody;
  private final Function<Buffer, Buffer> modifyResponseBody;
  private static final Function<Buffer, Buffer> NO_OP = buffer -> buffer;

  public BodyInterceptorImpl(Function<Buffer, Buffer> modifyRequestBody, Function<Buffer, Buffer> modifyResponseBody) {
    this.modifyRequestBody = Objects.requireNonNullElse(modifyRequestBody, NO_OP);
    this.modifyResponseBody = Objects.requireNonNullElse(modifyResponseBody, NO_OP);
  }

  @Override
  public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
    Body body = context.request().getBody();
    BufferingWriteStream bws = new BufferingWriteStream();
    return body.stream().pipeTo(bws).compose(r -> {
      context.request().setBody(
        Body.body(modifyRequestBody.apply(bws.content())));
      return context.sendRequest();
    });
  }

  @Override
  public Future<Void> handleProxyResponse(ProxyContext context) {
    Body body = context.response().getBody();
    BufferingWriteStream bws = new BufferingWriteStream();
    return body.stream().pipeTo(bws).compose(r -> {
      context.response().setBody(
        Body.body(modifyResponseBody.apply(bws.content())));
      return context.sendResponse();
    });
  }
}
