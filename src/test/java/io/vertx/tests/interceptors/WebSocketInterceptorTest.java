package io.vertx.tests.interceptors;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.httpproxy.*;
import io.vertx.httpproxy.interceptors.HeadInterceptor;
import io.vertx.httpproxy.interceptors.WebSocketInterceptor;
import io.vertx.tests.ProxyTestBase;
import org.junit.Test;



public class WebSocketInterceptorTest extends ProxyTestBase {

  public WebSocketInterceptorTest(ProxyOptions options) {
    super(options);
  }

  private SocketAddress backend(TestContext ctx, Async async) {
    return startHttpBackend(ctx, 8081, req -> {
      if (req.uri().startsWith("/ws")) {
        Future<ServerWebSocket> fut = req.toWebSocket();
        fut.onComplete(ctx.asyncAssertSuccess(ws -> {
          ws.handler(buff -> {
            ws.write(Buffer.buffer(req.uri()));
          });
          ws.closeHandler(v -> {
            async.countDown();
          });
        }));
      } else {
        req.response().end(req.uri()).onComplete(v -> async.countDown());
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
  private void testWithInterceptor(TestContext ctx, ProxyInterceptor interceptor, boolean httpHit, boolean wsHit) {
    Async latch = ctx.async(3);
    SocketAddress backend = backend(ctx, latch);

    startProxy(proxy -> {
      proxy.origin(backend);
      if (interceptor != null) {
        proxy.addInterceptor(interceptor);
      };
    });

    HttpClientAgent httpClient = vertx.createHttpClient();
    WebSocketClient webSocketClient = vertx.createWebSocketClient();

    Buffer body = httpClient
      .request(HttpMethod.GET, 8080, "localhost", "/http")
      .compose(req -> req.send()
        .expecting(HttpResponseExpectation.SC_OK))
      .compose(HttpClientResponse::body).await();
    ctx.assertEquals(body.toString().endsWith("/updated"), httpHit);
    webSocketClient.connect(8080, "localhost", "/ws")
      .onComplete(ctx.asyncAssertSuccess(webSocket -> {
        webSocket.handler(buffer -> {
          ctx.assertEquals(buffer.toString().endsWith("/updated"), wsHit);
          latch.countDown();
          webSocket.close();
        });
        webSocket.write(Buffer.buffer("hello"));
      }));
    latch.await(5000);
  }

  @Test
  public void testNotInterceptor(TestContext ctx) {
    testWithInterceptor(ctx, null, false, false);
  }

  @Test
  public void testNotApplySocket(TestContext ctx) {
    // this interceptor only applies to regular HTTP traffic
    ProxyInterceptor interceptor = HeadInterceptor.builder().updatingPath(x -> x + "/updated").build();
    testWithInterceptor(ctx, interceptor, true, false);
  }

  @Test
  public void testWithSocketInterceptor(TestContext ctx) {
    // this interceptor applies to both regular HTTP traffic and WebSocket handshake
    ProxyInterceptor interceptor = WebSocketInterceptor.allow(HeadInterceptor.builder().updatingPath(x -> x + "/updated").build());
    testWithInterceptor(ctx, interceptor, true, true);
  }

  @Test
  public void testOnlyHitSocket(TestContext ctx) {
    // this interceptor only applies to WebSocket handshake
    ProxyInterceptor interceptor = new ProxyInterceptor() {
      @Override
      public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
        if (context.isWebSocket()) {
          context.request().setURI(context.request().getURI() + "/updated");
        }
        return context.sendRequest();
      }

      @Override
      public boolean allowApplyToWebSocket() {
        return true;
      }
    };
    testWithInterceptor(ctx, interceptor, false, true);
  }


}
