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
package io.vertx.tests;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.httpproxy.*;
import org.junit.Test;

import static io.vertx.core.http.HttpHeaders.CONTENT_LENGTH;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ProxyTest extends TestBase {

  private HttpClient client;

  @Override
  public void tearDown(TestContext context) {
    super.tearDown(context);
    client = null;
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

}
