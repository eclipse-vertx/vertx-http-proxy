package io.vertx.tests;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.*;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.httpproxy.*;
import org.junit.Test;

import java.io.Closeable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class ProxyContextTest extends ProxyTestBase {

  private WebSocketClient wsClient;

  public ProxyContextTest(ProxyOptions options) {
    super(options);
  }

  @Override
  public void tearDown(TestContext context) {
    super.tearDown(context);
    wsClient = null;
  }

  // same in TestBase, but allow to attach contexts
  private Closeable startProxy(Consumer<HttpProxy> config, Map<String, Object> attachments) {
    CompletableFuture<Closeable> res = new CompletableFuture<>();
    vertx.deployVerticle(new AbstractVerticle() {
      HttpClient proxyClient;
      HttpServer proxyServer;
      HttpProxy proxy;
      @Override
      public void start(Promise<Void> startFuture) {
        proxyClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
        proxyServer = vertx.createHttpServer(new HttpServerOptions(serverOptions));
        proxy = HttpProxy.reverseProxy(proxyOptions, proxyClient);
        config.accept(proxy);
        proxyServer.requestHandler(request -> {
          proxy.handle(request, attachments);
        });
        proxyServer.listen().onComplete(ar -> startFuture.handle(ar.mapEmpty()));
      }
    }).onComplete(ar -> {
      if (ar.succeeded()) {
        String id = ar.result();
        res.complete(() -> {
          CountDownLatch latch = new CountDownLatch(1);
          vertx.undeploy(id).onComplete(ar2 -> latch.countDown());
          try {
            latch.await(10, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
          }
        });
      } else {
        res.completeExceptionally(ar.cause());
      }
    });
    try {
      return res.get(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    } catch (ExecutionException e) {
      throw new AssertionError(e.getMessage());
    } catch (TimeoutException e) {
      throw new AssertionError(e);
    }
  }

  @Test
  public void testOriginSelector(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.response().end("end");
    });
    startProxy(proxy -> {
      proxy.originSelector(context -> Future.succeededFuture(context.get("backend", SocketAddress.class)));
    }, new HashMap<>(Map.of("backend", backend)));
    vertx.createHttpClient().request(HttpMethod.GET, 8080, "localhost", "/")
      .compose(HttpClientRequest::send)
      .onComplete(ctx.asyncAssertSuccess(resp -> {
        ctx.assertEquals(resp.statusCode(), 200);
        latch.complete();
      }));
  }

  @Test
  public void testOriginSelectorWebSocket(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.toWebSocket().onSuccess(ws -> {
        ws.handler(ws::write);
      });
    });
    startProxy(proxy -> {
      proxy.originSelector(context -> Future.succeededFuture(context.get("backend", SocketAddress.class)));
    }, new HashMap<>(Map.of("backend", backend)));
    wsClient = vertx.createWebSocketClient();
    wsClient.connect(8080, "localhost", "/")
      .onComplete(ctx.asyncAssertSuccess(ws -> {
        latch.complete();
      }));
  }

  @Test
  public void testInterceptor(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      if (!req.uri().equals("/new-uri")) {
        req.response().setStatusCode(404).end();
      }
      req.response().end("end");
    });
    startProxy(proxy -> {
      proxy.origin(backend)
        .addInterceptor(new ProxyInterceptor() {
          @Override
          public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
            context.request().setURI(context.get("uri", String.class));
            return context.sendRequest();
          }
        });
    }, new HashMap<>(Map.of("uri", "/new-uri")));
    vertx.createHttpClient().request(HttpMethod.GET, 8080, "localhost", "/")
      .compose(HttpClientRequest::send)
      .onComplete(ctx.asyncAssertSuccess(resp -> {
        ctx.assertEquals(resp.statusCode(), 200);
        latch.complete();
      }));
  }
}
