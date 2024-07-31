package io.vertx.tests.interceptors;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.httpproxy.*;
import io.vertx.httpproxy.interceptors.PathInterceptor;
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

  private void testWithInterceptor(TestContext ctx, ProxyInterceptor interceptor, boolean httpHit, boolean wsHit) {
    Async latch = ctx.async(4);
    SocketAddress backend = backend(ctx, latch);

    startProxy(proxy -> {
      proxy.origin(backend);
      if (interceptor != null) proxy.addInterceptor(interceptor);
    });

    vertx.createHttpClient().request(HttpMethod.GET, 8080, "localhost", "/http")
      .compose(HttpClientRequest::send)
      .onComplete(ctx.asyncAssertSuccess(resp -> {
        resp.body().onSuccess(body -> {
          ctx.assertEquals(body.toString().endsWith("/updated"), httpHit);
          latch.countDown();

          vertx.createWebSocketClient().connect(8080, "localhost", "/ws")
            .onComplete(ctx.asyncAssertSuccess(webSocket -> {
              webSocket.handler(buffer -> {
                ctx.assertEquals(buffer.toString().endsWith("/updated"), wsHit);
                latch.countDown();
                webSocket.close();
              });
              webSocket.write(Buffer.buffer("hello"));
            }));
        });
      }));
    latch.await(5000);
  }

  @Test
  public void testNotInterceptor(TestContext ctx) {
    testWithInterceptor(ctx, null, false, false);
  }

  @Test
  public void testNotApplySocket(TestContext ctx) {
    ProxyInterceptor interceptor = PathInterceptor.changePath(x -> x + "/updated");
    testWithInterceptor(ctx, interceptor, true, false);
  }

  @Test
  public void testWithSocketInterceptor(TestContext ctx) {
    ProxyInterceptor interceptor = WebSocketInterceptor.allow(PathInterceptor.changePath(x -> x + "/updated"));
    testWithInterceptor(ctx, interceptor, true, true);
  }

  @Test
  public void testOnlyHitSocket(TestContext ctx) {
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
