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
 * Handles the HTTP reverse proxy logic between the <i><b>edge</b></i> and the <i><b>origin</b></i>.
 * <p>
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface HttpProxy extends Handler<HttpServerRequest> {

  /**
   * Create a new {@code HttpProxy} instance.
   *
   * @param client the {@code HttpClient} that forwards <i><b>edge</b></i> request to the <i><b>origin</b></i>.
   * @return a reference to this, so the API can be used fluently.
   */
  static HttpProxy reverseProxy2(HttpClient client) {
    return new io.vertx.httpproxy.impl.HttpProxyImpl(client);
  }

  /**
   * Set the {@code SocketAddress} of the <i><b>origin</b></i>.
   *
   * @param address the {@code SocketAddress} of the <i><b>origin</b></i>
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  default HttpProxy target(SocketAddress address) {
    return selector(req -> Future.succeededFuture(address));
  }

  /**
   * Set the host name and port number of the <i><b>origin</b></i>.
   *
   * @param port the port number of the <i><b>origin</b></i> server
   * @param host the host name of the <i><b>origin</b></i> server
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  default HttpProxy target(int port, String host) {
    return target(SocketAddress.inetSocketAddress(port, host));
  }

  /**
   * Select the {@code HttpServerRequest} of the <i><b>edge</b></i> with future {@code SocketAddress} of the
   * <i><b>origin</b></i>.
   *
   * @param selector a function that selects {@code HttpServerRequest} of the <i><b>edge</b></i>
   *                 with future {@code SocketAddress} of the <i><b>origin</b></i>
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpProxy selector(Function<HttpServerRequest, Future<SocketAddress>> selector);

  /**
   * Handle the {@code HttpServerRequest} of the <i><b>edge</b></i>.
   *
   * @param request the front {@code HttpServerRequest}
   */
  void handle(HttpServerRequest request);

}
