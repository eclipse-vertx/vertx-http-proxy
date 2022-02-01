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
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunnerWithParametersFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(VertxUnitRunnerWithParametersFactory.class)
public class ProxyTest extends ProxyTestBase {

  public ProxyTest(ProxyOptions options) {
    super(options);
  }

  @Test
  public void testRoundRobinSelector(TestContext ctx) {
    int numRequests = 10;
    SocketAddress[] backends = new SocketAddress[3];
    for (int i = 0;i < backends.length;i++) {
      int value = i;
      backends[i] = startHttpBackend(ctx, 8081 + value, req -> req.response().end("" + value));
    }
    AtomicInteger count = new AtomicInteger();
    startProxy(proxy -> proxy.originSelector(req -> Future.succeededFuture(backends[count.getAndIncrement() % backends.length])));
    HttpClient client = vertx.createHttpClient();
    Map<String, AtomicInteger> result = Collections.synchronizedMap(new HashMap<>());
    Async latch = ctx.async();
    for (int i = 0;i < backends.length * numRequests;i++) {
      client
          .request(HttpMethod.GET, 8080, "localhost", "/")
          .compose(req -> req
              .send()
              .compose(HttpClientResponse::body)
          ).onSuccess(buff -> {
        result.computeIfAbsent(buff.toString(), k -> new AtomicInteger()).getAndIncrement();
        synchronized (result) {
          int total = result.values().stream().reduce(0, (a, b) -> a + b.get(), (a, b) -> a + b);
          if (total == backends.length * numRequests) {
            for (int j = 0;j < backends.length;j++) {
              AtomicInteger val = result.remove("" + j);
              ctx.assertEquals(numRequests, val.get());
            }
            ctx.assertEquals(result, Collections.emptyMap());
            latch.complete();
          }
        }
      });
    }
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
    HttpClient client = vertx.createHttpClient();
    client
      .request(HttpMethod.GET, 8080, "localhost", "/")
      .compose(req -> req
        .send()
        .compose(HttpClientResponse::body)
      ).onSuccess(buff -> {
        latch.countDown();
      });
  }
}
