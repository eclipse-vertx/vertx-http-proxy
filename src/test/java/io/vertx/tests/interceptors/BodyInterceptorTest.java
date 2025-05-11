/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.tests.interceptors;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.httpproxy.*;
import io.vertx.tests.ProxyTestBase;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:wangzengyi1935@163.com">Zengyi Wang</a>
 */
public class BodyInterceptorTest extends ProxyTestBase {

  HttpClient client = null;

  public BodyInterceptorTest(ProxyOptions options) {
    super(options);
  }

  @Override
  public void setUp() {
    super.setUp();
    client = vertx.createHttpClient();
  }

  @Override
  public void tearDown(TestContext context) {
    if (client != null) client.close();
    super.tearDown(context);
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
      .addInterceptor(ProxyInterceptor.builder().transformingRequestBody(
        BodyTransformers.jsonObject(jsonObject -> {
        jsonObject.remove("k2");
        jsonObject.put("k1", 1);
        return jsonObject;
      })).build()));

    String content = "{\"k2\": 2}";
    client.request(HttpMethod.POST, 8080, "localhost", "/")
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
      .addInterceptor(ProxyInterceptor.builder().transformingResponseBody(
        BodyTransformers.jsonObject(jsonObject -> {
          jsonObject.remove("k2");
          jsonObject.put("k1", 1);
          return jsonObject;
        })).build()));

    client.request(HttpMethod.POST, 8080, "localhost", "/")
      .compose(HttpClientRequest::send)
      .onComplete(ctx.asyncAssertSuccess(response -> {
        ctx.assertEquals("application/json", response.getHeader(HttpHeaders.CONTENT_TYPE));
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
      .addInterceptor(ProxyInterceptor.builder().transformingResponseBody(
        BodyTransformers.jsonArray(array -> {
          array.remove(2);
          return array;
        }
      )).build()));

    client.request(HttpMethod.POST, 8080, "localhost", "/")
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
      req.response()
        .putHeader(HttpHeaders.CONTENT_TYPE, "text/plain")
        .end("hello");
    });

    startProxy(proxy -> proxy.origin(backend)
      .addInterceptor(ProxyInterceptor.builder().transformingResponseBody(BodyTransformers.discard()).build()));

    client.request(HttpMethod.POST, 8080, "localhost", "/")
      .compose(HttpClientRequest::send)
      .onComplete(ctx.asyncAssertSuccess(response -> {
        ctx.assertNull(response.getHeader(HttpHeaders.CONTENT_TYPE));
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
    byte[] content = new byte[]{-78, -30, -54, -44}; // gbk encoding, represents 测试 in gbk
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.response()
        .putHeader(HttpHeaders.CONTENT_TYPE, "text/plain")
        .end(Buffer.buffer(content));
    });

    startProxy(proxy -> proxy.origin(backend)
      .addInterceptor(ProxyInterceptor.builder().transformingResponseBody(
        BodyTransformers.text(text -> {
          if ("测试".equals(text))
            text = "success";
          return text;
        }, "gbk")).build()));

    client.request(HttpMethod.POST, 8080, "localhost", "/")
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
      .addInterceptor(ProxyInterceptor.builder().transformingRequestBody(
        BodyTransformers.jsonValue(json -> {
          if (json instanceof JsonObject) ((JsonObject) json).put("k", 1);
          if (json instanceof Integer) json = 1;
          if (json instanceof JsonArray) ((JsonArray) json).set(0, 1);
          return json;
        })).build()));

    String[] contents = new String[]{"{\"k\": 2}", "2", "[2, 1]"};
    for (String content : contents) {
      client.request(HttpMethod.POST, 8080, "localhost", "/")
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

  @Test
  public void testResponseMaxBufferedBytes(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.response()
        .putHeader("Content-Type", "application/json")
        .end(Buffer.buffer("A".repeat(1024)));
    });

    startProxy(proxy -> proxy.origin(backend)
      .addInterceptor(ProxyInterceptor.builder().transformingResponseBody(null, MediaType.APPLICATION_JSON, buffer -> buffer, 512).build()));

    client.request(HttpMethod.POST, 8080, "localhost", "/")
      .compose(HttpClientRequest::send)
      .onComplete(ctx.asyncAssertSuccess(response -> {
        ctx.assertEquals(500, response.statusCode());
        response.body().onComplete(ctx.asyncAssertSuccess(body -> {
          ctx.assertEquals(0, body.length());
          latch.complete();
        }));
      }));
  }

  @Test
  public void testRequestMaxBufferedBytes(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      ctx.fail();
    });

    startProxy(proxy -> proxy.origin(backend)
      .addInterceptor(ProxyInterceptor.builder().transformingRequestBody(null, MediaType.APPLICATION_JSON, buffer -> {
        ctx.fail();
        return buffer;
      }, 512).build()));

    client.request(HttpMethod.POST, 8080, "localhost", "/")
      .compose(request -> request.send(Buffer.buffer("A".repeat(1024)))
      .onComplete(ctx.asyncAssertSuccess(response -> {
        ctx.assertEquals(500, response.statusCode());
        response.body().onComplete(ctx.asyncAssertSuccess(body -> {
          ctx.assertEquals(0, body.length());
          latch.complete();
        }));
      })));
  }

  @Test
  public void testResponseBodyTransformationError1(TestContext ctx) {
    testResponseTransformationError(ctx, ProxyInterceptor.builder().transformingResponseBody(null, MediaType.APPLICATION_JSON, buffer -> {
      throw new RuntimeException();
    }).build());
  }

  @Test
  public void testResponseBodyTransformationError2(TestContext ctx) {
    testResponseTransformationError(ctx, ProxyInterceptor.builder().transformingResponseBody(new BodyTransformer() {
      @Override
      public boolean consumes(MediaType mediaType) {
        throw new RuntimeException();
      }
      @Override
      public Future<Body> transform(Body body) {
        return Future.succeededFuture();
      }
    }).build());
  }

  @Test
  public void testResponseBodyTransformationError3(TestContext ctx) {
    testResponseTransformationError(ctx, ProxyInterceptor.builder().transformingResponseBody(new BodyTransformer() {
      @Override
      public boolean consumes(MediaType mediaType) {
        return true;
      }
      @Override
      public MediaType produces(MediaType mediaType) {
        throw new RuntimeException();
      }
    }).build());
  }

  @Test
  public void testResponseHeadTransformationError(TestContext ctx) {
    testResponseTransformationError(ctx, ProxyInterceptor.builder().transformingResponseHeaders(headers -> {
      throw new RuntimeException();
    }).build());
  }

  private void testResponseTransformationError(TestContext ctx, ProxyInterceptor interceptor) {
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.response()
        .putHeader("Content-Type", "application/json")
        .end();
    });

    startProxy(proxy -> proxy.origin(backend)
      .addInterceptor(interceptor));

    client.request(HttpMethod.POST, 8080, "localhost", "/")
      .compose(HttpClientRequest::send)
      .onComplete(ctx.asyncAssertSuccess(response -> {
        ctx.assertEquals(500, response.statusCode());
        response.body().onComplete(ctx.asyncAssertSuccess(body -> {
          ctx.assertEquals(0, body.length());
          latch.complete();
        }));
      }));
  }

  @Test
  public void testRequestBodyTransformationError(TestContext ctx) {
    testRequestTransformationError(ctx, ProxyInterceptor.builder().transformingRequestBody(null, MediaType.APPLICATION_JSON, buffer -> {
      throw new RuntimeException();
    }).build());
  }

  @Test
  public void testRequestHeadTransformationError(TestContext ctx) {
    testRequestTransformationError(ctx, ProxyInterceptor.builder().transformingRequestHeaders(headers -> {
      throw new RuntimeException();
    }).build());
  }

  private void testRequestTransformationError(TestContext ctx, ProxyInterceptor interceptor) {
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      ctx.fail();
    });

    startProxy(proxy -> proxy.origin(backend).addInterceptor(interceptor));

    client.request(HttpMethod.POST, 8080, "localhost", "/")
      .compose(request -> request.send(Buffer.buffer("A".repeat(1024)))
        .onComplete(ctx.asyncAssertSuccess(response -> {
          ctx.assertEquals(500, response.statusCode());
          response.body().onComplete(ctx.asyncAssertSuccess(body -> {
            ctx.assertEquals(0, body.length());
            latch.complete();
          }));
        })));
  }

  @Test
  public void testRequestConsumes1(TestContext ctx) {
    testRequestConsumes(ctx, "application/json", MediaType.APPLICATION_JSON, true);
  }

  @Test
  public void testRequestConsumes4(TestContext ctx) {
    testRequestConsumes(ctx, "text/plain", MediaType.APPLICATION_JSON, false);
  }

  private void testRequestConsumes(TestContext ctx,
                                    String contentType,
                                    MediaType interceptorConsumes,
                                    boolean expectedInvoked) {

    AtomicBoolean invoked = new AtomicBoolean();
    BodyTransformer transformer = new BodyTransformer() {
      @Override
      public boolean consumes(MediaType mediaType) {
        return interceptorConsumes.accepts(mediaType);
      }
      @Override
      public MediaType produces(MediaType mediaType) {
        return interceptorConsumes;
      }
      @Override
      public Future<Body> transform(Body body) {
        invoked.set(true);
        return BodyTransformer.super.transform(body);
      }
    };

    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.response()
        .end();
    });

    startProxy(proxy -> proxy.origin(backend)
      .addInterceptor(ProxyInterceptor.builder().transformingRequestBody(transformer).build()));

    client.request(HttpMethod.POST, 8080, "localhost", "/")
      .compose(request -> request
        .putHeader(HttpHeaders.CONTENT_TYPE, contentType)
        .send().map(response -> response.getHeader(HttpHeaders.CONTENT_TYPE)))
      .await();

    assertEquals(expectedInvoked, invoked.get());
  }

  @Test
  public void testResponseConsumes1(TestContext ctx) {
    testResponseConsumes(ctx, "application/json", MediaType.APPLICATION_JSON, true);
  }

  @Test
  public void testResponseConsumes4(TestContext ctx) {
    testResponseConsumes(ctx, "text/plain", MediaType.APPLICATION_JSON, false);
  }

  private void testResponseConsumes(TestContext ctx,
                                   String contentType,
                                   MediaType interceptorConsumes,
                                   boolean expectedInvoked) {

    AtomicBoolean invoked = new AtomicBoolean();
    BodyTransformer transformer = new BodyTransformer() {
      @Override
      public boolean consumes(MediaType mediaType) {
        return interceptorConsumes.accepts(mediaType);
      }
      @Override
      public MediaType produces(MediaType mediaType) {
        return interceptorConsumes;
      }
      @Override
      public Future<Body> transform(Body body) {
        invoked.set(true);
        return BodyTransformer.super.transform(body);
      }
    };

    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.response()
        .putHeader(HttpHeaders.CONTENT_TYPE, contentType)
        .end();
    });

    startProxy(proxy -> proxy.origin(backend)
      .addInterceptor(ProxyInterceptor.builder().transformingResponseBody(transformer).build()));

    client.request(HttpMethod.GET, 8080, "localhost", "/")
      .compose(request -> request
        .putHeader(HttpHeaders.CONTENT_TYPE, "*/*")
        .send().compose(HttpClientResponse::end))
      .await();

    assertEquals(expectedInvoked, invoked.get());
  }

  @Test
  public void testResponseProduces1(TestContext ctx) {
    String contentType = testResponseProduces(ctx, "application/json", MediaType.APPLICATION_JSON, "application/json", true);
    assertEquals("application/json", contentType);
  }

  @Test
  public void testResponseProduces2(TestContext ctx) {
    String contentType = testResponseProduces(ctx, "application/*", MediaType.APPLICATION_JSON, "application/json", true);
    assertEquals("application/json", contentType);
  }

  @Test
  public void testResponseProduces3(TestContext ctx) {
    String contentType = testResponseProduces(ctx, "*/*", MediaType.APPLICATION_JSON, "application/json", true);
    assertEquals("application/json", contentType);
  }

  @Test
  public void testResponseProduces4(TestContext ctx) {
    String contentType = testResponseProduces(ctx, "application/json,text/plain", MediaType.APPLICATION_JSON, "application/json", true);
    assertEquals("application/json", contentType);
  }

  @Test
  public void testResponseProduces5(TestContext ctx) {
    String contentType = testResponseProduces(ctx, "application/json;q=0.1,text/plain", MediaType.APPLICATION_JSON, "application/json", true);
    assertEquals("application/json", contentType);
  }

  @Test
  public void testResponseProduces6(TestContext ctx) {
    String contentType = testResponseProduces(ctx, "text/plain", MediaType.APPLICATION_JSON, "text/plain", false);
    assertEquals("text/plain", contentType);
  }

  private String testResponseProduces(TestContext ctx,
                                      String acceptHeader,
                                      MediaType interceptorProduces,
                                      String responseContentType,
                                      boolean expectedInvoked) {

    AtomicBoolean invoked = new AtomicBoolean();
    BodyTransformer transformer = new BodyTransformer() {
      @Override
      public boolean consumes(MediaType mediaType) {
        return true;
      }
      @Override
      public MediaType produces(MediaType mediaType) {
        return interceptorProduces;
      }
      @Override
      public Future<Body> transform(Body body) {
        invoked.set(true);
        return BodyTransformer.super.transform(body);
      }
    };

    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.response()
        .putHeader(HttpHeaders.CONTENT_TYPE, responseContentType)
        .end();
    });

    startProxy(proxy -> proxy.origin(backend)
      .addInterceptor(ProxyInterceptor.builder().transformingResponseBody(transformer).build()));

    String ret = client.request(HttpMethod.POST, 8080, "localhost", "/")
      .compose(request -> request
        .putHeader(HttpHeaders.ACCEPT, acceptHeader)
        .send().map(response -> response.getHeader(HttpHeaders.CONTENT_TYPE)))
      .await();

    assertEquals(expectedInvoked, invoked.get());

    return ret;
  }

  @Test
  public void testInvalidContentType(TestContext ctx) {
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      ctx.assertEquals("invalid request content type", req.getHeader(HttpHeaders.CONTENT_TYPE));
      req.body().onSuccess(buffer -> {
        req
          .response()
          .putHeader(HttpHeaders.CONTENT_TYPE, "invalid response content type")
          .end();
      });
    });
    BodyTransformer transformer = new BodyTransformer() {
      @Override
      public boolean consumes(MediaType mediaType) {
        return true;
      }
      @Override
      public Future<Body> transform(Body body) {
        ctx.fail();
        return BodyTransformer.super.transform(body);
      }
    };
    startProxy(proxy -> {
      proxy.origin(backend)
        .addInterceptor(ProxyInterceptor.builder()
          .transformingRequestBody(transformer)
          .transformingResponseBody(transformer)
          .build());
    });
    String contentType = client.request(HttpMethod.GET, 8080, "localhost", "/")
      .compose(request -> request
        .putHeader(HttpHeaders.CONTENT_TYPE, "invalid request content type")
        .send()
        .map(req -> req.getHeader(HttpHeaders.CONTENT_TYPE))
      ).await();
    assertEquals("invalid response content type", contentType);
  }

  @Test
  public void testInvalidAcceptHeader(TestContext ctx) {
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.body().onSuccess(buffer -> {
        req
          .response()
          .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
          .end();
      });
    });
    BodyTransformer transformer = new BodyTransformer() {
      @Override
      public boolean consumes(MediaType mediaType) {
        return true;
      }
      @Override
      public Future<Body> transform(Body body) {
        ctx.fail();
        return BodyTransformer.super.transform(body);
      }
    };
    startProxy(proxy -> {
      proxy.origin(backend)
        .addInterceptor(ProxyInterceptor.builder()
          .transformingResponseBody(transformer)
          .build());
    });
    client.request(HttpMethod.GET, 8080, "localhost", "/")
      .compose(request -> request
        .putHeader(HttpHeaders.ACCEPT, "invalid accept header")
        .send()
        .map(HttpClientResponse::end)
      ).await();
  }
}
