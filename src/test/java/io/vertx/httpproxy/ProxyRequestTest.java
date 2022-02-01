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

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.ReadStream;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ProxyRequestTest extends ProxyTestBase {

  public ProxyRequestTest(ProxyOptions options) {
    super(options);
  }

  @Ignore
  @Test
  public void testProxyRequestIllegalHttpVersion(TestContext ctx) {
    runHttpTest(ctx, req -> req.response().end("Hello World"), ctx.asyncAssertFailure());
    NetClient client = vertx.createNetClient();
    client.connect(8080, "localhost", ctx.asyncAssertSuccess(so -> {
      so.write("GET /somepath http/1.1\r\n\r\n");
    }));
  }

  @Test
  public void testBackendResponse(TestContext ctx) {
    runHttpTest(ctx, req -> req.response().end("Hello World"), ctx.asyncAssertSuccess());
    HttpClient httpClient = vertx.createHttpClient();
    httpClient.request(HttpMethod.GET, 8080, "localhost", "/somepath")
        .compose(req -> req.send().compose(HttpClientResponse::body))
        .onComplete(ctx.asyncAssertSuccess());
  }

  @Test
  public void testChunkedBackendResponse(TestContext ctx) {
    testChunkedBackendResponse(ctx, HttpVersion.HTTP_1_1);
  }

  @Test
  public void testChunkedBackendResponseToHttp1_0Client(TestContext ctx) {
    testChunkedBackendResponse(ctx, HttpVersion.HTTP_1_0);
  }

  private void testChunkedBackendResponse(TestContext ctx, HttpVersion version) {
    runHttpTest(ctx, req -> req.response().setChunked(true).end("Hello World"), ctx.asyncAssertSuccess());
    HttpClient httpClient = vertx.createHttpClient(new HttpClientOptions().setProtocolVersion(version));
    Future<Buffer> res = httpClient.request(HttpMethod.GET, 8080, "localhost", "/somepath")
      .compose(req -> req.send().compose(HttpClientResponse::body));
    if (version == HttpVersion.HTTP_1_1) {
      res.onComplete(ctx.asyncAssertSuccess());
    } else {
      res.onComplete(ctx.asyncAssertSuccess());
    }
  }

  @Test
  public void testChunkedFrontendRequest(TestContext ctx) {
    runHttpTest(ctx, req -> {
      String te = req.getHeader(HttpHeaders.TRANSFER_ENCODING);
      if (te != null && te.equalsIgnoreCase("chunked")) {
        ctx.fail("got chunked request");
      }
      req.response().end("Hello World");
    }, ctx.asyncAssertSuccess());
    HttpClient httpClient = vertx.createHttpClient();
    httpClient
        .request(HttpMethod.GET, 8080, "localhost", "/somepath")
        .compose(HttpClientRequest::send)
        .compose(HttpClientResponse::body)
        .onComplete(ctx.asyncAssertSuccess());
  }

  @Test
  public void testNonChunkedFrontendRequest(TestContext ctx) {
    runHttpTest(ctx, req -> {
      String te = req.getHeader(HttpHeaders.TRANSFER_ENCODING);
      if (te == null || !te.equalsIgnoreCase("chunked")) {
        ctx.fail("got non chunked request");
      }
      req.response().end("Hello World");
    }, ctx.asyncAssertSuccess());
    HttpClient httpClient = vertx.createHttpClient();
    httpClient
        .request(HttpMethod.POST, 8080, "localhost", "/somepath")
        .compose(req -> req.setChunked(true).send("chunk"))
        .compose(HttpClientResponse::body)
        .onComplete(ctx.asyncAssertSuccess());
  }

  @Ignore
  @Test
  public void testIllegalTransferEncodingBackendResponse(TestContext ctx) {
    runNetTest(ctx, req -> req.write("" +
        "HTTP/1.1 200 OK\r\n" +
        "transfer-encoding: identity\r\n" +
        "connection: close\r\n" +
        "\r\n"), ctx.asyncAssertSuccess());
    HttpClient httpClient = vertx.createHttpClient();
    httpClient.request(HttpMethod.GET, 8080, "localhost", "/somepath")
        .compose(req -> req.send().compose(HttpClientResponse::body))
        .onComplete(ctx.asyncAssertSuccess());
  }

  @Test
  public void testCloseBackendResponse(TestContext ctx) {
    testCloseBackendResponse(ctx, false);
  }

  @Test
  public void testCloseChunkedBackendResponse(TestContext ctx) {
    testCloseBackendResponse(ctx, true);
  }

  private void testCloseBackendResponse(TestContext ctx, boolean chunked) {
    CompletableFuture<Void> cont = new CompletableFuture<>();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      HttpServerResponse resp = req.response();
      if (chunked) {
        resp.setChunked(true);
      } else {
        resp.putHeader("content-length", "10000");
      }
      resp.write("part");
      cont.thenAccept(v -> {
        resp.close();
      });
    });
    HttpClient backendClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    Async async = ctx.async();
    startHttpServer(ctx, serverOptions, req -> {
      ProxyRequest proxyReq = ProxyRequest.reverseProxy(req);
      backendClient.request(new RequestOptions().setServer(backend), ctx.asyncAssertSuccess(clientReq -> {
        proxyReq.proxy(clientReq).onComplete(ctx.asyncAssertFailure(err -> async.complete()));
      }));
    });
    HttpClient httpClient = vertx.createHttpClient();
    httpClient.request(HttpMethod.GET, 8080, "localhost", "/somepath", ctx.asyncAssertSuccess(req ->
      req.send(ctx.asyncAssertSuccess(resp -> {
        resp.handler(buff -> {
          cont.complete(null);
        });
      }))
    ));
  }

  @Test
  public void testCloseFrontendResponse(TestContext ctx) {
    testCloseFrontendResponse(ctx, false);
  }

  @Test
  public void testCloseChunkedFrontendResponse(TestContext ctx) {
    testCloseFrontendResponse(ctx, true);
  }

  private void testCloseFrontendResponse(TestContext ctx, boolean chunked) {
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
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
    HttpClient backendClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    Async async = ctx.async();
    startHttpServer(ctx, serverOptions, req -> {
      ProxyRequest proxyReq = ProxyRequest.reverseProxy(req);
      backendClient.request(new RequestOptions().setServer(backend), ctx.asyncAssertSuccess(clientReq -> {
        proxyReq.proxy(clientReq).onComplete(ctx.asyncAssertFailure(err -> async.complete()));
      }));
    });
    HttpClient httpClient = vertx.createHttpClient();
    httpClient.request(HttpMethod.GET, 8080, "localhost", "/somepath", ctx.asyncAssertSuccess(req ->
        req.send(ctx.asyncAssertSuccess(resp -> {
          resp.handler(buff -> {
            resp.request().connection().close();
          });
        }))
    ));
  }

  @Test
  public void testCloseFrontendRequest(TestContext ctx) throws Exception {
    testCloseChunkedFrontendRequest(ctx, false);
  }

  @Test
  public void testCloseChunkedFrontendRequest(TestContext ctx) throws Exception {
    testCloseChunkedFrontendRequest(ctx, true);
  }

  private void testCloseChunkedFrontendRequest(TestContext ctx, boolean chunked) throws Exception {
    Promise<Void> latch = Promise.promise();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.handler(buff -> {
        ctx.assertEquals("part", buff.toString());
        latch.tryComplete();
      });
    });
    HttpClient backendClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    Async async = ctx.async();
    startHttpServer(ctx, serverOptions, req -> {
      ProxyRequest proxyReq = ProxyRequest.reverseProxy(req);
      backendClient.request(new RequestOptions().setServer(backend), ctx.asyncAssertSuccess(clientReq -> {
        proxyReq.proxy(clientReq).onComplete(ctx.asyncAssertFailure(err -> async.complete()));
      }));
    });
    HttpClient httpClient = vertx.createHttpClient();
    httpClient.request(HttpMethod.GET, 8080, "localhost", "/somepath", ctx.asyncAssertSuccess(req -> {
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
  public void testCloseBackendRequest(TestContext ctx) throws Exception {
    testCloseBackendRequest(ctx, false);
  }

  @Test
  public void testCloseChunkedBackendRequest(TestContext ctx) throws Exception {
    testCloseBackendRequest(ctx, true);
  }

  private void testCloseBackendRequest(TestContext ctx, boolean chunked) throws Exception {
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.handler(buff -> {
        ctx.assertEquals("part", buff.toString());
        req.connection().close();
      });
    });
    HttpClient backendClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    Async async = ctx.async();
    startHttpServer(ctx, serverOptions, req -> {
      req.pause();
      ProxyRequest proxyReq = ProxyRequest.reverseProxy(req);
      backendClient.request(new RequestOptions().setServer(backend), ctx.asyncAssertSuccess(backReq -> {
        proxyReq.send(backReq).onComplete(ctx.asyncAssertFailure(err -> {
          async.complete();
          req.response().setStatusCode(502).end();
        }));
      }));
    });
    HttpClient httpClient = vertx.createHttpClient();
    httpClient.request(HttpMethod.GET, 8080, "localhost", "/somepath", ctx.asyncAssertSuccess(req -> {
      req.response().onComplete(ctx.asyncAssertSuccess(resp -> {
        ctx.assertEquals(502, resp.statusCode());
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
  public void testLatency(TestContext ctx) throws Exception {
    HttpClient client = vertx.createHttpClient();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      HttpServerResponse resp = req.response();
      req.bodyHandler(resp::end);
    });
    HttpClient backendClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    Async async = ctx.async();
    startHttpServer(ctx, serverOptions, req -> {
      req.pause();
      vertx.setTimer(500, id1 -> {
        ProxyRequest proxyReq = ProxyRequest.reverseProxy(req);
        backendClient.request(new RequestOptions().setServer(backend), ctx.asyncAssertSuccess(backReq -> {
          proxyReq.send(backReq).onComplete(ctx.asyncAssertSuccess(resp -> {
            vertx.setTimer(500, id2 -> {
              resp.send().onComplete(ctx.asyncAssertSuccess(v -> async.complete()));
            });
          }));
        }));
      });
    });
    Buffer sent = Buffer.buffer("Hello world");
    client.request(HttpMethod.POST, 8080, "localhost", "/somepath")
        .compose(req -> req.send(sent).compose(HttpClientResponse::body))
        .onComplete(ctx.asyncAssertSuccess(received -> {
          ctx.assertEquals(sent, received);
        }));
  }

  @Test
  public void testRequestFilter(TestContext ctx) {
    Filter filter = new Filter();
    CompletableFuture<Integer> onResume = new CompletableFuture<>();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.pause();
      onResume.thenAccept(num -> {
        req.bodyHandler(body -> {
          ctx.assertEquals(filter.expected, body);
          req.response().end();
        });
        req.resume();
      });
    });
    Async async = ctx.async();
    HttpClient backendClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    HttpClient client = vertx.createHttpClient();
    try {
      HttpProxy proxy = HttpProxy.reverseProxy(backendClient);
      proxy.origin(backend);
      proxy.addInterceptor(new ProxyInterceptor() {
        @Override
        public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
          ProxyRequest proxyRequest = context.request();
          Body body = proxyRequest.getBody();
          proxyRequest.setBody(Body.body(filter.init(body.stream()), body.length()));
          return context.sendRequest();
        }
      });
      startHttpServer(ctx, serverOptions, proxy);
      client.request(HttpMethod.POST, 8080, "localhost", "/somepath", ctx.asyncAssertSuccess(req -> {
        req.setChunked(true);
        AtomicInteger num = new AtomicInteger();
        vertx.setPeriodic(1, id -> {
          if (filter.paused.get()) {
            vertx.cancelTimer(id);
            req.end();
            onResume.complete(num.get());
          } else {
            num.incrementAndGet();
            req.write(CHUNK);
          }
        });
        req.response()
          .flatMap(HttpClientResponse::body)
          .onComplete(ctx.asyncAssertSuccess(v -> {
            async.complete();
          }));
      }));
      async.await();
    } finally {
      client.close();
      backendClient.close();
    }
  }

  @Test
  public void testResponseFilter(TestContext ctx) {
    Filter filter = new Filter();
    CompletableFuture<Integer> onResume = new CompletableFuture<>();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      HttpServerResponse resp = req.response().setChunked(true);
      AtomicInteger num = new AtomicInteger();
      vertx.setPeriodic(1, id -> {
        if (!filter.paused.get()) {
          resp.write(CHUNK);
          num.getAndIncrement();
        } else {
          vertx.cancelTimer(id);
          resp.end();
          onResume.complete(num.get());
        }
      });
    });
    HttpClient backendClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    HttpProxy proxy = HttpProxy.reverseProxy(backendClient);
    proxy.origin(backend);
    proxy.addInterceptor(new ProxyInterceptor() {
      @Override
      public Future<Void> handleProxyResponse(ProxyContext context) {
        ProxyResponse proxyResponse = context.response();
        Body body = proxyResponse.getBody();
        proxyResponse.setBody(Body.body(filter.init(body.stream()), body.length()));
        return context.sendResponse();
      }
    });
    startHttpServer(ctx, serverOptions, proxy);
    Async async = ctx.async();
    HttpClient client = vertx.createHttpClient();
    client.request(HttpMethod.GET, 8080, "localhost", "/somepath", ctx.asyncAssertSuccess(req -> {
      req.send().onComplete(ctx.asyncAssertSuccess(resp -> {
        resp.pause();
        onResume.thenAccept(num -> {
          resp.resume();
        });
        resp.endHandler(v -> {
          async.complete();
        });
      }));
    }));
  }

  @Test
  public void testUpdateRequestHeaders(TestContext ctx) throws Exception {
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      ctx.assertNull(req.getHeader("header"));
      ctx.assertEquals("proxy_header_value", req.getHeader("proxy_header"));
      req.response().putHeader("header", "header_value").end();
    });
    HttpClient backendClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    startHttpServer(ctx, serverOptions, req -> {
      ProxyRequest proxyReq = ProxyRequest.reverseProxy(req);
      MultiMap clientHeaders = proxyReq.headers();
      clientHeaders.add("proxy_header", "proxy_header_value");
      ctx.assertEquals("header_value", clientHeaders.get("header"));
      clientHeaders.remove("header");
      backendClient.request(new RequestOptions().setServer(backend), ctx.asyncAssertSuccess(clientReq -> {
        proxyReq.send(clientReq).onComplete(ctx.asyncAssertSuccess(proxyResp -> {
          MultiMap targetHeaders = proxyResp.headers();
          targetHeaders.add("proxy_header", "proxy_header_value");
          ctx.assertEquals("header_value", targetHeaders.get("header"));
          targetHeaders.remove("header");
          proxyResp.send().onComplete(ctx.asyncAssertSuccess());
        }));
      }));
    });
    HttpClient client = vertx.createHttpClient();
    client.request(HttpMethod.GET, 8080, "localhost", "/somepath")
        .compose(req ->
      req
          .putHeader("header", "header_value")
          .send()
          .compose(resp -> {
            ctx.assertEquals("proxy_header_value", resp.getHeader("proxy_header"));
            ctx.assertNull(resp.getHeader("header"));
            return resp.body();
          })
    ).onComplete(ctx.asyncAssertSuccess());
  }

  @Test
  public void testReleaseProxyResponse(TestContext ctx) {
    Async drainedLatch = ctx.async();
    CompletableFuture<Void> full = new CompletableFuture<>();
    Buffer chunk = Buffer.buffer(new byte[1024]);
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      HttpServerResponse resp = req.response();
      resp
        .setChunked(true)
        .putHeader("header", "header-value");
      vertx.setPeriodic(1, id -> {
        if (resp.writeQueueFull()) {
          vertx.cancelTimer(id);
          resp.drainHandler(v -> {
            drainedLatch.complete();
          });
          full.complete(null);
        } else {
          resp.write(chunk);
        }
      });
    });
    HttpClient backendClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    startHttpServer(ctx, serverOptions, req -> {
      ProxyRequest proxyReq = ProxyRequest.reverseProxy(req);
      backendClient.request(new RequestOptions().setServer(backend), ctx.asyncAssertSuccess(clientReq -> {
        proxyReq.send(clientReq).onComplete(ctx.asyncAssertSuccess(proxyResp -> {
          full.whenComplete((v, err) -> {
            proxyResp.release();
            req.response().end("another-response");
          });
        }));
      }));
    });
    HttpClient client = vertx.createHttpClient();
    client.request(HttpMethod.GET, 8080, "localhost", "/somepath")
      .compose(req -> req.send().compose(resp -> {
        ctx.assertNull(resp.getHeader("header"));
        return resp.body();
      }))
      .onComplete(ctx.asyncAssertSuccess(body -> {
        ctx.assertEquals("another-response", body.toString());
      }));
  }

  @Test
  public void testReleaseProxyRequest(TestContext ctx) {
    CompletableFuture<Void> full = new CompletableFuture<>();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      ctx.assertEquals(null, req.getHeader("header"));
      req.body(ctx.asyncAssertSuccess(body -> {
        req.response().end(body);
      }));
    });
    HttpClient backendClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    startHttpServer(ctx, serverOptions, req -> {
      ProxyRequest proxyReq = ProxyRequest.reverseProxy(req);
      full.whenComplete((v, err) -> {
        proxyReq.release();
        proxyReq.setBody(Body.body(Buffer.buffer("another-request")));
        backendClient.request(new RequestOptions().setServer(backend), ctx.asyncAssertSuccess(clientReq -> {
          proxyReq.send(clientReq).onComplete(ctx.asyncAssertSuccess(proxyResp -> {
            proxyResp.send().onComplete(ctx.asyncAssertSuccess());
          }));
        }));
      });
    });
    HttpClient client = vertx.createHttpClient();
    Async drainedLatch = ctx.async();
    Buffer chunk = Buffer.buffer(new byte[1024]);
    client.request(HttpMethod.GET, 8080, "localhost", "/somepath", ctx.asyncAssertSuccess(req -> {
      req.setChunked(true);
      req.putHeader("header", "header-value");
      vertx.setPeriodic(1, id -> {
        if (req.writeQueueFull()) {
          req.drainHandler(v1 -> {
            req.end().onSuccess(v2 -> {
              vertx.cancelTimer(id);
              drainedLatch.complete();
            });
          });
          full.complete(null);
        } else {
          req.write(chunk);
        }
      });
      req.response().onComplete(ctx.asyncAssertSuccess(resp -> {
        resp.body(ctx.asyncAssertSuccess(body -> {
          ctx.assertEquals("another-request", body.toString());
        }));
      }));
    }));
  }

  @Test
  public void testSendDefaultProxyResponse(TestContext ctx) {
    startHttpServer(ctx, serverOptions, req -> {
      ProxyRequest proxyReq = ProxyRequest.reverseProxy(req);
      proxyReq.response().send();
    });
    HttpClient client = vertx.createHttpClient();
    Async async = ctx.async();
    client.request(HttpMethod.GET, 8080, "localhost", "/somepath")
      .compose(req -> req.send().compose(resp -> {
        ctx.assertEquals(200, resp.statusCode());
        ctx.assertNull(resp.getHeader(HttpHeaders.TRANSFER_ENCODING));
        return resp.body();
      }))
      .onComplete(ctx.asyncAssertSuccess(body -> {
        ctx.assertEquals("", body.toString());
        async.complete();
      }));
  }

  @Test
  public void testSendProxyResponse(TestContext ctx) {
    startHttpServer(ctx, serverOptions, req -> {
      ProxyRequest proxyReq = ProxyRequest.reverseProxy(req);
      proxyReq.response()
        .setStatusCode(302)
        .setStatusMessage("some-status-message")
        .putHeader("some-header", "some-header-value")
        .setBody(Body.body(Buffer.buffer("hello world")))
        .send();
    });
    HttpClient client = vertx.createHttpClient();
    Async async = ctx.async();
    client.request(HttpMethod.GET, 8080, "localhost", "/somepath")
      .compose(req -> req.send().compose(resp -> {
        ctx.assertEquals(302, resp.statusCode());
        ctx.assertEquals("some-status-message", resp.statusMessage());
        ctx.assertEquals("some-header-value", resp.getHeader("some-header"));
        ctx.assertNull(resp.getHeader(HttpHeaders.TRANSFER_ENCODING));
        return resp.body();
      }))
      .onComplete(ctx.asyncAssertSuccess(body -> {
        ctx.assertEquals("hello world", body.toString());
        async.complete();
      }));
  }

  @Test
  public void testProxyRequestUnresolvedTarget(TestContext ctx) {
    startProxy(request -> Future.succeededFuture(SocketAddress.inetSocketAddress(1234, "localhost")));
    HttpClient client = vertx.createHttpClient();
    Async async = ctx.async();
    client.request(HttpMethod.GET, 8080, "localhost", "/")
      .compose(req -> req.send().compose(res -> {
        ctx.assertEquals(502, res.statusCode());
        return res.body();
      }))
      .onComplete(ctx.asyncAssertSuccess(body -> {
        ctx.assertEquals("", body.toString());
        async.complete();
      }));
  }

  private static Buffer CHUNK;

  static {
    byte[] bytes = new byte[1024];
    for (int i = 0;i < 1024;i++) {
      bytes[i] = (byte)('A' + (i % 26));
    }
    CHUNK = Buffer.buffer(bytes);
  }

  static class Filter implements ReadStream<Buffer> {

    private final AtomicBoolean paused = new AtomicBoolean();
    private ReadStream<Buffer> stream;
    private Buffer expected = Buffer.buffer();
    private Handler<Buffer> dataHandler;
    private Handler<Throwable> exceptionHandler;
    private Handler<Void> endHandler;

    ReadStream<Buffer> init(ReadStream<Buffer> s) {
      stream = s;
      stream.handler(buff -> {
        if (dataHandler != null) {
          byte[] bytes = new byte[buff.length()];
          for (int i = 0;i < bytes.length;i++) {
            bytes[i] = (byte)(('a' - 'A') + buff.getByte(i));
          }
          expected.appendBytes(bytes);
          dataHandler.handle(Buffer.buffer(bytes));
        }
      });
      stream.exceptionHandler(err -> {
        if (exceptionHandler != null) {
          exceptionHandler.handle(err);
        }
      });
      stream.endHandler(v -> {
        if (endHandler != null) {
          endHandler.handle(v);
        }
      });
      return this;
    }

    @Override
    public ReadStream<Buffer> pause() {
      paused.set(true);
      stream.pause();
      return this;
    }

    @Override
    public ReadStream<Buffer> resume() {
      stream.resume();
      return this;
    }

    @Override
    public ReadStream<Buffer> fetch(long amount) {
      stream.fetch(amount);
      return this;
    }

    @Override
    public ReadStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
      exceptionHandler = handler;
      return this;
    }

    @Override
    public ReadStream<Buffer> handler(Handler<Buffer> handler) {
      dataHandler = handler;
      return this;
    }

    @Override
    public ReadStream<Buffer> endHandler(Handler<Void> handler) {
      endHandler = handler;
      return this;
    }
  }

  private void runHttpTest(TestContext ctx,
                           Handler<HttpServerRequest> backendHandler,
                           Handler<AsyncResult<Void>> expect) {
    Async async = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, backendHandler);
    HttpClient client = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    startHttpServer(ctx, serverOptions, req -> {
      req.pause(); // Should it be necessary ?
      ProxyRequest proxyRequest = ProxyRequest.reverseProxy(req);
      client.request(new RequestOptions().setServer(backend), ar -> {
        if (ar.succeeded()) {
          proxyRequest.send(ar.result()).onComplete(ar2 -> {
            if (ar2.succeeded()) {
              ProxyResponse proxyResponse = ar2.result();
              proxyResponse.send().onComplete(ar3 -> {
                expect.handle(ar3);
                async.complete();
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

  private void runNetTest(TestContext ctx,
                       Handler<NetSocket> backendHandler,
                       Handler<AsyncResult<Void>> expect) {
    Async async = ctx.async();
    SocketAddress backend = startNetBackend(ctx, 8081, backendHandler);
    HttpClient backendClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    startHttpServer(ctx, serverOptions, req -> {
      ProxyRequest proxyReq = ProxyRequest.reverseProxy(req);
      backendClient.request(new RequestOptions().setServer(backend), ctx.asyncAssertSuccess(clientReq -> {
        proxyReq.proxy(clientReq).onComplete(ar -> {
          expect.handle(ar);
          async.complete();
        });
      }));
    });
  }
}
