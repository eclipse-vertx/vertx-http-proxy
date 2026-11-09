package io.vertx.tests.websocket;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.net.SocketAddress;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyResponse;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;
import io.vertx.tests.TestBase;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WebSocketInterceptorTest extends TestBase {

  private HttpClientAgent httpClient;
  private WebSocketClient webSocketClient;

  private SocketAddress backend(VertxTestContext ctx, Checkpoint async) {
    return startHttpBackend(8081, req -> {
      if (req.uri().startsWith("/ws")) {
        Future<ServerWebSocket> fut = req.toWebSocket();
        fut.onComplete(ctx.succeeding(ws -> {
          ws.handler(buff -> {
            ws.write(Buffer.buffer(req.uri()));
          });
          ws.closeHandler(v -> {
            async.flag();
          });
        }));
      } else {
        req.response().end(req.uri()).onComplete(v -> async.flag());
      }
    });
  }

  /**
   * The interceptor adds a suffix to the uri. If uri is changed by the interceptor, it calls a hit.
   *
   * @param ctx the test context
   * @param interceptor the added interceptor
   * @param httpHit if interceptor changes the regular HTTP packet
   * @param wsHit if interceptor changes the WebSocket packet
   */
  private void testWithInterceptor(VertxTestContext ctx, ProxyInterceptor interceptor, boolean httpHit, boolean wsHit) {
    Checkpoint latch = ctx.checkpoint(3);
    SocketAddress backend = backend(ctx, latch);

    startProxy(proxy -> {
      proxy.origin(backend);
      if (interceptor != null) {
        proxy.addInterceptor(interceptor, wsHit);
      };
    });

    httpClient = vertx.createHttpClient();
    webSocketClient = vertx.createWebSocketClient();

    Buffer body = httpClient
      .request(HttpMethod.GET, 8080, "localhost", "/http")
      .compose(req -> req.send()
        .expecting(HttpResponseExpectation.SC_OK))
      .compose(HttpClientResponse::body).await();
    assertEquals(body.toString().endsWith("/updated"), httpHit);
    webSocketClient.connect(8080, "localhost", "/ws")
      .onComplete(ctx.succeeding(webSocket -> {
        webSocket.handler(buffer -> {
          assertEquals(buffer.toString().endsWith("/updated"), wsHit);
          latch.flag();
          webSocket.close();
        });
        webSocket.write(Buffer.buffer("hello"));
      }));
    latch.await(Duration.ofSeconds(5));
  }

  @Test
  public void testNotInterceptor(VertxTestContext ctx) {
    testWithInterceptor(ctx, null, false, false);
  }

  @Test
  public void testNotApplySocket(VertxTestContext ctx) {
    // this interceptor only applies to regular HTTP traffic
    ProxyInterceptor interceptor = ProxyInterceptor.builder().transformingPath(x -> x + "/updated").build();
    testWithInterceptor(ctx, interceptor, true, false);
  }

  @Test
  public void testWithSocketInterceptor(VertxTestContext ctx) {
    // this interceptor applies to both regular HTTP traffic and WebSocket handshake
    ProxyInterceptor interceptor = ProxyInterceptor.builder().transformingPath(x -> x + "/updated").build();
    testWithInterceptor(ctx, interceptor, true, true);
  }

  @Test
  public void testOnlyHitSocket(VertxTestContext ctx) {
    // this interceptor only applies to WebSocket handshake
    ProxyInterceptor interceptor = new ProxyInterceptor() {
      @Override
      public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
        if (context.isWebSocket()) {
          context.request().setURI(context.request().getURI() + "/updated");
        }
        return context.sendRequest();
      }
    };
    testWithInterceptor(ctx, interceptor, false, true);
  }


}
