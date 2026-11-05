/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
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
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.ReadStream;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.httpproxy.*;
import io.vertx.tests.TestBase;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.vertx.core.http.HttpHeaders.CONTENT_LENGTH;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ProxyInterceptorTest extends TestBase {

  private HttpClient client;
  private HttpClient httpClient;
  private HttpClient backendClient;

  @Override
  public void tearDown(TestContext context) {
    super.tearDown(context);
    client = null;
    httpClient = null;
    backendClient = null;
  }

  @Test
  public void testFilter(TestContext ctx) {
    Async latch = ctx.async(3);
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> req.response().end("HOLA"));
    startProxy(proxy -> proxy.origin(backend).addInterceptor(new ProxyInterceptor() {
      @Override
      public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
        Future<ProxyResponse> fut = context.sendRequest();
        fut.onComplete(ctx.asyncAssertSuccess(v -> latch.countDown()));
        return fut;
      }
      @Override
      public Future<Void> handleProxyResponse(ProxyContext context) {
        Future<Void> fut = context.sendResponse();
        fut.onComplete(ctx.asyncAssertSuccess(v -> latch.countDown()));
        return fut;
      }
    }));
    client = vertx.createHttpClient();
    client
      .request(HttpMethod.GET, 8080, "localhost", "/")
      .compose(req -> req
        .send()
        .compose(HttpClientResponse::body)
      ).onSuccess(buff -> {
        latch.countDown();
      });
  }

  @Test
  public void testFilterNullBodies(TestContext ctx) {
    Async latch = ctx.async(3);
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.body().onComplete(ctx.asyncAssertSuccess(body -> {
        ctx.assertEquals(0, body.length());
        ctx.assertEquals("0", req.getHeader(CONTENT_LENGTH));
        req.response().end("IGNORED_BACKEND_RESPONSE_BODY");
      }));
    });
    startProxy(proxy -> proxy.origin(backend).addInterceptor(new ProxyInterceptor() {
      @Override
      public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
        context.request().setBody(null);
        Future<ProxyResponse> fut = context.sendRequest();
        fut.onComplete(ctx.asyncAssertSuccess(v -> latch.countDown()));
        return fut;
      }

      @Override
      public Future<Void> handleProxyResponse(ProxyContext context) {
        context.response().setBody(null);
        Future<Void> fut = context.sendResponse();
        fut.onComplete(ctx.asyncAssertSuccess(v -> latch.countDown()));
        return fut;
      }
    }));
    client = vertx.createHttpClient();
    client
      .request(HttpMethod.POST, 8080, "localhost", "/")
      .compose(req -> req
        .send("IGNORED_CLIENT_REQUEST_BODY")
        .compose(resp -> resp.body().map(resp))
      ).onComplete(ctx.asyncAssertSuccess(resp -> {
        ctx.assertEquals(0, resp.body().result().length());
        ctx.assertEquals("0", resp.getHeader(CONTENT_LENGTH));
        latch.countDown();
      }));
  }

  @Test
  public void testFilterRequestFail(TestContext ctx) {
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> req.response().end("HOLA"));
    startProxy(proxy -> proxy.origin(backend).addInterceptor(new ProxyInterceptor() {
      @Override
      public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
        return Future.failedFuture(new RuntimeException("Some error"));
      }
    }));
    client = vertx.createHttpClient();
    Async async = ctx.async();
    client.request(HttpMethod.GET, 8080, "localhost", "/")
      .compose(req -> req.send().compose(resp -> {
        ctx.assertEquals(502, resp.statusCode());
        return resp.body();
      }))
      .onComplete(ctx.asyncAssertSuccess(body -> async.complete()));
  }

  @Test
  public void testFilterResponseFail(TestContext ctx) {
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> req.response().end("HOLA"));
    startProxy(proxy -> proxy.origin(backend).addInterceptor(new ProxyInterceptor() {
      @Override
      public Future<Void> handleProxyResponse(ProxyContext context) {
        return Future.failedFuture(new RuntimeException("Some error"));
      }
    }));
    client = vertx.createHttpClient();
    Async async = ctx.async();
    client.request(HttpMethod.GET, 8080, "localhost", "/")
      .compose(req -> req.send().compose(resp -> {
        ctx.assertEquals(502, resp.statusCode());
        return resp.body();
      }))
      .onComplete(ctx.asyncAssertSuccess(body -> async.complete()));
  }

  @Test
  public void testVariableFromInterceptor(TestContext ctx) {
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> req.response().end("HOLA"));
    ProxyInterceptor interceptor = new ProxyInterceptor() {
      @Override
      public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
        context.set("foo", "bar");
        return context.sendRequest();
      }
    };
    OriginRequestProvider provider = (proxyContext) -> {
      ctx.assertEquals("bar", proxyContext.get("foo", String.class));
      return client.request(new RequestOptions().setServer(backend));
    };
    startProxy(proxy -> proxy.origin(provider).addInterceptor(interceptor));
    client = vertx.createHttpClient();
    client
      .request(HttpMethod.GET, 8080, "localhost", "/")
      .compose(req -> req
        .send()
        .compose(HttpClientResponse::body)
      )
      .onComplete(ctx.asyncAssertSuccess(buffer -> ctx.assertEquals("HOLA", buffer.toString())));
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
    backendClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
    httpClient = vertx.createHttpClient();
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
    httpClient.request(HttpMethod.POST, 8080, "localhost", "/somepath").onComplete(ctx.asyncAssertSuccess(req -> {
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
    backendClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
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
    httpClient = vertx.createHttpClient();
    httpClient.request(HttpMethod.GET, 8080, "localhost", "/somepath").onComplete(ctx.asyncAssertSuccess(req -> {
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


  private static Buffer CHUNK;

  static {
    byte[] bytes = new byte[1024];
    for (int i = 0; i < 1024; i++) {
      bytes[i] = (byte) ('A' + (i % 26));
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
          for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (('a' - 'A') + buff.getByte(i));
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
}
