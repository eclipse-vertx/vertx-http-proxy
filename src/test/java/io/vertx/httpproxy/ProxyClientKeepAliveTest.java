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
package io.vertx.httpproxy;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.WriteStream;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.junit.Test;

import java.io.Closeable;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.HEAD;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ProxyClientKeepAliveTest extends ProxyTestBase {

  protected boolean keepAlive = true;
  protected boolean pipelining = false;

  public ProxyClientKeepAliveTest(ProxyOptions options) {
    super(options);
  }

  @Override
  public void setUp() {
    super.setUp();
    clientOptions.setKeepAlive(keepAlive).setPipelining(pipelining);
  }

/*
  @Test
  public void testNotfound(TestContext ctx) {
    startProxy(ctx);
    HttpClient client = vertx.createHttpClient();
    Async async = ctx.async();
    client.getNow(8080, "localhost", "/", resp -> {
      ctx.assertEquals(404, resp.statusCode());
      async.complete();
    });
  }
*/

  @Test
  public void testGet(TestContext ctx) {
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      ctx.assertEquals("/somepath", req.uri());
      ctx.assertEquals("localhost:8080", req.host());
      req.response().end("Hello World");
    });
    startProxy(backend);
    HttpClient client = vertx.createHttpClient();
    client.request(GET, 8080, "localhost", "/somepath")
        .compose(req -> req
            .send()
            .compose(resp -> {
              ctx.assertEquals(200, resp.statusCode());
              return resp.body();
            }))
        .onComplete(ctx.asyncAssertSuccess(body -> {
          ctx.assertEquals("Hello World", body.toString());
        }));
  }

  @Test
  public void testPost(TestContext ctx) {
    byte[] body = new byte[1024];
    Random random = new Random();
    random.nextBytes(body);
    Async async = ctx.async(2);
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.bodyHandler(buff -> {
        req.response().end();
        ctx.assertEquals(Buffer.buffer(body), buff);
        async.countDown();
      });
    });
    startProxy(backend);
    HttpClient client = vertx.createHttpClient();
    client.request(GET, 8080, "localhost", "/")
        .compose(req -> req
            .send(Buffer.buffer(body))
            .compose(resp -> {
              ctx.assertEquals(200, resp.statusCode());
              return resp.body();
            }))
        .onComplete(ctx.asyncAssertSuccess(buff -> async.countDown()));
  }

  @Test
  public void testBackendClosesDuringUpload(TestContext ctx) {
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      AtomicInteger len = new AtomicInteger();
      req.handler(buff -> {
        if (len.addAndGet(buff.length()) == 1024) {
          req.connection().close();
        }
      });
    });
    startProxy(backend);
    HttpClient client = vertx.createHttpClient();
    Async async = ctx.async();
    client.request(HttpMethod.POST, 8080, "localhost", "/")
        .onComplete(ctx.asyncAssertSuccess(req -> {
          req.response().onComplete(ctx.asyncAssertSuccess(resp -> {
            ctx.assertEquals(502, resp.statusCode());
            async.complete();
          }));
          req.putHeader("Content-Length", "2048");
          req.write(Buffer.buffer(new byte[1024]));
        }));
  }

  @Test
  public void testClientClosesDuringUpload(TestContext ctx) {
    Async async = ctx.async();
    Promise<Void> closeLatch = Promise.promise();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.response().closeHandler(v -> {
        async.complete();
      });
      req.handler(buff -> {
        closeLatch.tryComplete();
      });
    });
    startProxy(backend);
    HttpClient client = vertx.createHttpClient();
    client.request(HttpMethod.POST, 8080, "localhost", "/")
        .onComplete(ctx.asyncAssertSuccess(req -> {
          req.response().onComplete(ctx.asyncAssertFailure());
          req.putHeader("Content-Length", "2048");
          req.write(Buffer.buffer(new byte[1024]));
          closeLatch.future().onComplete(ar -> {
            req.connection().close();
          });
        }));
  }

  @Test
  public void testClientClosesAfterUpload(TestContext ctx) {
    Async async = ctx.async();
    Promise<Void> closeLatch = Promise.promise();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.endHandler(v -> {
        closeLatch.complete();
        vertx.setTimer(200, id -> {
          req.response().setChunked(true).write("partial response");
        });
      });
      req.response().closeHandler(v -> {
        async.complete();
      });
    });
    startProxy(backend);
    HttpClient client = vertx.createHttpClient();
    client.request(HttpMethod.POST, 8080, "localhost", "/")
        .onComplete(ctx.asyncAssertSuccess(req -> {
          closeLatch.future().onSuccess(v -> {
            HttpConnection conn = req.connection();
            conn.close();
          });
          req.send(Buffer.buffer(new byte[1024]), ctx.asyncAssertFailure());
        }));
  }

  @Test
  public void testBackendCloseResponseWithOnGoingRequest(TestContext ctx) {
    SocketAddress backend = startNetBackend(ctx, 8081, so -> {
      Buffer body = Buffer.buffer();
      so.handler(buff -> {
        body.appendBuffer(buff);
        if (buff.toString().contains("\r\n\r\n")) {
          so.write(
              "HTTP/1.1 200 OK\r\n" +
              "content-length: 0\r\n" +
              "\r\n");
          so.close();
        }
      });
    });
    startProxy(backend);
    HttpClient client = vertx.createHttpClient();
    client.request(HttpMethod.POST, 8080, "localhost", "/")
        .onComplete(ctx.asyncAssertSuccess(req -> {
          req.putHeader("Content-Length", "2048");
          req.write(Buffer.buffer(new byte[1024]));
          req.response().onComplete(ctx.asyncAssertSuccess(resp -> {
            ctx.assertEquals(200, resp.statusCode());
          }));
        }));
  }

  @Test
  public void testBackendCloseResponse(TestContext ctx) {
    testBackendCloseResponse(ctx, false);
  }

  @Test
  public void testBackendCloseChunkedResponse(TestContext ctx) {
    testBackendCloseResponse(ctx, true);
  }

  private void testBackendCloseResponse(TestContext ctx, boolean chunked) {
    CompletableFuture<Void> closeFuture = new CompletableFuture<>();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      HttpServerResponse resp = req.response();
      if (chunked) {
        resp.setChunked(true);
      } else {
        resp.putHeader("content-length", "10000");
      }
      resp.write("part");
      closeFuture.thenAccept(v -> {
        resp.close();
      });
    });
    startProxy(backend);
    HttpClient client = vertx.createHttpClient();
    Async async = ctx.async();
    client.request(GET, 8080, "localhost", "/")
        .onComplete(ctx.asyncAssertSuccess(req -> {
          req.send(ctx.asyncAssertSuccess(resp -> {
            resp.handler(buff -> {
              closeFuture.complete(null);
            });
            resp.exceptionHandler(err -> {
              async.complete();
            });
          }));
    }));
  }

  @Test
  public void testFrontendCloseResponse(TestContext ctx) {
    testFrontendCloseResponse(ctx, false);
  }

  @Test
  public void testFrontendCloseChunkedResponse(TestContext ctx) {
    testBackendCloseResponse(ctx, true);
  }

  private void testFrontendCloseResponse(TestContext ctx, boolean chunked) {
    Async async = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      HttpServerResponse resp = req.response();
      if (chunked) {
        resp.setChunked(true);
      } else {
        resp.putHeader("content-length", "10000");
      }
      resp.write("part");
      resp.exceptionHandler(err -> {
        async.complete();
      });
    });
    startProxy(backend);
    HttpClient client = vertx.createHttpClient();
    client.request(GET, 8081, "localhost", "/", ctx.asyncAssertSuccess(req -> {
      req.send(ctx.asyncAssertSuccess(resp -> {
        resp.handler(buff -> {
          resp.request().connection().close();
          System.out.println("closing");
        });
      }));
    }));
  }

  @Test
  public void testBackendRepliesIncorrectHttpVersion(TestContext ctx) {
    SocketAddress backend = startNetBackend(ctx, 8081, so -> {
      so.write("HTTP/1.2 200 OK\r\n\r\n");
      so.close();
    });
    startProxy(backend);
    HttpClient client = vertx.createHttpClient();
    client.request(GET, 8080, "localhost", "/")
        .compose(req ->
      req.send().compose(resp -> {
        ctx.assertEquals(502, resp.statusCode());
        return resp.body();
      })).onComplete(ctx.asyncAssertSuccess(b -> {
    }));
  }

  @Test
  public void testSuppressIncorrectWarningHeaders(TestContext ctx) {
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.response()
          .putHeader("date", "Tue, 15 Nov 1994 08:12:30 GMT")
          .putHeader("warning", "199 Miscellaneous warning \"Tue, 15 Nov 1994 08:12:31 GMT\"")
        .end();
    });
    startProxy(backend);
    HttpClient client = vertx.createHttpClient();
    client.request(GET, 8080, "localhost", "/", ctx.asyncAssertSuccess(req -> {
      req.send(ctx.asyncAssertSuccess(resp -> {
        ctx.assertNotNull(resp.getHeader("date"));
        ctx.assertNull(resp.getHeader("warning"));
      }));
    }));
  }

  @Test
  public void testAddMissingHeaderDate(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.response()
//          .putHeader("date", "Tue, 15 Nov 1994 08:12:30 GMT")
//          .putHeader("warning", "199 Miscellaneous warning \"Tue, 15 Nov 1994 08:12:31 GMT\"")
          .end();
    });
    startProxy(backend);
    HttpClient client = vertx.createHttpClient();
    client.request(GET, 8080, "localhost", "/", ctx.asyncAssertSuccess(req -> {
      req.send(ctx.asyncAssertSuccess(resp -> {
        ctx.assertNotNull(resp.getHeader("date"));
        latch.complete();
      }));
    }));
  }

  @Test
  public void testAddMissingHeaderDateFromWarning(TestContext ctx) {
    String expectedDate = "Tue, 15 Nov 1994 08:12:31 GMT";
    String expectedWarning = "199 Miscellaneous warning \"" + expectedDate + "\"";
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.response()
          .putHeader("warning", expectedWarning)
          .end();
    });
    startProxy(backend);
    HttpClient client = vertx.createHttpClient();
    client.request(GET, 8080, "localhost", "/", ctx.asyncAssertSuccess(req -> {
      req.send(ctx.asyncAssertSuccess(resp -> {
        ctx.assertEquals(expectedDate, resp.getHeader("date"));
        ctx.assertEquals(expectedWarning, resp.getHeader("warning"));
        latch.complete();
      }));
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
    HttpClient client = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(version));
    StringBuilder sb = new StringBuilder();
    for (int i = 0;i < num;i++) {
      sb.append("chunk-").append(i);
    }
    client.request(GET, 8080, "localhost", "/", ctx.asyncAssertSuccess(req -> {
      req.send(ctx.asyncAssertSuccess(resp -> {
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
  public void testChunkedTransferEncodingRequest(TestContext ctx) {
    int num = 50;
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      StringBuilder sb = new StringBuilder();
      for (int i = 0;i < num;i++) {
        sb.append("chunk-").append(i);
      }
      ctx.assertEquals("chunked", req.getHeader("transfer-encoding"));
      req.handler(buff -> {
        String part = buff.toString();
        if (sb.indexOf(part) == 0) {
          sb.delete(0, part.length());
        } else {
          ctx.fail();
        }
      });
      req.endHandler(v -> {
        ctx.assertEquals("", sb.toString());
        latch.complete();
      });
      req.response().end();
    });
    startProxy(backend);
    HttpClient client = vertx.createHttpClient();
    client.request(GET, 8080, "localhost", "/")
        .onSuccess(req -> {
          req.setChunked(true);
          streamChunkedBody(req, num);
        });
  }

  @Test
  public void testIllegalClientHttpVersion(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      ctx.fail();
    });
    startProxy(backend);
    NetClient client = vertx.createNetClient();
    client.connect(8080, "localhost", ctx.asyncAssertSuccess(so -> {
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
    HttpClient client = vertx.createHttpClient();
    client.request(GET, 8080, "localhost", "" + uri, ctx.asyncAssertSuccess(req -> {
      req.send(ctx.asyncAssertSuccess(resp -> {
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
    HttpClient client = vertx.createHttpClient(/*new HttpClientOptions().setProtocolVersion(HttpVersion.HTTP_1_0)*/);
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
        client.connect(8080, "localhost", ctx.asyncAssertSuccess(so -> {
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
    HttpClient client = vertx.createHttpClient();
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
    HttpClient client = vertx.createHttpClient();
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
    HttpClient client = vertx.createHttpClient();
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
          System.out.println(body.toString());
          so.write(responseBody.get());
        }
      });
    });
    for (String response : responses) {
      responseBody.set(response);
      Async latch = ctx.async();
      HttpClient client = vertx.createHttpClient();
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
    for (int i = 0;i < len;i++) {
      uri.append((char)('A' + random.nextInt(26)));
    }
    return uri;
  }

  @Test
  public void testPropagateHeaders(TestContext ctx) {
    SocketAddress backend = startHttpBackend(ctx, new HttpServerOptions().setPort(8081).setMaxInitialLineLength(10000), req -> {
      ctx.assertEquals("request_header_value", req.getHeader("request_header"));
      ctx.assertNull(req.getHeader("proxy-authenticate"));
      req.response().putHeader("response_header", "response_header_value").end();
    });
    startProxy(backend);
    HttpClient client = vertx.createHttpClient();
    Async latch = ctx.async();
    client.request(GET, 8080, "localhost", "/", ctx.asyncAssertSuccess(req -> {
      req
        .putHeader("request_header", "request_header_value")
        .putHeader("proxy-authenticate", "proxy-authenticate_value")
        .send(ctx.asyncAssertSuccess(resp -> {
        ctx.assertEquals(200, resp.statusCode());
        ctx.assertEquals("response_header_value", resp.getHeader("response_header"));
        latch.complete();
      }));
    }));
  }

  @Test
  public void testAuthorityOverride1(TestContext ctx) {
    testAuthorityOverride(ctx, "foo:8080", "foo:8080", "localhost:8080");
  }

  @Test
  public void testAuthorityOverride2(TestContext ctx) {
    testAuthorityOverride(ctx, "foo", "foo", "localhost:8080");
  }

  @Test
  public void testAuthorityOverride3(TestContext ctx) {
    testAuthorityOverride(ctx, "localhost:8080", "localhost:8080", null);
  }

  private void testAuthorityOverride(TestContext ctx, String authority, String expectedAuthority, String expectedForwardedHost) {
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      ctx.assertEquals("/somepath", req.uri());
      ctx.assertEquals(expectedAuthority, req.host());
      ctx.assertEquals(expectedForwardedHost, req.getHeader("x-forwarded-host"));
      req.response().end("Hello World");
    });
    startProxy(proxy -> {
      proxy.origin(backend);
      proxy.addInterceptor(new ProxyInterceptor() {
        @Override
        public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
          ProxyRequest request = context.request();
          ctx.assertEquals("localhost:8080", request.getAuthority());
          request.setAuthority(authority);
          return ProxyInterceptor.super.handleProxyRequest(context);
        }
      });
    });
    HttpClient client = vertx.createHttpClient();
    client.request(GET, 8080, "localhost", "/somepath")
      .compose(req -> req
        .send()
        .compose(resp -> {
          ctx.assertEquals(200, resp.statusCode());
          return resp.body();
        }))
      .onComplete(ctx.asyncAssertSuccess(body -> {
        ctx.assertEquals("Hello World", body.toString());
      }));
  }
}
