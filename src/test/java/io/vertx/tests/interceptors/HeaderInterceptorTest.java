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
import io.vertx.httpproxy.ProxyOptions;
import io.vertx.httpproxy.interceptors.HeadInterceptor;
import io.vertx.tests.ProxyTestBase;
import org.junit.Test;

import java.util.Set;

/**
 * @author <a href="mailto:wangzengyi1935@163.com">Zengyi Wang</a>
 */
public class HeaderInterceptorTest extends ProxyTestBase {

  public HeaderInterceptorTest(ProxyOptions options) {
    super(options);
  }

  @Test
  public void testFilterRequestHeader(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      ctx.assertEquals(req.headers().get("k1"), "v1");
      ctx.assertEquals(req.headers().get("k2"), null);
      req.response().end("Hello");
    });

    startProxy(proxy -> proxy.origin(backend)
      .addInterceptor(HeadInterceptor.builder().filteringRequestHeaders(Set.of("k2")).build()));

    vertx.createHttpClient().request(HttpMethod.GET, 8080, "localhost", "/")
      .compose(request -> request
        .putHeader("k1", "v1")
        .putHeader("k2", "v2")
        .send())
      .onComplete(ctx.asyncAssertSuccess(response -> {
        latch.complete();
      }));
  }

  @Test
  public void testFilterResponseHeader(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.response()
        .putHeader("k1", "v1")
        .putHeader("k2", "v2")
        .end("Hello");
    });

    startProxy(proxy -> proxy.origin(backend)
      .addInterceptor(HeadInterceptor.builder().filteringResponseHeaders(Set.of("k2")).build()));

    vertx.createHttpClient().request(HttpMethod.GET, 8080, "localhost", "/")
      .compose(HttpClientRequest::send)
      .onComplete(ctx.asyncAssertSuccess(resp -> {
        ctx.assertEquals(resp.headers().get("k1"), "v1");
        ctx.assertEquals(resp.headers().get("k2"), null);
        latch.complete();
      }));
  }


}
