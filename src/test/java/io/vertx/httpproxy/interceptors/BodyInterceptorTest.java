package io.vertx.httpproxy.interceptors;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.httpproxy.ProxyOptions;
import io.vertx.httpproxy.ProxyTestBase;
import org.junit.Test;

import java.util.Arrays;
import java.util.Objects;

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
      .addInterceptor(BodyInterceptor.modifyRequestBody(
        BodyTransformer.transformJsonObject(jsonObject -> {
        jsonObject.remove("k2");
        jsonObject.put("k1", 1);
        return jsonObject;
      }))));

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
      .addInterceptor(BodyInterceptor.modifyResponseBody(
        BodyTransformer.transformJsonObject(jsonObject -> {
          jsonObject.remove("k2");
          jsonObject.put("k1", 1);
          return jsonObject;
        }))));

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

  @Test
  public void testJsonArray(TestContext ctx) {
    Async latch = ctx.async();
    String content = "[1, 2, 3]";
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.response()
        .putHeader("Content-Type", "application/json")
        .end(content);
    });

    startProxy(proxy -> proxy.origin(backend)
      .addInterceptor(BodyInterceptor.modifyResponseBody(
        BodyTransformer.transformJsonArray(array -> {
          array.remove(2);
          return array;
        }
      ))));

    vertx.createHttpClient().request(HttpMethod.POST, 8080, "localhost", "/")
      .compose(HttpClientRequest::send)
      .onComplete(ctx.asyncAssertSuccess(response -> {
        response.body().compose((buffer -> {
          JsonArray jsonArray = buffer.toJsonArray();
          ctx.assertEquals(jsonArray.size(), 2);
          latch.complete();
          return Future.succeededFuture();
        }));
      }));
  }

  @Test
  public void testDiscard(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.response().end("hello");
    });

    startProxy(proxy -> proxy.origin(backend)
      .addInterceptor(BodyInterceptor.modifyResponseBody(BodyTransformer.discard())));

    vertx.createHttpClient().request(HttpMethod.POST, 8080, "localhost", "/")
      .compose(HttpClientRequest::send)
      .onComplete(ctx.asyncAssertSuccess(response -> {
        response.body().compose((buffer -> {
          ctx.assertEquals(buffer.length(), 0);
          latch.complete();
          return Future.succeededFuture();
        }));
      }));
  }

  @Test
  public void testTransformText(TestContext ctx) {
    Async latch = ctx.async();
    byte[] content = new byte[]{-78, -30, -54, -44}; // gbk encoding, represents 测试 in gbk, ���� in utf-8
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.response()
        .end(Buffer.buffer(content));
    });

    startProxy(proxy -> proxy.origin(backend)
      .addInterceptor(BodyInterceptor.modifyResponseBody(
        BodyTransformer.transformText(text -> {
          if ("测试".equals(text)) text = "success";
          return text;
        }, "gbk"))));

    vertx.createHttpClient().request(HttpMethod.POST, 8080, "localhost", "/")
      .compose(HttpClientRequest::send)
      .onComplete(ctx.asyncAssertSuccess(response -> {
        response.body().compose((buffer -> {
          ctx.assertEquals(buffer.toString(), "success");
          latch.complete();
          return Future.succeededFuture();
        }));
      }));
  }

  @Test
  public void testObjectTypes(TestContext ctx) {
    Async latch = ctx.async(3);
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.body().onSuccess(buffer -> {
        Object json = Json.decodeValue(buffer);
        if (json instanceof JsonObject) ctx.assertEquals(((JsonObject) json).getInteger("k"), 1);
        if (json instanceof Integer) ctx.assertEquals(json, 1);
        if (json instanceof JsonArray) ctx.assertEquals(((JsonArray) json).getInteger(0), 1);
        req.response().end("Hello");
      });
    });

    startProxy(proxy -> proxy.origin(backend)
      .addInterceptor(BodyInterceptor.modifyRequestBody(
        BodyTransformer.transformJson(json -> {
          if (json instanceof JsonObject) ((JsonObject) json).put("k", 1);
          if (json instanceof Integer) json = 1;
          if (json instanceof JsonArray) ((JsonArray) json).set(0, 1);
          return json;
        }))));

    String[] contents = new String[]{"{\"k\": 2}", "2", "[2, 1]"};
    for (String content : contents) {
      vertx.createHttpClient().request(HttpMethod.POST, 8080, "localhost", "/")
        .compose(request -> request
          .putHeader("Content-Type", "application/json")
          .putHeader("Content-Length", "" + content.length())
          .write(content)
          .compose(r -> request.send())
        )
        .onComplete(ctx.asyncAssertSuccess(response -> {
          latch.countDown();
        }));
    }

  }

}
