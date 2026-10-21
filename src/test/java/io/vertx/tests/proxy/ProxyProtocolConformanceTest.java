/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.proxy;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.WriteStream;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.tests.TestBase;
import org.junit.Test;

import java.io.Closeable;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.HEAD;

public class ProxyProtocolConformanceTest extends TestBase {

  private HttpClient client;

  @Override
  public void tearDown(TestContext context) {
    super.tearDown(context);
    client = null;
  }

  @Test
  public void testBackendRepliesIncorrectHttpVersion(TestContext ctx) {
    SocketAddress backend = startNetBackend(ctx, 8081, so -> {
      so.write("HTTP/1.2 200 OK\r\n\r\n");
      so.close();
    });
    startProxy(backend);
    client = vertx.createHttpClient();
    client.request(GET, 8080, "localhost", "/")
        .compose(req ->
      req.send().compose(resp -> {
        ctx.assertEquals(502, resp.statusCode());
        return resp.body();
      })).onComplete(ctx.asyncAssertSuccess(b -> {
    }));
  }

  @Test
  public void testChunkedResponseToHttp1_1Client(TestContext ctx) {
    checkChunkedResponse(ctx, HttpVersion.HTTP_1_1);
  }

  @Test
  public void testChunkedResponseToHttp1_0Client(TestContext ctx) {
    checkChunkedResponse(ctx, HttpVersion.HTTP_1_0);
  }

