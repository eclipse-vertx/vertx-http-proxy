package io.vertx.tests;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.httpproxy.ProxyOptions;
import io.vertx.httpproxy.cache.CacheOptions;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class WebSocketCacheTest extends ProxyTestBase {

  public WebSocketCacheTest() {
    super(new ProxyOptions().setCacheOptions(new CacheOptions()));
  }

  @Test
  public void testWsWithCache(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.toWebSocket().onSuccess(ws -> {
        ws.handler(ws::write);
      });
    });
    startProxy(backend);
    WebSocketClient client = vertx.createWebSocketClient();
    client.connect(8080, "localhost", "/").onComplete(ctx.asyncAssertSuccess(ws1 -> {
      ws1.handler(buffer -> {
        ctx.assertEquals(buffer.toString(), "v1");
        ws1.close().onComplete(ctx.asyncAssertSuccess(v -> {
          client.connect(8080, "localhost", "/").onComplete(ctx.asyncAssertSuccess(ws2 -> {
            ws2.handler(buffer2 -> {
              ctx.assertEquals(buffer2.toString(), "v2");
              ws2.close().onComplete(ctx.asyncAssertSuccess(v2 -> {
                latch.complete();
              }));
            });
            ws2.write(Buffer.buffer("v2")); // second WebSocket, send and reply "v2"
          }));
        }));
      });
      ws1.write(Buffer.buffer("v1")); // first WebSocket, send and reply "v1"
    }));
    latch.awaitSuccess(20_000);
  }
}
