package io.vertx.httpproxy.interceptors;

import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.httpproxy.ProxyOptions;
import io.vertx.httpproxy.ProxyTestBase;
import org.junit.Test;

import java.util.Set;

/**
 * @author <a href="mailto:wangzengyi1935@163.com">Zengyi Wang</a>
 */
public class HeaderInterceptorTest extends ProxyTestBase {

  public HeaderInterceptorTest(ProxyOptions options) {
    super(options);
  }

  @Test
  public void testFilterRequestHeader(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      ctx.assertEquals(req.headers().get("k1"), "v1");
      ctx.assertEquals(req.headers().get("k2"), null);
      req.response().end("Hello");
    });

    startProxy(proxy -> proxy.origin(backend)
      .addInterceptor(HeadersInterceptor.filterRequestHeaders(Set.of("k2"))));

    vertx.createHttpClient().request(HttpMethod.GET, 8080, "localhost", "/")
      .compose(request -> request
        .putHeader("k1", "v1")
        .putHeader("k2", "v2")
        .send())
      .onComplete(ctx.asyncAssertSuccess(response -> {
        latch.complete();
      }));
  }

  @Test
  public void testFilterResponseHeader(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.response()
        .putHeader("k1", "v1")
        .putHeader("k2", "v2")
        .end("Hello");
    });

    startProxy(proxy -> proxy.origin(backend)
      .addInterceptor(HeadersInterceptor.filterResponseHeaders(Set.of("k2"))));

    vertx.createHttpClient().request(HttpMethod.GET, 8080, "localhost", "/")
      .compose(HttpClientRequest::send)
      .onComplete(ctx.asyncAssertSuccess(resp -> {
        ctx.assertEquals(resp.headers().get("k1"), "v1");
        ctx.assertEquals(resp.headers().get("k2"), null);
        latch.complete();
      }));
  }


}
