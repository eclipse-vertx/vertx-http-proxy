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
import io.vertx.httpproxy.*;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@RunWith(VertxUnitRunner.class)
public class WebSocketTest extends ProxyTestBase {

  private WebSocketClient wsClient;
  private HttpClient httpClient;

  @Override
  public void tearDown(TestContext context) {
    super.tearDown(context);
    wsClient = null;
    httpClient = null;
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

  @Test
  public void testWebSocketFirefox(TestContext ctx) {
    Async async = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      Future<ServerWebSocket> fut = req.toWebSocket();
      fut.onComplete(ctx.asyncAssertSuccess(ws -> {
        ws.closeHandler(v -> {
          async.complete();
        });
      }));
    });
    startProxy(backend);
    httpClient = vertx.createHttpClient();
    RequestOptions options = new RequestOptions()
      .setPort(8080)
      .setHost("localhost")
      .setURI("/ws")
      .putHeader("Origin", "http://localhost:8080")
      .putHeader("Connection", "keep-alive, Upgrade")
      .putHeader("Upgrade", "Websocket")
      .putHeader("Sec-WebSocket-Version", "13")
      .putHeader("Sec-WebSocket-Key", "xy6UoM3l3TcREmAeAhZuYQ==");
    httpClient.request(options).onComplete(ctx.asyncAssertSuccess(clientRequest -> {
      clientRequest.connect().onComplete(ctx.asyncAssertSuccess(response -> {
        ctx.assertEquals(101, response.statusCode());
        response.netSocket().close();
      }));
    }));
  }

  @Test
  public void testVariableFromInterceptor(TestContext ctx) {
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
    ProxyInterceptor interceptor = new ProxyInterceptor() {
      @Override
      public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
        context.set("foo", "bar");
        return context.sendRequest();
      }
    };
    OriginRequestProvider provider = (proxyContext) -> {
      ctx.assertEquals("bar", proxyContext.get("foo", String.class));
      return proxyContext.client().request(new RequestOptions().setServer(backend));
    };
    startProxy(proxy -> proxy.origin(provider).addInterceptor(interceptor, true));
    wsClient = vertx.createWebSocketClient();
    WebSocketConnectOptions options = new WebSocketConnectOptions()
      .setPort(8080)
      .setHost("localhost")
      .setURI("/ws");
    wsClient.connect(options).onComplete(ctx.asyncAssertSuccess(ws -> {
      ws.write(Buffer.buffer("ping"));
      ws.handler(buff -> {
        ws.close();
      });
    }));
  }

  @Test
  public void testWebSocketExtensionsNegotiatedBetweenClientAndBackend(TestContext ctx) {
    Async async = ctx.async();
    HttpServerOptions backendOptions = new HttpServerOptions().setPort(8081).setHost("localhost")
      .setPerFrameWebSocketCompressionSupported(false) // Disable extension in the backend
      .setPerMessageWebSocketCompressionSupported(false); // Disable extension in the backend
    SocketAddress backend = startHttpBackend(ctx, backendOptions, req -> {
      ctx.assertTrue(req.headers().contains("sec-websocket-extensions"));
      Future<ServerWebSocket> fut = req.toWebSocket();
      fut.onComplete(ctx.asyncAssertSuccess(ws -> {
        ws.handler(buff -> ws.writeTextMessage(buff.toString()));
        ws.closeHandler(v -> {
          async.complete();
        });
      }));
    });
    startProxy(proxyServerOptions -> {
      return proxyServerOptions
        .setPerFrameWebSocketCompressionSupported(true) // Enable extension in the proxy
        .setPerMessageWebSocketCompressionSupported(true); // Enable extension in the proxy
    }, httpProxy -> httpProxy.origin(backend));
    wsClient = vertx.createWebSocketClient(new WebSocketClientOptions()
      .setTryUsePerFrameCompression(true)  // Enable extension in the client
      .setTryUsePerMessageCompression(true)); // Enable extension in the client
    WebSocketConnectOptions options = new WebSocketConnectOptions()
      .setPort(8080)
      .setHost("localhost")
      .setURI("/ws");
    wsClient.connect(options).onComplete(ctx.asyncAssertSuccess(ws -> {
      ctx.assertFalse(ws.headers().contains("sec-websocket-extensions"), "Expected extensions to be declined");
      ws.textMessageHandler(msg -> {
        ctx.assertEquals("hello", msg);
        ws.close();
      });
      ws.writeTextMessage("hello");
    }));
  }
}
