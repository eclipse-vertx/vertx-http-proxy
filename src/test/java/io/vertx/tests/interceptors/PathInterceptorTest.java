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

package io.vertx.tests.interceptors;

import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.SocketAddress;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;
import io.vertx.tests.TestBase;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author <a href="mailto:wangzengyi1935@163.com">Zengyi Wang</a>
 */
public class PathInterceptorTest extends TestBase {

  @Test
  public void addPrefixTest(VertxTestContext ctx) {
    Checkpoint latch = ctx.checkpoint();
    SocketAddress backend = startHttpBackend(8081, req -> {
      assertEquals(req.uri(), "/prefix/hello");
      req.response().end("Hello");
    });

    startProxy(proxy -> proxy.origin(backend)
      .addInterceptor(ProxyInterceptor.builder().addingPathPrefix("/prefix").build()));

    vertx.createHttpClient().request(HttpMethod.GET, 8080, "localhost", "/hello")
      .compose(HttpClientRequest::send)
      .onComplete(ctx.succeeding(resp -> latch.flag()));
  }

  @Test
  public void removePrefixTest(VertxTestContext ctx) {
    Checkpoint latch = ctx.checkpoint();
    SocketAddress backend = startHttpBackend(8081, req -> {
      assertEquals(req.uri(), "/hello");
      req.response().end("Hello");
    });

    startProxy(proxy -> proxy.origin(backend)
      .addInterceptor(ProxyInterceptor.builder().removingPathPrefix("/prefix").build()));

    vertx.createHttpClient().request(HttpMethod.GET, 8080, "localhost", "/prefix/hello")
      .compose(HttpClientRequest::send)
      .onComplete(ctx.succeeding(resp -> latch.flag()));
  }

  @Test
  public void absentPrefixTest(VertxTestContext ctx) {
    Checkpoint latch = ctx.checkpoint();
    SocketAddress backend = startHttpBackend(8081, req -> {
      assertEquals(req.uri(), "/hello");
      req.response().end("Hello");
    });

    startProxy(proxy -> proxy.origin(backend)
      .addInterceptor(ProxyInterceptor.builder().removingPathPrefix("/prefix").build()));

    vertx.createHttpClient().request(HttpMethod.GET, 8080, "localhost", "/hello")
      .compose(HttpClientRequest::send)
      .onComplete(ctx.succeeding(resp -> latch.flag()));
  }
}
