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
package io.vertx.tests.cache;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.TestContext;
import io.vertx.httpproxy.BodyTransformers;
import io.vertx.httpproxy.ProxyInterceptor;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.assertEquals;

/**
 * The cache interceptor is registered before user interceptors: on the response path user interceptors run first, so
 * the cache stores the transformed body, and on a cache hit the request does not reach later interceptors.
 */
public class CacheBodyTransformerTest extends CacheTestBase {

  @Test
  public void testCachedResponseHasTransformedBody(TestContext ctx) {
    AtomicInteger backendHits = new AtomicInteger();
    AtomicInteger transformations = new AtomicInteger();
    SocketAddress backend = startHttpBackend(8081, req -> {
      backendHits.incrementAndGet();
      req.response()
        .putHeader(HttpHeaders.CONTENT_TYPE, "text/plain")
        .putHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=60")
        .end("content");
    });
    startProxy(proxy -> proxy.origin(backend)
      .addInterceptor(ProxyInterceptor.builder().transformingResponseBody(
        BodyTransformers.text(text -> {
          transformations.incrementAndGet();
          return text.toUpperCase();
        }, "UTF-8")).build()));
    HttpClient client = vertx.createHttpClient();
    client.request(HttpMethod.GET, 8080, "localhost", "/")
      .compose(req1 -> req1.send().compose(resp1 -> {
        ctx.verify(v -> {
          assertEquals(200, resp1.statusCode());
        });
        return resp1.body();
      }))
      .compose(body1 -> {
        ctx.verify(v -> {
          assertEquals("CONTENT", body1.toString());
          assertEquals(1, backendHits.get());
          assertEquals(1, transformations.get());
        });
        return client.request(HttpMethod.GET, 8080, "localhost", "/");
      })
      .compose(req2 -> req2.send().compose(resp2 -> {
        ctx.verify(v -> {
          assertEquals(200, resp2.statusCode());
        });
        return resp2.body();
      }))
      .onComplete(ctx.asyncAssertSuccess(body2 -> {
        ctx.verify(v -> {
          assertEquals("CONTENT", body2.toString());
          assertEquals(1, backendHits.get());
          assertEquals(1, transformations.get());
        });
      }));
  }
}
