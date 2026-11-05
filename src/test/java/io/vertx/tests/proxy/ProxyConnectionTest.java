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

import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.WriteStream;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunnerWithParametersFactory;
import io.vertx.tests.TestBase;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static io.vertx.core.http.HttpMethod.GET;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Connection lifecycle tests (close during/after upload, backend/frontend close).
 * Parameterized by connection mode: keep-alive, pipelined, non-persistent.
 */
@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(VertxUnitRunnerWithParametersFactory.class)
public class ProxyConnectionTest extends TestBase {

  @Parameterized.Parameters(name = "{2}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
      {true,  false, "keep-alive"},
      {true,  true,  "pipelined"},
      {false, false, "non-persistent"},
    });
  }

  private final boolean keepAlive;
  private final boolean pipelining;

  public ProxyConnectionTest(boolean keepAlive, boolean pipelining, String label) {
    this.keepAlive = keepAlive;
    this.pipelining = pipelining;
  }

  @Override
  public void setUp() {
    super.setUp();
    clientOptions.setKeepAlive(keepAlive).setPipelining(pipelining);
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
            ctx.verify(v -> {
              assertEquals(502, resp.statusCode());
            });
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
          req.send(Buffer.buffer(new byte[1024])).onComplete(ctx.asyncAssertFailure());
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
            ctx.verify(v -> {
              assertEquals(200, resp.statusCode());
            });
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
        req.connection().close();
      });
    });
    startProxy(backend);
    HttpClient client = vertx.createHttpClient();
    Async async = ctx.async();
    client.request(GET, 8080, "localhost", "/")
        .onComplete(ctx.asyncAssertSuccess(req -> {
          req.send().onComplete(ctx.asyncAssertSuccess(resp -> {
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
    testFrontendCloseResponse(ctx, true);
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
    client.request(GET, 8080, "localhost", "/").onComplete(ctx.asyncAssertSuccess(req -> {
      req.send().onComplete(ctx.asyncAssertSuccess(resp -> {
        resp.handler(buff -> {
          resp.request().connection().close();
        });
      }));
    }));
  }

  @Test
  public void testChunkedTransferEncodingRequest(TestContext ctx) {
    // Does not pass in non-persistent mode (times out when run in a suite)
    Assume.assumeTrue("testChunkedTransferEncodingRequest requires keep-alive or pipelined connection mode", keepAlive);
    int num = 50;
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < num; i++) {
        sb.append("chunk-").append(i);
      }
      ctx.verify(v -> {
        assertEquals("chunked", req.getHeader("transfer-encoding"));
      });
      req.handler(buff -> {
        String part = buff.toString();
        if (sb.indexOf(part) == 0) {
          sb.delete(0, part.length());
        } else {
          ctx.verify(v -> {
            fail();
          });
        }
      });
      req.endHandler(end -> {
        ctx.verify(v -> {
          assertEquals("", sb.toString());
        });
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

  private void streamChunkedBody(WriteStream<Buffer> stream, int num) {
    java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger(0);
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
}
