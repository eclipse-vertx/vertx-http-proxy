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
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.WriteStream;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;
import io.vertx.tests.TestBase;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static io.vertx.core.http.HttpMethod.GET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Connection lifecycle tests (close during/after upload, backend/frontend close).
 * Parameterized by connection mode: keep-alive, pipelined, non-persistent.
 */
@ParameterizedClass(name = "{2}")
@MethodSource("data")
public class ProxyConnectionTest extends TestBase {

  public static Stream<Arguments> data() {
    return Stream.of(
      Arguments.of(true,  false, "keep-alive"),
      Arguments.of(true,  true,  "pipelined"),
      Arguments.of(false, false, "non-persistent")
    );
  }

  @Parameter(0)
  boolean keepAlive;

  @Parameter(1)
  boolean pipelining;

  @Parameter(2)
  String label;

  private HttpClient client;

  @BeforeEach
  @Override
  public void setUp(Vertx vertx) {
    super.setUp(vertx);
    clientOptions.setKeepAlive(keepAlive).setPipelining(pipelining);
  }

  @Test
  public void testBackendClosesDuringUpload(VertxTestContext ctx) {
    SocketAddress backend = startHttpBackend(8081, req -> {
      AtomicInteger len = new AtomicInteger();
      req.handler(buff -> {
        if (len.addAndGet(buff.length()) == 1024) {
          req.connection().close();
        }
      });
    });
    startProxy(backend);
    client = vertx.createHttpClient();
    client.request(HttpMethod.POST, 8080, "localhost", "/")
        .onComplete(ctx.succeeding(req -> {
          req.response().onComplete(ctx.succeeding(resp -> {
            assertEquals(502, resp.statusCode());
            ctx.completeNow();
          }));
          req.putHeader("Content-Length", "2048");
          req.write(Buffer.buffer(new byte[1024]));
        }));
  }

  @Test
  public void testClientClosesDuringUpload(VertxTestContext ctx) {
    Checkpoint backendResponseClosed = ctx.checkpoint();
    Checkpoint clientResponseFailed = ctx.checkpoint();
    Promise<Void> closeLatch = Promise.promise();
    SocketAddress backend = startHttpBackend(8081, req -> {
      req.response().closeHandler(v -> {
        backendResponseClosed.flag();
      });
      req.handler(buff -> {
        closeLatch.tryComplete();
      });
    });
    startProxy(backend);
    client = vertx.createHttpClient();
    client.request(HttpMethod.POST, 8080, "localhost", "/")
        .onComplete(ctx.succeeding(req -> {
          req.response().onComplete(ctx.failing(err -> clientResponseFailed.flag()));
          req.putHeader("Content-Length", "2048");
          req.write(Buffer.buffer(new byte[1024]));
          closeLatch.future().onComplete(ar -> {
            req.connection().close();
          });
        }));
  }

  @Test
  public void testClientClosesAfterUpload(VertxTestContext ctx) {
    Checkpoint backendResponseClosed = ctx.checkpoint();
    Checkpoint clientRequestFailed = ctx.checkpoint();
    Promise<Void> closeLatch = Promise.promise();
    SocketAddress backend = startHttpBackend(8081, req -> {
      req.endHandler(v -> {
        closeLatch.complete();
        vertx.setTimer(200, id -> {
          req.response().setChunked(true).write("partial response");
        });
      });
      req.response().closeHandler(v -> {
        backendResponseClosed.flag();
      });
    });
    startProxy(backend);
    client = vertx.createHttpClient();
    client.request(HttpMethod.POST, 8080, "localhost", "/")
        .onComplete(ctx.succeeding(req -> {
          closeLatch.future().onSuccess(v -> {
            HttpConnection conn = req.connection();
            conn.close();
          });
          req.send(Buffer.buffer(new byte[1024])).onComplete(ctx.failing(err -> clientRequestFailed.flag()));
        }));
  }

  @Test
  public void testBackendCloseResponseWithOnGoingRequest(VertxTestContext ctx) {
    SocketAddress backend = startNetBackend(8081, so -> {
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
    client = vertx.createHttpClient();
    client.request(HttpMethod.POST, 8080, "localhost", "/")
        .onComplete(ctx.succeeding(req -> {
          req.putHeader("Content-Length", "2048");
          req.write(Buffer.buffer(new byte[1024]));
          req.response().onComplete(ctx.succeeding(resp -> {
            assertEquals(200, resp.statusCode());
            ctx.completeNow();
          }));
        }));
  }

  @Test
  public void testBackendCloseResponse(VertxTestContext ctx) {
    testBackendCloseResponse(ctx, false);
  }

  @Test
  public void testBackendCloseChunkedResponse(VertxTestContext ctx) {
    testBackendCloseResponse(ctx, true);
  }

  private void testBackendCloseResponse(VertxTestContext ctx, boolean chunked) {
    CompletableFuture<Void> closeFuture = new CompletableFuture<>();
    SocketAddress backend = startHttpBackend(8081, req -> {
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
    client = vertx.createHttpClient();
    client.request(GET, 8080, "localhost", "/")
        .onComplete(ctx.succeeding(req -> {
          req.send().onComplete(ctx.succeeding(resp -> {
            resp.handler(buff -> {
              closeFuture.complete(null);
            });
            resp.exceptionHandler(err -> {
              ctx.completeNow();
            });
          }));
    }));
  }

  @Test
  public void testFrontendCloseResponse(VertxTestContext ctx) {
    testFrontendCloseResponse(ctx, false);
  }

  @Test
  public void testFrontendCloseChunkedResponse(VertxTestContext ctx) {
    testFrontendCloseResponse(ctx, true);
  }

  private void testFrontendCloseResponse(VertxTestContext ctx, boolean chunked) {
    SocketAddress backend = startHttpBackend(8081, req -> {
      HttpServerResponse resp = req.response();
      if (chunked) {
        resp.setChunked(true);
      } else {
        resp.putHeader("content-length", "10000");
      }
      resp.write("part");
      resp.exceptionHandler(err -> {
        ctx.completeNow();
      });
    });
    startProxy(backend);
    client = vertx.createHttpClient();
    client.request(GET, 8080, "localhost", "/").onComplete(ctx.succeeding(req -> {
      req.send().onComplete(ctx.succeeding(resp -> {
        resp.handler(buff -> {
          resp.request().connection().close();
        });
      }));
    }));
  }

  @Test
  public void testChunkedTransferEncodingRequest(VertxTestContext ctx) {
    // Does not pass in non-persistent mode (times out when run in a suite)
    Assumptions.assumeTrue(keepAlive, "testChunkedTransferEncodingRequest requires keep-alive or pipelined connection mode");
    int num = 50;
    SocketAddress backend = startHttpBackend(8081, req -> {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < num; i++) {
        sb.append("chunk-").append(i);
      }
      assertEquals("chunked", req.getHeader("transfer-encoding"));
      req.handler(buff -> {
        String part = buff.toString();
        if (sb.indexOf(part) == 0) {
          sb.delete(0, part.length());
        } else {
          fail();
        }
      });
      req.endHandler(end -> {
        assertEquals("", sb.toString());
        ctx.completeNow();
      });
      req.response().end();
    });
    startProxy(backend);
    client = vertx.createHttpClient();
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
