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
package io.vertx.tests.proxy;

import io.vertx.core.Future;
import io.vertx.core.http.*;
import io.vertx.core.net.SocketAddress;
import io.vertx.httpproxy.OriginRequestProvider;
import io.vertx.junit5.VertxTestContext;
import io.vertx.tests.TestBase;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProxyOriginTest extends TestBase {

  private HttpClient client;

  @Test
  public void testRoundRobinSelector(VertxTestContext ctx) {
    int numRequests = 10;
    SocketAddress[] backends = new SocketAddress[3];
    for (int i = 0;i < backends.length;i++) {
      int value = i;
      backends[i] = startHttpBackend(8081 + value, req -> req.response().end("" + value));
    }
    AtomicInteger count = new AtomicInteger();
    startProxy(proxy -> proxy.origin(OriginRequestProvider.selector(proxyContext -> Future.succeededFuture(backends[count.getAndIncrement() % backends.length]))));
    client = vertx.createHttpClient();
    Map<String, AtomicInteger> result = Collections.synchronizedMap(new HashMap<>());
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
              assertEquals(numRequests, val.get());
            }
            assertEquals(result, Collections.emptyMap());
            ctx.completeNow();
          }
        }
      });
    }
  }

  @Test
  public void testUpstreamRefuse(VertxTestContext ctx) {
    SocketAddress backend = SocketAddress.inetSocketAddress(8081, "localhost");
    startProxy(proxy -> proxy.origin(backend));
    client = vertx.createHttpClient();
    client.request(HttpMethod.GET, 8080, "localhost", "/")
      .compose(req -> req.send().compose(resp -> {
        assertEquals(502, resp.statusCode());
        return resp.body();
      }))
      .onComplete(ctx.succeedingThenComplete());
  }

  @Test
  public void testMissingOriginReturns502(VertxTestContext ctx) {
    startProxy(request -> Future.succeededFuture(SocketAddress.inetSocketAddress(1234, "localhost")));
    client = vertx.createHttpClient();
    client.request(HttpMethod.GET, 8080, "localhost", "/")
      .compose(req -> req.send().compose(res -> {
        assertEquals(502, res.statusCode());
        return res.body();
      }))
      .onComplete(ctx.succeeding(body -> {
        assertEquals("", body.toString());
        ctx.completeNow();
      }));
  }
}
