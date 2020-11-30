/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.httpproxy;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;

import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface HttpProxy extends Handler<HttpServerRequest> {

  static HttpProxy reverseProxy2(HttpClient client) {
    return new io.vertx.httpproxy.impl.HttpProxyImpl(client);
  }

  @Fluent
  default HttpProxy target(SocketAddress address) {
    return selector(req -> Future.succeededFuture(address));
  }

  @Fluent
  default HttpProxy target(int port, String host) {
    return target(SocketAddress.inetSocketAddress(port, host));
  }

  @Fluent
  HttpProxy selector(Function<HttpServerRequest, Future<SocketAddress>> selector);

  void handle(HttpServerRequest request);

}
