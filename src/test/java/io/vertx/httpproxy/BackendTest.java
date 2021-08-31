package io.vertx.httpproxy;

import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.junit.Test;

/**
 * @author <a href="mailto:emad.albloushi@gmail.com">Emad Alblueshi</a>
 */

public class BackendTest extends ProxyTestBase {

  private HttpClient client;

  public BackendTest(ProxyOptions options) {
    super(options);
  }

  @Override
  public void setUp() {
    super.setUp();
    client = vertx.createHttpClient();
  }

  @Test
  public void testResponse(TestContext ctx) {
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      ctx.assertEquals(HttpMethod.POST, req.method());
      req.response()
        .addCookie(Cookie.cookie("some-cookie-name", "some-cookie-value"))
        .putHeader("some-header", "some-header-value")
        .setStatusCode(203)
        .setStatusMessage("some-status-message")
        .end("some-data");
    });
    startProxy(backend);
    HttpClient client = vertx.createHttpClient();
    Async async = ctx.async();
    client.request(HttpMethod.POST, 8080, "localhost", "/path")
      .compose(req -> req.send().compose(resp -> {
        ctx.assertEquals(1, resp.cookies().size());
        ctx.assertEquals("some-cookie-name=some-cookie-value", resp.cookies().get(0));
        ctx.assertEquals("some-header-value", resp.getHeader("some-header"));
        ctx.assertEquals(203, resp.statusCode());
        ctx.assertEquals("some-status-message", resp.statusMessage());
        return resp.body();
      }))
      .onComplete(ctx.asyncAssertSuccess(body -> {
        ctx.assertEquals("some-data", body.toString());
        async.complete();
      }));
  }
}
