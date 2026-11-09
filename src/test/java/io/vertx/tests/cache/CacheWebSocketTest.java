package io.vertx.tests.cache;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.net.SocketAddress;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CacheWebSocketTest extends CacheTestBase {

  private WebSocketClient client;

  @Test
  public void testWsWithCache(VertxTestContext ctx) {
    Checkpoint latch = ctx.checkpoint();
    SocketAddress backend = startHttpBackend(8081, req -> {
      req.toWebSocket().onSuccess(ws -> {
        ws.handler(ws::write);
      });
    });
    startProxy(backend);
    client = vertx.createWebSocketClient();
    client.connect(8080, "localhost", "/").onComplete(ctx.succeeding(ws1 -> {
      ws1.handler(buffer -> {
        assertEquals(buffer.toString(), "v1");
        ws1.close().onComplete(ctx.succeeding(closed -> {
          client.connect(8080, "localhost", "/").onComplete(ctx.succeeding(ws2 -> {
            ws2.handler(buffer2 -> {
              assertEquals(buffer2.toString(), "v2");
              ws2.close().onComplete(ctx.succeeding(v2 -> {
                latch.flag();
              }));
            });
            ws2.write(Buffer.buffer("v2")); // second WebSocket, send and reply "v2"
          }));
        }));
      });
      ws1.write(Buffer.buffer("v1")); // first WebSocket, send and reply "v1"
    }));
    latch.await(Duration.ofMillis(20000));
  }
}