  private void checkChunkedResponse(TestContext ctx, HttpVersion version) {
    int num = 50;
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      HttpServerResponse resp = req.response();
      resp.setChunked(true);
      streamChunkedBody(resp, num);
    });
    startProxy(backend);
    client = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(version));
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < num; i++) {
      sb.append("chunk-").append(i);
    }
    client.request(GET, 8080, "localhost", "/").onComplete(ctx.asyncAssertSuccess(req -> {
      req.send().onComplete(ctx.asyncAssertSuccess(resp -> {
        if (version == HttpVersion.HTTP_1_1) {
          ctx.assertEquals("chunked", resp.getHeader("transfer-encoding"));
          ctx.assertEquals(null, resp.getHeader("content-length"));
        } else {
          ctx.assertEquals(null, resp.getHeader("transfer-encoding"));
          ctx.assertEquals("" + sb.length(), resp.getHeader("content-length"));
        }
        resp.handler(buff -> {
          String part = buff.toString();
          if (sb.indexOf(part) == 0) {
            sb.delete(0, part.length());
          } else {
            ctx.fail();
          }
        });
        resp.endHandler(v -> {
          ctx.assertEquals("", sb.toString());
          latch.complete();
        });
      }));
    }));
  }

  @Test
  public void testIllegalClientHttpVersion(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      ctx.fail();
    });
    startProxy(backend);
    NetClient client = vertx.createNetClient();
    client.connect(8080, "localhost").onComplete(ctx.asyncAssertSuccess(so -> {
      Buffer resp = Buffer.buffer();
      so.handler(resp::appendBuffer);
      so.closeHandler(v -> {
        ctx.assertTrue(resp.toString().startsWith("HTTP/1.1 501 Not Implemented\r\n"));
        latch.complete();
      });
      so.write("GET /somepath http/1.1\r\n\r\n");
    }));
  }

  @Test
  public void testHandleLongInitialLength(TestContext ctx) {
    serverOptions.setMaxInitialLineLength(10000);
    Async latch = ctx.async();
    String uri = "/" + randomAlphaString(5999);
    SocketAddress backend = startHttpBackend(ctx, new HttpServerOptions().setPort(8081).setMaxInitialLineLength(10000), req -> {
      ctx.assertEquals(uri, req.uri());
      req.response().end();
    });
    startProxy(backend);
    client = vertx.createHttpClient();
    client.request(GET, 8080, "localhost", "" + uri).onComplete(ctx.asyncAssertSuccess(req -> {
      req.send().onComplete(ctx.asyncAssertSuccess(resp -> {
        ctx.assertEquals(200, resp.statusCode());
        latch.complete();
      }));
    }));
  }

  @Test
  public void testLargeChunkExtValue(TestContext ctx) {
    String s = "" + randomAlphaString(4096);
    Async latch = ctx.async();
    SocketAddress backend = startNetBackend(ctx, 8081, so -> {
      Buffer body = Buffer.buffer();
      so.handler(buff -> {
        body.appendBuffer(buff);
        if (body.toString().endsWith("\r\n\r\n")) {
          so.write("" +
              "HTTP/1.1 200 OK\r\n" +
              "Transfer-Encoding: chunked\r\n" +
              "connection: close\r\n" +
              "\r\n" +
              "A; name=\"" + s + "\"\r\n" +
              "0123456789\r\n" +
              "0\r\n" +
              "\r\n"
          );
        }
      });
    });
    clientOptions.setMaxInitialLineLength(5000);
    startProxy(backend);
    client = vertx.createHttpClient(/*new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_1_0)*/);
    client.request(GET, 8080, "localhost", "/somepath").compose(req ->
      req.send().compose(resp -> {
        ctx.assertEquals(200, resp.statusCode());
        return resp.body();
      })
    ).onComplete(ctx.asyncAssertSuccess(body -> {
      ctx.assertEquals("0123456789", body.toString());
      latch.complete();
    }));
  }

  @Test
  public void testRequestIllegalTransferEncoding1(TestContext ctx) throws Exception {
    checkBadRequest(ctx,
        "POST /somepath HTTP/1.1\r\n" +
        "transfer-encoding: identity\r\n" +
        "connection: close\r\n" +
        "\r\n",
        "POST /somepath HTTP/1.1\r\n" +
            "transfer-encoding: chunked, identity\r\n" +
            "connection: close\r\n" +
            "\r\n",
        "POST /somepath HTTP/1.1\r\n" +
            "transfer-encoding: identity, chunked\r\n" +
            "connection: close\r\n" +
            "\r\n",
        "POST /somepath HTTP/1.1\r\n" +
            "transfer-encoding: identity\r\n" +
            "transfer-encoding: chunked\r\n" +
            "connection: close\r\n" +
            "\r\n",
        "POST /somepath HTTP/1.1\r\n" +
            "transfer-encoding: chunked\r\n" +
            "transfer-encoding: identity\r\n" +
            "connection: close\r\n" +
            "\r\n",
        "POST /somepath HTTP/1.1\r\n" +
            "transfer-encoding: other, chunked\r\n" +
            "connection: close\r\n" +
            "\r\n",
        "POST /somepath HTTP/1.1\r\n" +
            "transfer-encoding: other\r\n" +
            "transfer-encoding: chunked\r\n" +
            "connection: close\r\n" +
            "\r\n");
  }

  private void checkBadRequest(TestContext ctx, String... requests) throws Exception {
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      ctx.fail();
    });
    startProxy(backend);
    for (String request : requests) {
      Async latch = ctx.async();
      NetClient client = vertx.createNetClient();
      try {
        client.connect(8080, "localhost").onComplete(ctx.asyncAssertSuccess(so -> {
          Buffer resp = Buffer.buffer();
          so.handler(buff -> {
            resp.appendBuffer(buff);
            if (resp.toString().startsWith("HTTP/1.1 400 Bad Request\r\n")) {
              latch.complete();
            }
          });
          so.write(request);
        }));
        latch.awaitSuccess(10000);
      } finally {
        client.close();
      }
    }
  }

  @Test
  public void testResponseIllegalTransferEncoding(TestContext ctx) throws Exception {
    checkBadResponse(ctx, "" +
        "HTTP/1.1 200 OK\r\n" +
        "Transfer-Encoding: other, chunked\r\n" +
        "connection: close\r\n" +
        "\r\n" +
        "A\r\n" +
        "0123456789\r\n" +
        "0\r\n" +
        "\r\n", "" +
        "HTTP/1.1 200 OK\r\n" +
        "Transfer-Encoding: other\r\n" +
        "Transfer-Encoding: chunked\r\n" +
        "connection: close\r\n" +
        "\r\n" +
        "A\r\n" +
        "0123456789\r\n" +
        "0\r\n" +
        "\r\n");
  }

  @Test
  public void testRawMethod(TestContext ctx) throws Exception {
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      ctx.assertEquals("FOO", req.method().name());
      req.response().end();
    });
    startProxy(backend);
    client = vertx.createHttpClient();
    client.request(HttpMethod.valueOf("FOO"), 8080, "localhost", "/")
        .compose(req -> req.send().compose(HttpClientResponse::body))
        .onComplete(ctx.asyncAssertSuccess());
  }

  @Test
  public void testHead(TestContext ctx) throws Exception {
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      ctx.assertEquals(HEAD, req.method());
      req.response().putHeader("content-length", "" + "content".length()).end();
    });
    startProxy(backend);
    client = vertx.createHttpClient();
    client.request(HEAD, 8080, "localhost", "/").compose(req ->
      req.send().compose(HttpClientResponse::body)
    ).onComplete(ctx.asyncAssertSuccess(body -> {
      ctx.assertEquals("", body.toString());
      latch.complete();
    }));
  }

  // TODO test we don't filter content...
  @Test
  public void testHeadWithNotSendBody(TestContext ctx) throws Exception {
    Async latch = ctx.async();
    SocketAddress backend = startNetBackend(ctx, 8081, so -> {
      so.write(
        "HTTP/1.1 200 OK\r\n" +
          "content-length: 20\r\n" +
          "\r\n" +
          "0123456789"
      );
    });
    startProxy(backend);
    client = vertx.createHttpClient();
    client.request(HEAD, 8080, "localhost", "/").compose(req ->
      req.send().compose(HttpClientResponse::body)
    ).onComplete(ctx.asyncAssertSuccess(body -> {
      ctx.assertEquals("", body.toString());
      latch.complete();
    }));
  }

  private void checkBadResponse(TestContext ctx, String... responses) throws Exception {
    AtomicReference<String> responseBody = new AtomicReference<>();
    SocketAddress backend = startNetBackend(ctx, 8081, so -> {
      Buffer body = Buffer.buffer();
      so.handler(buff -> {
        body.appendBuffer(buff);
        if (body.toString().endsWith("\r\n\r\n")) {
          so.write(responseBody.get());
        }
      });
    });
    for (String response : responses) {
      responseBody.set(response);
      Async latch = ctx.async();
      client = vertx.createHttpClient();
      try (Closeable proxy = startProxy(backend)) {
        client.request(GET, 8080, "localhost", "/somepath")
            .compose(req -> req.send().compose(resp -> {
              ctx.assertEquals(501, resp.statusCode());
              return resp.body();
            })).onComplete(ctx.asyncAssertSuccess(body -> {
          ctx.assertEquals("", body.toString());
          latch.complete();
        }));
        latch.awaitSuccess(10000);
      } finally {
        client.close();
      }
    }
  }

  private void streamChunkedBody(WriteStream<Buffer> stream, int num) {
    AtomicInteger count = new AtomicInteger(0);
    vertx.setPeriodic(10, id -> {
      int val = count.getAndIncrement();
      if (val < num) {
        stream.write(Buffer.buffer("chunk-" + val));
      } else {
        vertx.cancelTimer(id);
        stream.end();
      }
    });
  }

  private StringBuilder randomAlphaString(int len) {
    Random random = new Random();
    StringBuilder uri = new StringBuilder();
    for (int i = 0; i < len; i++) {
      uri.append((char) ('A' + random.nextInt(26)));
    }
    return uri;
  }
}
