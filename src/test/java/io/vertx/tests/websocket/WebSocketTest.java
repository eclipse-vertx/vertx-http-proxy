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
package io.vertx.tests.websocket;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.SocketAddress;
import io.vertx.httpproxy.OriginRequestProvider;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyResponse;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;
import io.vertx.tests.TestBase;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class WebSocketTest extends TestBase {

  private WebSocketClient wsClient;
  private HttpClient httpClient;
  private NetClient netClient;

  @Test
  public void testWebSocketV00(VertxTestContext ctx) {
    testWebSocket(ctx, WebSocketVersion.V00);
  }

  @Test
  public void testWebSocketV07(VertxTestContext ctx) {
    testWebSocket(ctx, WebSocketVersion.V07);
  }

  @Test
  public void testWebSocketV08(VertxTestContext ctx) {
    testWebSocket(ctx, WebSocketVersion.V08);
  }

  @Test
  public void testWebSocketV13(VertxTestContext ctx) {
    testWebSocket(ctx, WebSocketVersion.V13);
  }

  private void testWebSocket(VertxTestContext ctx, WebSocketVersion version) {
    Checkpoint async = ctx.checkpoint();
    SocketAddress backend = startHttpBackend(8081, req -> {
      Future<ServerWebSocket> fut = req.toWebSocket();
      fut.onComplete(ctx.succeeding(ws -> {
        ws.handler(buff -> ws.write(buff));
        ws.closeHandler(v -> {
          async.flag();
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
    wsClient.connect(options).onComplete(ctx.succeeding(ws -> {
      ws.write(Buffer.buffer("ping"));
      ws.handler(buff -> {
        ws.close();
      });
    }));
  }

  @Test
  public void testWebSocketReject(VertxTestContext ctx) {
    Checkpoint async = ctx.checkpoint();
    SocketAddress backend = startHttpBackend(8081, req -> {
      req.response().setStatusCode(400).end();
    });
    startProxy(backend);
    wsClient = vertx.createWebSocketClient();
    WebSocketConnectOptions options = new WebSocketConnectOptions()
      .setPort(8080)
      .setHost("localhost")
      .setURI("/ws");
    wsClient.connect(options).onComplete(ctx.failing(err -> {
      assertTrue(err.getClass() == UpgradeRejectedException.class);
      async.flag();
    }));
  }

  @Test
  public void testInboundClose(VertxTestContext ctx) {
    Checkpoint async = ctx.checkpoint();
    SocketAddress backend = startNetBackend(8081, so -> {
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
    wsClient.connect(options).onComplete(ctx.failing(err -> {
      assertTrue(err.getClass() == UpgradeRejectedException.class);
      async.flag();
    }));
  }

  @Test
  public void testWebSocketFirefox(VertxTestContext ctx) {
    Checkpoint async = ctx.checkpoint();
    SocketAddress backend = startHttpBackend(8081, req -> {
      Future<ServerWebSocket> fut = req.toWebSocket();
      fut.onComplete(ctx.succeeding(ws -> {
        ws.closeHandler(v -> {
          async.flag();
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
    httpClient.request(options).onComplete(ctx.succeeding(clientRequest -> {
      clientRequest.connect().onComplete(ctx.succeeding(response -> {
        assertEquals(101, response.statusCode());
        response.netSocket().close();
      }));
    }));
  }

  @Test
  public void testWebSocketHostHeader(VertxTestContext ctx) {
    Checkpoint async = ctx.checkpoint();
    SocketAddress backend = startHttpBackend(8081, req -> {
      assertEquals("localhost:8081", req.headers().get("Host"));
      Future<ServerWebSocket> fut = req.toWebSocket();
      fut.onComplete(ctx.succeeding(ws -> {
        ws.closeHandler(v -> {
          async.flag();
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
      .putHeader("Connection", "Upgrade")
      .putHeader("Upgrade", "Websocket")
      .putHeader("Sec-WebSocket-Version", "13")
      .putHeader("Sec-WebSocket-Key", "xy6UoM3l3TcREmAeAhZuYQ==");
    httpClient.request(options).onComplete(ctx.succeeding(clientRequest -> {
      clientRequest.connect().onComplete(ctx.succeeding(response -> {
        assertEquals(101, response.statusCode());
        response.netSocket().close();
      }));
    }));
  }

  @Test
  public void testVariableFromInterceptor(VertxTestContext ctx) {
    Checkpoint async = ctx.checkpoint();
    SocketAddress backend = startHttpBackend(8081, req -> {
      Future<ServerWebSocket> fut = req.toWebSocket();
      fut.onComplete(ctx.succeeding(ws -> {
        ws.handler(buff -> ws.write(buff));
        ws.closeHandler(v -> {
          async.flag();
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
      assertEquals("bar", proxyContext.get("foo", String.class));
      return proxyContext.client().request(new RequestOptions().setServer(backend));
    };
    startProxy(proxy -> proxy.origin(provider).addInterceptor(interceptor, true));
    wsClient = vertx.createWebSocketClient();
    WebSocketConnectOptions options = new WebSocketConnectOptions()
      .setPort(8080)
      .setHost("localhost")
      .setURI("/ws");
    wsClient.connect(options).onComplete(ctx.succeeding(ws -> {
      ws.write(Buffer.buffer("ping"));
      ws.handler(buff -> {
        ws.close();
      });
    }));
  }

  @Test
  public void testWebSocketExtensionsNegotiatedBetweenClientAndBackend(VertxTestContext ctx) {
    Checkpoint async = ctx.checkpoint();
    HttpServerOptions backendOptions = new HttpServerOptions().setPort(8081).setHost("localhost")
      .setPerFrameWebSocketCompressionSupported(false) // Disable extension in the backend
      .setPerMessageWebSocketCompressionSupported(false); // Disable extension in the backend
    SocketAddress backend = startHttpBackend(backendOptions, req -> {
      assertTrue(req.headers().contains("sec-websocket-extensions"));
      Future<ServerWebSocket> fut = req.toWebSocket();
      fut.onComplete(ctx.succeeding(ws -> {
        ws.handler(buff -> ws.writeTextMessage(buff.toString()));
        ws.closeHandler(v -> {
          async.flag();
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
    wsClient.connect(options).onComplete(ctx.succeeding(ws -> {
      assertFalse(ws.headers().contains("sec-websocket-extensions"), "Expected extensions to be declined");
      ws.textMessageHandler(msg -> {
        assertEquals("hello", msg);
        ws.close();
      });
      ws.writeTextMessage("hello");
    }));
  }

  private static final Buffer UPGRADE_RESPONSE = Buffer.buffer("HTTP/1.1 101 Switching Protocols\r\n" +
    "Upgrade: websocket\r\n" +
    "Connection: Upgrade\r\n" +
    "Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=\r\n" +
    "\r\n");

  private static final Buffer UPGRADE = Buffer.buffer("GET /ws HTTP/1.1\r\n" +
    "Host: localhost:8080\r\n" +
    "Connection: Upgrade\r\n" +
    "Upgrade: websocket\r\n" +
    "Sec-WebSocket-Version: 13\r\n" +
    "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
    "\r\n");

  @Test
  public void testClientCloseDuringUpgradeClosesBackendConnection(VertxTestContext ctx) {
    Checkpoint backendClosed = ctx.checkpoint();
    Promise<Void> upgradeReceived = Promise.promise();
    SocketAddress backend = startNetBackend(8081, so -> {
      // Never answers the upgrade
      so.handler(buff -> upgradeReceived.tryComplete());
      so.closeHandler(v -> backendClosed.flag());
    });
    startProxy(backend);
    netClient = vertx.createNetClient();
    netClient.connect(8080, "localhost").onComplete(ctx.succeeding(so -> {
      upgradeReceived.future().onComplete(ctx.succeeding(v -> so.close()));
      so.write(UPGRADE);
    }));
  }

  @Test
  public void testClientCloseAfterUpgradeClosesBackendConnection(VertxTestContext ctx) {
    Checkpoint backendClosed = ctx.checkpoint();
    SocketAddress backend = startNetBackend(8081, so -> {
      so.handler(buff -> {
        so.write(UPGRADE_RESPONSE);
      });
      so.closeHandler(v -> backendClosed.flag());
    });
    startProxy(backend);
    netClient = vertx.createNetClient();
    netClient.connect(8080, "localhost").onComplete(ctx.succeeding(so -> {
      so.handler(buff -> {
        // Upgrade response received by the client side, now close abruptly
        so.close();
      });
      so.write(UPGRADE);
    }));
  }

  @Test
  public void testBackendCloseAfterUpgradeClosesClientConnection(VertxTestContext ctx) {
    Checkpoint clientClosed = ctx.checkpoint();
    SocketAddress backend = startNetBackend(8081, so -> {
      so.handler(buff -> {
        so.write(UPGRADE_RESPONSE);
        // Close the backend side after upgrade completes
        vertx.setTimer(100, id -> so.close());
      });
    });
    startProxy(backend);
    netClient = vertx.createNetClient();
    netClient.connect(8080, "localhost").onComplete(ctx.succeeding(so -> {
      so.closeHandler(v -> clientClosed.flag());
      so.write(UPGRADE);
    }));
  }

  // The backend connection is only closed when the proxy's pooled connection expires, which takes the default
  // 60 s keep-alive timeout: more than the 30 s default of VertxExtension.
  // See https://github.com/eclipse-vertx/vertx-http-proxy/issues/166
  @Test
  @Timeout(value = 90, timeUnit = TimeUnit.SECONDS)
  public void testClientCloseBeforeOriginResolvedSendsNoUpgrade(VertxTestContext ctx) {
    Checkpoint backendClosed = ctx.checkpoint();
    Promise<Void> resolving = Promise.promise();
    SocketAddress backend = startNetBackend(8081, so -> {
      so.handler(buff -> fail("Upgrade sent for a client that has gone"));
      so.closeHandler(v -> backendClosed.flag());
    });
    startProxy(proxy -> proxy.origin(OriginRequestProvider.selector(proxyContext -> {
      Promise<SocketAddress> origin = Promise.promise();
      // Resolved only once the client has gone
      proxyContext.request().proxiedRequest().connection().closeHandler(v -> origin.complete(backend));
      resolving.complete();
      return origin.future();
    })));
    netClient = vertx.createNetClient();
    netClient.connect(8080, "localhost").onComplete(ctx.succeeding(so -> {
      resolving.future().onComplete(ctx.succeeding(v -> so.close()));
      so.write(UPGRADE);
    }));
  }
}
