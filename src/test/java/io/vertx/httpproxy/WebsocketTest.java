package io.vertx.httpproxy;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.junit.Test;

public class WebsocketTest extends ProxyTestBase {

  private HttpClient client;

  @Override
  public void setUp() {
    super.setUp();
    client = vertx.createHttpClient();
  }

  @Test
  public void testResponse(TestContext ctx) {
    String websocketText = "Hello vertx-http-proxy!";

    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      ctx.assertEquals(HttpMethod.GET, req.method());
      ctx.assertEquals("websocket", req.headers().get("upgrade"));

      req.toWebSocket().onSuccess(ws -> ws.writeTextMessage(websocketText));
    });
    startProxy(backend);

    HttpClientOptions clientOptions = new HttpClientOptions();
    clientOptions.setDefaultHost("127.0.0.1");
    clientOptions.setDefaultPort(8080);

    HttpClient client = vertx.createHttpClient(clientOptions);
    Async async = ctx.async();
    client.webSocket("/websocket").onSuccess(ws -> {
      ws.handler(replay -> {
        ctx.assertEquals(replay.toString(), websocketText);
        async.complete();
      });
      ws.exceptionHandler(ctx::fail);
    }).onFailure(ctx::fail);
  }
}
