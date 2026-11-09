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
package io.vertx.tests.proxy.request;

import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.net.SocketAddress;
import io.vertx.httpproxy.*;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;
import io.vertx.tests.TestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ProxyRequestTest extends TestBase {

  private HttpClient httpClient;
  private HttpClient backendClient;

  /**
   * Equivalent of the vertx-unit {@code ctx.asyncAssertSuccess()}: asserts the result is a success <em>and</em>
   * registers one more completion criterion for the test. Several tests here have more than one, so
   * {@link VertxTestContext#succeedingThenComplete()} cannot be used: it would end the test on the first one.
   */
  private static <T> Handler<AsyncResult<T>> succeedingCheckpoint(VertxTestContext ctx) {
    return succeedingCheckpoint(ctx, result -> {
    });
  }

  /**
   * Equivalent of the vertx-unit {@code ctx.asyncAssertSuccess(handler)} when the assertions carried by
   * {@code handler} must be awaited: {@link VertxTestContext#succeeding(Handler)} alone would let the test end
   * before the handler ran.
   */
  private static <T> Handler<AsyncResult<T>> succeedingCheckpoint(VertxTestContext ctx, Handler<T> handler) {
    Checkpoint checkpoint = ctx.checkpoint();
    return ctx.succeeding(result -> {
      handler.handle(result);
      checkpoint.flag();
    });
  }

  @Test
  public void testBackendResponse(VertxTestContext ctx) {
    runHttpTest(ctx, req -> req.response().end("Hello World"), succeedingCheckpoint(ctx));
    httpClient = vertx.createHttpClient();
    httpClient.request(HttpMethod.GET, 8080, "localhost", "/somepath")
        .compose(req -> req.send().compose(HttpClientResponse::body))
        .onComplete(succeedingCheckpoint(ctx));
  }

  @Test
  public void testChunkedBackendResponse(VertxTestContext ctx) {
    testChunkedBackendResponse(ctx, HttpVersion.HTTP_1_1);
  }

  @Test
  public void testChunkedBackendResponseToHttp1_0Client(VertxTestContext ctx) {
    testChunkedBackendResponse(ctx, HttpVersion.HTTP_1_0);
  }

  private void testChunkedBackendResponse(VertxTestContext ctx, HttpVersion version) {
    runHttpTest(ctx, req -> req.response().setChunked(true).end("Hello World"), succeedingCheckpoint(ctx));
    httpClient = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(version));
    Future<Buffer> res = httpClient.request(HttpMethod.GET, 8080, "localhost", "/somepath")
      .compose(req -> req.send().compose(HttpClientResponse::body));
    if (version == HttpVersion.HTTP_1_1) {
      res.onComplete(succeedingCheckpoint(ctx));
    } else {
      res.onComplete(succeedingCheckpoint(ctx));
    }
  }

  @Test
  public void testChunkedFrontendRequest(VertxTestContext ctx) {
    runHttpTest(ctx, req -> {
      String te = req.getHeader(HttpHeaders.TRANSFER_ENCODING);
      if (te != null && te.equalsIgnoreCase("chunked")) {
        fail("got chunked request");
      }
      req.response().end("Hello World");
    }, succeedingCheckpoint(ctx));
    httpClient = vertx.createHttpClient();
    httpClient.request(HttpMethod.GET, 8080, "localhost", "/somepath")
      .compose(req -> req
        .send()
        .compose(HttpClientResponse::body))
      .onComplete(succeedingCheckpoint(ctx));
  }

  @Test
  public void testNonChunkedFrontendRequest(VertxTestContext ctx) {
    runHttpTest(ctx, req -> {
      String te = req.getHeader(HttpHeaders.TRANSFER_ENCODING);
      if (te == null || !te.equalsIgnoreCase("chunked")) {
        fail("got non chunked request");
      }
      req.response().end("Hello World");
    }, succeedingCheckpoint(ctx));
    httpClient = vertx.createHttpClient();
    httpClient
        .request(HttpMethod.POST, 8080, "localhost", "/somepath")
        .compose(req -> req
          .setChunked(true)
          .send("chunk")
          .andThen(ctx.succeeding(resp -> assertEquals(200, resp.statusCode())))
          .compose(HttpClientResponse::end))
        .onComplete(succeedingCheckpoint(ctx));
  }

  @Test
  public void testCloseBackendResponse(VertxTestContext ctx) {
    testCloseBackendResponse(ctx, false);
  }

  @Test
  public void testCloseChunkedBackendResponse(VertxTestContext ctx) {
    testCloseBackendResponse(ctx, true);
  }

  private void testCloseBackendResponse(VertxTestContext ctx, boolean chunked) {
    Promise<Void> cont = Promise.promise();
    SocketAddress backend = startHttpBackend(8081, req -> {
      HttpServerResponse resp = req.response();
      if (chunked) {
        resp.setChunked(true);
      } else {
        resp.putHeader("content-length", "10000");
      }
      resp.write("part");
      cont.future().onSuccess(v -> {
        req.connection().close();
      });
    });
    backendClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    Checkpoint async = ctx.checkpoint();
    startHttpServer(serverOptions, req -> {
      ProxyRequest proxyReq = ProxyRequest.reverseProxy(req);
      backendClient.request(new RequestOptions().setServer(backend)).onComplete(ctx.succeeding(clientReq -> {
        proxyReq.proxy(clientReq).onComplete(ctx.failing(err -> async.flag()));
      }));
    });
    httpClient = vertx.createHttpClient();
    httpClient.request(HttpMethod.GET, 8080, "localhost", "/somepath").onComplete(ctx.succeeding(req ->
      req.send().onComplete(ctx.succeeding(resp -> {
        resp.handler(buff -> {
          cont.tryComplete();
        });
      }))
    ));
  }

  @Test
  public void testCloseFrontendResponse(VertxTestContext ctx) {
    testCloseFrontendResponse(ctx, false);
  }

  @Test
  public void testCloseChunkedFrontendResponse(VertxTestContext ctx) {
    testCloseFrontendResponse(ctx, true);
  }

  private void testCloseFrontendResponse(VertxTestContext ctx, boolean chunked) {
    SocketAddress backend = startHttpBackend(8081, req -> {
      HttpServerResponse resp = req.response();
      if (chunked) {
        resp.setChunked(true);
      } else {
        resp.putHeader("content-length", "10000");
      }
      long id = vertx.setPeriodic(1, id_ -> {
        resp.write("part");
      });
      resp.closeHandler(v -> {
        vertx.cancelTimer(id);
      });
    });
    backendClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    Checkpoint async = ctx.checkpoint();
    startHttpServer(serverOptions, req -> {
      ProxyRequest proxyReq = ProxyRequest.reverseProxy(req);
      backendClient.request(new RequestOptions().setServer(backend)).onComplete(ctx.succeeding(clientReq -> {
        proxyReq.proxy(clientReq).onComplete(ctx.failing(err -> async.flag()));
      }));
    });
    httpClient = vertx.createHttpClient();
    httpClient.request(HttpMethod.GET, 8080, "localhost", "/somepath").onComplete(ctx.succeeding(req ->
        req.send().onComplete(ctx.succeeding(resp -> {
          resp.handler(buff -> {
            resp.request().connection().close();
          });
        }))
    ));
  }

  @Test
  public void testCloseFrontendRequest(VertxTestContext ctx) throws Exception {
    testCloseChunkedFrontendRequest(ctx, false);
  }

  @Test
  public void testCloseChunkedFrontendRequest(VertxTestContext ctx) throws Exception {
    testCloseChunkedFrontendRequest(ctx, true);
  }

  private void testCloseChunkedFrontendRequest(VertxTestContext ctx, boolean chunked) throws Exception {
    Promise<Void> latch = Promise.promise();
    SocketAddress backend = startHttpBackend(8081, req -> {
      req.handler(buff -> {
        assertEquals("part", buff.toString());
        latch.tryComplete();
      });
    });
    backendClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    Checkpoint async = ctx.checkpoint();
    startHttpServer(serverOptions, req -> {
      ProxyRequest proxyReq = ProxyRequest.reverseProxy(req);
      backendClient.request(new RequestOptions().setServer(backend)).onComplete(ctx.succeeding(clientReq -> {
        proxyReq.proxy(clientReq).onComplete(ctx.failing(err -> async.flag()));
      }));
    });
    httpClient = vertx.createHttpClient();
    httpClient.request(HttpMethod.GET, 8080, "localhost", "/somepath").onComplete(ctx.succeeding(req -> {
      if (chunked) {
        req.setChunked(true);
      } else {
        req.putHeader("content-length", "10000");
      }
      req.write("part");
      latch.future().onSuccess(v -> {
        req.connection().close();
      });
    }));
  }

  @Test
  public void testCloseBackendRequest(VertxTestContext ctx) throws Exception {
    testCloseBackendRequest(ctx, false);
  }

  @Test
  public void testCloseChunkedBackendRequest(VertxTestContext ctx) throws Exception {
    testCloseBackendRequest(ctx, true);
  }

  private void testCloseBackendRequest(VertxTestContext ctx, boolean chunked) throws Exception {
    SocketAddress backend = startHttpBackend(8081, req -> {
      req.handler(buff -> {
        assertEquals("part", buff.toString());
        req.connection().close();
      });
    });
    backendClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    Checkpoint async = ctx.checkpoint();
    startHttpServer(serverOptions, req -> {
      req.pause();
      ProxyRequest proxyReq = ProxyRequest.reverseProxy(req);
      backendClient.request(new RequestOptions().setServer(backend)).onComplete(ctx.succeeding(backReq -> {
        proxyReq.send(backReq).onComplete(ctx.failing(err -> {
          async.flag();
          req.response().setStatusCode(502).end();
        }));
      }));
    });
    httpClient = vertx.createHttpClient();
    httpClient.request(HttpMethod.GET, 8080, "localhost", "/somepath").onComplete(ctx.succeeding(req -> {
      req.response().onComplete(succeedingCheckpoint(ctx, resp -> {
        assertEquals(502, resp.statusCode());
      }));
      if (chunked) {
        req.setChunked(true);
      } else {
        req.putHeader("content-length", "10000");
      }
      req.write("part");
    }));
  }

  @Test
  public void testLatency(VertxTestContext ctx) throws Exception {
    httpClient = vertx.createHttpClient();
    SocketAddress backend = startHttpBackend(8081, req -> {
      HttpServerResponse resp = req.response();
      req.bodyHandler(resp::end);
    });
    backendClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    Checkpoint async = ctx.checkpoint();
    startHttpServer(serverOptions, req -> {
      req.pause();
      vertx.setTimer(500, id1 -> {
        ProxyRequest proxyReq = ProxyRequest.reverseProxy(req);
        backendClient.request(new RequestOptions().setServer(backend)).onComplete(ctx.succeeding(backReq -> {
          proxyReq.send(backReq).onComplete(ctx.succeeding(resp -> {
            vertx.setTimer(500, id2 -> {
              resp.send().onComplete(ctx.succeeding(v -> async.flag()));
            });
          }));
        }));
      });
    });
    Buffer sent = Buffer.buffer("Hello world");
    httpClient.request(HttpMethod.POST, 8080, "localhost", "/somepath")
        .compose(req -> req.send(sent).compose(HttpClientResponse::body))
        .onComplete(succeedingCheckpoint(ctx, received -> {
          assertEquals(sent, received);
        }));
  }

  @Test
  public void testUpdateRequestHeaders(VertxTestContext ctx) throws Exception {
    SocketAddress backend = startHttpBackend(8081, req -> {
      assertNotEquals("example.org", req.getHeader(HttpHeaders.HOST));
      assertNull(req.getHeader("header"));
      assertEquals("proxy_header_value", req.getHeader("proxy_header"));
      req.response().putHeader("header", "header_value").end();
    });
    backendClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    startHttpServer(serverOptions, req -> {
      ProxyRequest proxyReq = ProxyRequest.reverseProxy(req);
      MultiMap clientHeaders = proxyReq.headers();
      clientHeaders.add("proxy_header", "proxy_header_value");
      assertEquals("header_value", clientHeaders.get("header"));
      clientHeaders.remove("header");
      backendClient.request(new RequestOptions().setServer(backend)).onComplete(ctx.succeeding(clientReq -> {
        proxyReq.send(clientReq).onComplete(ctx.succeeding(proxyResp -> {
          MultiMap targetHeaders = proxyResp.headers();
          targetHeaders.add("proxy_header", "proxy_header_value");
          assertEquals("header_value", targetHeaders.get("header"));
          targetHeaders.remove("header");
          proxyResp.send().onComplete(succeedingCheckpoint(ctx));
        }));
      }));
    });
    httpClient = vertx.createHttpClient();
    httpClient.request(HttpMethod.GET, 8080, "localhost", "/somepath")
        .compose(req ->
      req
          .putHeader("header", "header_value")
          .putHeader(HttpHeaders.HOST, "example.org")
          .send()
          .compose(resp -> {
            assertEquals("proxy_header_value", resp.getHeader("proxy_header"));
            assertNull(resp.getHeader("header"));
            return resp.body();
          })
    ).onComplete(succeedingCheckpoint(ctx));
  }

  @Test
  public void testReleaseProxyResponse(VertxTestContext ctx) {
    Checkpoint drainedLatch = ctx.checkpoint();
    Promise<Void> full = Promise.promise();
    Buffer chunk = Buffer.buffer(new byte[1024]);
    SocketAddress backend = startHttpBackend(8081, req -> {
      HttpServerResponse resp = req.response();
      resp
        .setChunked(true)
        .putHeader("header", "header-value");
      vertx.setPeriodic(1, id -> {
        if (resp.writeQueueFull()) {
          vertx.cancelTimer(id);
          resp.drainHandler(v -> {
            drainedLatch.flag();
          });
          full.complete();
        } else {
          resp.write(chunk);
        }
      });
    });
    backendClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    startHttpServer(serverOptions, req -> {
      ProxyRequest proxyReq = ProxyRequest.reverseProxy(req);
      backendClient.request(new RequestOptions().setServer(backend)).onComplete(ctx.succeeding(clientReq -> {
        proxyReq.send(clientReq).onComplete(ctx.succeeding(proxyResp -> {
          full.future().onSuccess(v -> {
            proxyResp.release();
            req.response().end("another-response");
          });
        }));
      }));
    });
    httpClient = vertx.createHttpClient();
    httpClient.request(HttpMethod.GET, 8080, "localhost", "/somepath")
      .compose(req -> req.send().compose(resp -> {
        assertNull(resp.getHeader("header"));
        return resp.body();
      }))
      .onComplete(succeedingCheckpoint(ctx, body -> {
        assertEquals("another-response", body.toString());
      }));
  }

  @Test
  public void testReleaseProxyRequest(VertxTestContext ctx) {
    Promise<Void> full = Promise.promise();
    SocketAddress backend = startHttpBackend(8081, req -> {
      assertEquals(null, req.getHeader("header"));
      req.body().onComplete(ctx.succeeding(body -> {
        req.response().end(body);
      }));
    });
    backendClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    startHttpServer(serverOptions, req -> {
      ProxyRequest proxyReq = ProxyRequest.reverseProxy(req);
      full.future().onSuccess(v -> {
        proxyReq.release();
        proxyReq.setBody(Body.body(Buffer.buffer("another-request")));
        backendClient.request(new RequestOptions().setServer(backend))
          .onComplete(ctx.succeeding(clientReq -> {
          proxyReq.send(clientReq).onComplete(ctx.succeeding(proxyResp -> {
            proxyResp.send().onComplete(succeedingCheckpoint(ctx));
          }));
        }));
      });
    });
    httpClient = vertx.createHttpClient();
    Buffer chunk = Buffer.buffer(new byte[1024]);
    Buffer body = httpClient
      .request(HttpMethod.GET, 8080, "localhost", "/somepath")
      .compose(req -> {
        req.setChunked(true);
        req.putHeader("header", "header-value");
        vertx.setPeriodic(1, id -> {
          if (req.writeQueueFull()) {
            vertx.cancelTimer(id);
            req.end().onComplete(succeedingCheckpoint(ctx));
            full.complete();
          } else {
            req.write(chunk);
          }
        });
        return req.response()
          .expecting(HttpResponseExpectation.SC_OK)
          .compose(HttpClientResponse::body);
      }).await();
    assertEquals("another-request", body.toString());
  }

  @Test
  public void testReleaseProxyRequestAndFail(VertxTestContext ctx) {
    SocketAddress backend = startHttpBackend(8081, req -> {
      fail("Shouldn't be called");
    });
    backendClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    HttpProxy proxy = HttpProxy.reverseProxy(backendClient);
    proxy.origin(backend);
    proxy.addInterceptor(new ProxyInterceptor() {
      @Override
      public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
        context.request().release();
        return Future.failedFuture("boom");
      }
    });
    startHttpServer(serverOptions, proxy);
    httpClient = vertx.createHttpClient();
    httpClient.request(HttpMethod.GET, 8080, "localhost", "/somepath").onComplete(ctx.succeeding(req -> {
      req.send().onComplete(ctx.succeeding(resp -> {
        assertEquals(502, resp.statusCode());
        ctx.completeNow();
      }));
    }));
  }

  @Test
  public void testSendDefaultProxyResponse(VertxTestContext ctx) {
    startHttpServer(serverOptions, req -> {
      ProxyRequest proxyReq = ProxyRequest.reverseProxy(req);
      proxyReq.response().send();
    });
    httpClient = vertx.createHttpClient();
    Checkpoint async = ctx.checkpoint();
    httpClient.request(HttpMethod.GET, 8080, "localhost", "/somepath")
      .compose(req -> req.send().compose(resp -> {
        assertEquals(200, resp.statusCode());
        assertNull(resp.getHeader(HttpHeaders.TRANSFER_ENCODING));
        return resp.body();
      }))
      .onComplete(ctx.succeeding(body -> {
        assertEquals("", body.toString());
        async.flag();
      }));
  }

  @Test
  public void testSendProxyResponse(VertxTestContext ctx) {
    startHttpServer(serverOptions, req -> {
      ProxyRequest proxyReq = ProxyRequest.reverseProxy(req);
      proxyReq.response()
        .setStatusCode(302)
        .setStatusMessage("some-status-message")
        .putHeader("some-header", "some-header-value")
        .setBody(Body.body(Buffer.buffer("hello world")))
        .send();
    });
    httpClient = vertx.createHttpClient();
    Checkpoint async = ctx.checkpoint();
    httpClient.request(HttpMethod.GET, 8080, "localhost", "/somepath")
      .compose(req -> req.send().compose(resp -> {
        assertEquals(302, resp.statusCode());
        assertEquals("some-status-message", resp.statusMessage());
        assertEquals("some-header-value", resp.getHeader("some-header"));
        assertNull(resp.getHeader(HttpHeaders.TRANSFER_ENCODING));
        return resp.body();
      }))
      .onComplete(ctx.succeeding(body -> {
        assertEquals("hello world", body.toString());
        async.flag();
      }));
  }

  private void runHttpTest(VertxTestContext ctx,
                           Handler<HttpServerRequest> backendHandler,
                           Handler<AsyncResult<Void>> expect) {
    Checkpoint async = ctx.checkpoint();
    SocketAddress backend = startHttpBackend(8081, backendHandler);
    httpClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    startHttpServer(serverOptions, req -> {
      req.pause(); // Should it be necessary ?
      ProxyRequest proxyRequest = ProxyRequest.reverseProxy(req);
      httpClient.request(new RequestOptions().setServer(backend)).onComplete(ar -> {
        if (ar.succeeded()) {
          proxyRequest.send(ar.result()).onComplete(ar2 -> {
            if (ar2.succeeded()) {
              ProxyResponse proxyResponse = ar2.result();
              proxyResponse.send().onComplete(ar3 -> {
                expect.handle(ar3);
                async.flag();
              });
            } else {
              req.resume().response().setStatusCode(502).end();
            }
          });
        } else {
          req.resume().response().setStatusCode(404).end();
        }
      });
    });
  }

  @Test
  public void testInvalidContentType(VertxTestContext ctx) {
    SocketAddress backend = startHttpBackend(8081, req -> {
      req
        .response()
        .putHeader(HttpHeaders.CONTENT_TYPE, "invalid response content type").end();
    });
    backendClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    startHttpServer(serverOptions, req -> {
      ProxyRequest proxyReq = ProxyRequest.reverseProxy(req);
      assertEquals("invalid request content type", proxyReq.headers().get(HttpHeaders.CONTENT_TYPE));
      assertEquals("invalid request content type", proxyReq.getBody().mediaType());
      backendClient.request(new RequestOptions().setServer(backend)).onComplete(ctx.succeeding(clientReq -> {
        proxyReq
          .send(clientReq)
          .onComplete(ctx.succeeding(proxyResp -> {
            assertEquals("invalid response content type", proxyResp.headers().get(HttpHeaders.CONTENT_TYPE));
            assertEquals("invalid response content type", proxyResp.getBody().mediaType());
            proxyResp.send();
        }));
      }));
    });
    httpClient = vertx.createHttpClient();
    String header = httpClient
      .request(HttpMethod.GET, 8080, "localhost", "/somepath")
      .compose(req -> req
        .putHeader(HttpHeaders.CONTENT_TYPE, "invalid request content type")
        .send()
        .map(resp -> resp.getHeader(HttpHeaders.CONTENT_TYPE))
      )
      .await();
    assertEquals("invalid response content type", header);
    ctx.completeNow();
  }
}
