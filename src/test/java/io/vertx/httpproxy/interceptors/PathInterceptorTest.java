package io.vertx.httpproxy.interceptors;

import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.httpproxy.ProxyOptions;
import io.vertx.httpproxy.ProxyTestBase;
import org.junit.Test;

/**
 * @author <a href="mailto:wangzengyi1935@163.com">Zengyi Wang</a>
 */
public class PathInterceptorTest extends ProxyTestBase {

  public PathInterceptorTest(ProxyOptions options) {
    super(options);
  }

  @Test
  public void addPrefixTest(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      ctx.assertEquals(req.uri(), "/prefix/hello");
      req.response().end("Hello");
    });

    startProxy(proxy -> proxy.origin(backend)
      .addInterceptor(PathInterceptor.addPrefix("/prefix")));

    vertx.createHttpClient().request(HttpMethod.GET, 8080, "localhost", "/hello")
      .compose(HttpClientRequest::send)
      .onComplete(ctx.asyncAssertSuccess(resp -> latch.complete()));
  }

  @Test
  public void removePrefixTest(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      ctx.assertEquals(req.uri(), "/hello");
      req.response().end("Hello");
    });

    startProxy(proxy -> proxy.origin(backend)
      .addInterceptor(PathInterceptor.removePrefix("/prefix")));

    vertx.createHttpClient().request(HttpMethod.GET, 8080, "localhost", "/prefix/hello")
      .compose(HttpClientRequest::send)
      .onComplete(ctx.asyncAssertSuccess(resp -> latch.complete()));
  }

}
