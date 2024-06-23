package io.vertx.httpproxy.interceptors;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.httpproxy.ProxyOptions;
import io.vertx.httpproxy.ProxyTestBase;
import org.junit.Test;

/**
 * @author <a href="mailto:wangzengyi1935@163.com">Zengyi Wang</a>
 */
public class BodyInterceptorTest extends ProxyTestBase {
  public BodyInterceptorTest(ProxyOptions options) {
    super(options);
  }

  @Test
  public void testModifyRequestJson(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.body().onSuccess(buffer -> {
        JsonObject jsonObject = buffer.toJsonObject();
        ctx.assertEquals(jsonObject.getInteger("k1"), 1);
        ctx.assertEquals(jsonObject.getInteger("k2"), null);
        req.response().end("Hello");
      });
    });

    startProxy(proxy -> proxy.origin(backend)
      .addInterceptor(BodyInterceptor.modifyRequestBody(jsonObject -> {
        jsonObject.remove("k2");
        jsonObject.put("k1", 1);
        return jsonObject;
      }, JsonObject.class)));

    String content = "{\"k2\": 2}";
    vertx.createHttpClient().request(HttpMethod.POST, 8080, "localhost", "/")
      .compose(request -> request
        .putHeader("Content-Type", "application/json")
        .putHeader("Content-Length", "" + content.length())
        .write(content)
        .compose(r -> request.send())
      )
      .onComplete(ctx.asyncAssertSuccess(response -> {
        latch.complete();
      }));
  }

  @Test
  public void testModifyResponseJson(TestContext ctx) {
    Async latch = ctx.async();
    String content = "{\"k2\": 2}";
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.response()
        .putHeader("Content-Type", "application/json")
        .end(content);
    });

    startProxy(proxy -> proxy.origin(backend)
      .addInterceptor(BodyInterceptor.modifyResponseBody(jsonObject -> {
        jsonObject.remove("k2");
        jsonObject.put("k1", 1);
        return jsonObject;
      }, JsonObject.class)));

    vertx.createHttpClient().request(HttpMethod.POST, 8080, "localhost", "/")
      .compose(HttpClientRequest::send)
      .onComplete(ctx.asyncAssertSuccess(response -> {
        response.body().compose((buffer -> {
          JsonObject jsonObject = buffer.toJsonObject();
          ctx.assertEquals(jsonObject.getInteger("k1"), 1);
          ctx.assertEquals(jsonObject.getInteger("k2"), null);
          latch.complete();
          return Future.succeededFuture();
        }));
      }));
  }

}
