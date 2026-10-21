/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.proxy;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.tests.TestBase;
import org.junit.Test;

import java.util.Random;

import static io.vertx.core.http.HttpMethod.GET;

public class ProxyPassThroughTest extends TestBase {

  private HttpClient client;

  @Override
  public void tearDown(TestContext context) {
    super.tearDown(context);
    client = null;
  }

  /*
   * Cannot be restored: startProxy(ctx) and client.getNow(...) no longer exist in the current API.
  @Test
  public void testNotfound(TestContext ctx) {
    startProxy(ctx);
    client = vertx.createHttpClient();
    Async async = ctx.async();
    client.getNow(8080, "localhost", "/", resp -> {
      ctx.assertEquals(404, resp.statusCode());
      async.complete();
    });
  }
  */

  @Test
  public void testGet(TestContext ctx) {
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      ctx.assertEquals("/somepath", req.uri());
      ctx.assertEquals("localhost:8081", req.authority().toString());
      req.response().end("Hello World");
    });
    startProxy(backend);
    client = vertx.createHttpClient();
    client.request(GET, 8080, "localhost", "/somepath")
        .compose(req -> req
            .send()
            .compose(resp -> {
              ctx.assertEquals(200, resp.statusCode());
              return resp.body();
            }))
        .onComplete(ctx.asyncAssertSuccess(body -> {
          ctx.assertEquals("Hello World", body.toString());
        }));
  }

  @Test
  public void testPost(TestContext ctx) {
    byte[] body = new byte[1024];
    Random random = new Random();
    random.nextBytes(body);
    Async async = ctx.async(2);
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.bodyHandler(buff -> {
        req.response().end();
        ctx.assertEquals(Buffer.buffer(body), buff);
        async.countDown();
      });
    });
    startProxy(backend);
    client = vertx.createHttpClient();
    client.request(GET, 8080, "localhost", "/")
        .compose(req -> req
            .send(Buffer.buffer(body))
            .compose(resp -> {
              ctx.assertEquals(200, resp.statusCode());
              return resp.body();
            }))
        .onComplete(ctx.asyncAssertSuccess(buff -> async.countDown()));
  }

  @Test
  public void testResponse(TestContext ctx) {
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      ctx.assertEquals(HttpMethod.POST, req.method());
      req.response()
        .addCookie(Cookie.cookie("some-cookie-name", "some-cookie-value"))
        .putHeader("some-header", "some-header-value")
        .setStatusCode(203)
        .setStatusMessage("some-status-message")
        .end("some-data");
    });
    startProxy(backend);
    client = vertx.createHttpClient();
    Async async = ctx.async();
    client.request(HttpMethod.POST, 8080, "localhost", "/path")
      .compose(req -> req.send().compose(resp -> {
        ctx.assertEquals(1, resp.cookies().size());
        ctx.assertEquals("some-cookie-name=some-cookie-value", resp.cookies().get(0));
        ctx.assertEquals("some-header-value", resp.getHeader("some-header"));
        ctx.assertEquals(203, resp.statusCode());
        ctx.assertEquals("some-status-message", resp.statusMessage());
        return resp.body();
      }))
      .onComplete(ctx.asyncAssertSuccess(body -> {
        ctx.assertEquals("some-data", body.toString());
        async.complete();
      }));
  }
}
