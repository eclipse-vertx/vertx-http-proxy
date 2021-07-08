package io.vertx.httpproxy;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.junit.Test;

import java.util.UUID;

public class WebSocketTest extends ProxyTestBase {

  @Test
  public void simpleTest(TestContext ctx) {
    String webSocketText = "Hello vertx-http-proxy!";

    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      ctx.assertEquals(HttpMethod.GET, req.method());
      ctx.assertEquals("websocket", req.headers().get("upgrade"));

      req.toWebSocket().onSuccess(ws -> ws.writeTextMessage(webSocketText));
    });
    startProxy(backend);

    HttpClientOptions clientOptions = new HttpClientOptions();
    clientOptions.setDefaultHost("127.0.0.1");
    clientOptions.setDefaultPort(8080);

    HttpClient client = vertx.createHttpClient(clientOptions);
    Async async = ctx.async();
    client.webSocket("/websocket").onSuccess(ws -> {
      ws.handler(replay -> {
        ctx.assertEquals(replay.toString(), webSocketText);
        async.complete();
      });
      ws.exceptionHandler(ctx::fail);
    }).onFailure(ctx::fail);
  }

  @Test
  public void complexTest(TestContext ctx) {
    String uuidInHeader = UUID.randomUUID().toString();
    String uri = "/websocket";
    int backendPost = 8081;

    SocketAddress backend = startHttpBackend(ctx, backendPost, req -> {
      ctx.assertEquals(HttpMethod.GET, req.method());
      ctx.assertEquals("websocket", req.getHeader("upgrade"));
      ctx.assertEquals(uuidInHeader, req.getHeader("uuid"));
      ctx.assertEquals(uri, req.uri());
      ctx.assertTrue(req.host().endsWith(String.valueOf(backendPost)));


      req.toWebSocket()
        .onSuccess(ws -> ws.writeTextMessage(req.getHeader("uuid")))
        .onFailure(ctx::fail);
    });
    startProxy(backend);

    HttpClientOptions clientOptions = new HttpClientOptions();
    clientOptions.setDefaultHost("127.0.0.1");
    clientOptions.setDefaultPort(8080);

    HttpClient client = vertx.createHttpClient(clientOptions);
    Async async = ctx.async();

    WebSocketConnectOptions webSocketConnectOptions = new WebSocketConnectOptions();
    webSocketConnectOptions.putHeader("uuid", uuidInHeader);
    webSocketConnectOptions.setURI(uri);

    client
      .webSocket(webSocketConnectOptions)
      .onSuccess(ws -> {
        ws.handler(replay -> {
          ctx.assertEquals(replay.toString(), uuidInHeader);
          async.complete();
        });
        ws.exceptionHandler(ctx::fail);
      }).onFailure(ctx::fail);
  }

}
