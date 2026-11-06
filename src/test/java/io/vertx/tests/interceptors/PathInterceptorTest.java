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
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.tests.TestBase;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:wangzengyi1935@163.com">Zengyi Wang</a>
 */
public class PathInterceptorTest extends TestBase {

  @Test
  public void addPrefixTest(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(8081, req -> {
      ctx.verify(v -> {
        assertEquals(req.uri(), "/prefix/hello");
      });
      req.response().end("Hello");
    });

    startProxy(proxy -> proxy.origin(backend)
      .addInterceptor(ProxyInterceptor.builder().addingPathPrefix("/prefix").build()));

    vertx.createHttpClient().request(HttpMethod.GET, 8080, "localhost", "/hello")
      .compose(HttpClientRequest::send)
      .onComplete(ctx.asyncAssertSuccess(resp -> latch.complete()));
  }

  @Test
  public void removePrefixTest(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(8081, req -> {
      ctx.verify(v -> {
        assertEquals(req.uri(), "/hello");
      });
      req.response().end("Hello");
    });

    startProxy(proxy -> proxy.origin(backend)
      .addInterceptor(ProxyInterceptor.builder().removingPathPrefix("/prefix").build()));

    vertx.createHttpClient().request(HttpMethod.GET, 8080, "localhost", "/prefix/hello")
      .compose(HttpClientRequest::send)
      .onComplete(ctx.asyncAssertSuccess(resp -> latch.complete()));
  }

  @Test
  public void absentPrefixTest(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(8081, req -> {
      ctx.verify(v -> {
        assertEquals(req.uri(), "/hello");
      });
      req.response().end("Hello");
    });

    startProxy(proxy -> proxy.origin(backend)
      .addInterceptor(ProxyInterceptor.builder().removingPathPrefix("/prefix").build()));

    vertx.createHttpClient().request(HttpMethod.GET, 8080, "localhost", "/hello")
      .compose(HttpClientRequest::send)
      .onComplete(ctx.asyncAssertSuccess(resp -> latch.complete()));
  }
}
