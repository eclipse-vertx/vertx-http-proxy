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
package io.vertx.tests;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.httpproxy.ProxyOptions;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@RunWith(VertxUnitRunner.class)
public class WebSocketTest extends ProxyTestBase {

  private WebSocketClient wsClient;

  @Override
  public void tearDown(TestContext context) {
    super.tearDown(context);
    wsClient = null;
  }

  public WebSocketTest() {
    super(new ProxyOptions());
  }

  @Test
  public void testWebSocketV00(TestContext ctx) {
    testWebSocket(ctx, WebSocketVersion.V00);
  }

  @Test
  public void testWebSocketV07(TestContext ctx) {
    testWebSocket(ctx, WebSocketVersion.V07);
  }

  @Test
  public void testWebSocketV08(TestContext ctx) {
    testWebSocket(ctx, WebSocketVersion.V08);
  }

  @Test
  public void testWebSocketV13(TestContext ctx) {
    testWebSocket(ctx, WebSocketVersion.V13);
  }

  private void testWebSocket(TestContext ctx, WebSocketVersion version) {
    Async async = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      Future<ServerWebSocket> fut = req.toWebSocket();
      fut.onComplete(ctx.asyncAssertSuccess(ws -> {
        ws.handler(buff -> ws.write(buff));
        ws.closeHandler(v -> {
          async.complete();
        });
      }));
    });
    startProxy(backend);
    wsClient = vertx.createWebSocketClient();
    WebSocketConnectOptions options = new WebSocketConnectOptions()
      .setPort(8080)
      .setHost("localhost")
      .setURI("/ws")
      .setVersion(version);
    wsClient.connect(options).onComplete(ctx.asyncAssertSuccess(ws -> {
      ws.write(Buffer.buffer("ping"));
      ws.handler(buff -> {
        ws.close();
      });
    }));
  }

  @Test
  public void testWebSocketReject(TestContext ctx) {
    Async async = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.response().setStatusCode(400).end();
    });
    startProxy(backend);
    wsClient = vertx.createWebSocketClient();
    WebSocketConnectOptions options = new WebSocketConnectOptions()
      .setPort(8080)
      .setHost("localhost")
      .setURI("/ws");
    wsClient.connect(options).onComplete(ctx.asyncAssertFailure(err -> {
      ctx.assertTrue(err.getClass() == UpgradeRejectedException.class);
      async.complete();
    }));
  }

  @Test
  public void testInboundClose(TestContext ctx) {
    Async async = ctx.async();
    SocketAddress backend = startNetBackend(ctx, 8081, so -> {
      so.handler(buff -> {
        so.close();
      });
    });
    startProxy(backend);
    wsClient = vertx.createWebSocketClient();
    WebSocketConnectOptions options = new WebSocketConnectOptions()
      .setPort(8080)
      .setHost("localhost")
      .setURI("/ws");
    wsClient.connect(options).onComplete(ctx.asyncAssertFailure(err -> {
      ctx.assertTrue(err.getClass() == UpgradeRejectedException.class);
      async.complete();
    }));
  }
}
