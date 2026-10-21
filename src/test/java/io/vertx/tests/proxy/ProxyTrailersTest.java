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
import io.vertx.ext.unit.TestContext;
import io.vertx.httpproxy.*;
import io.vertx.tests.TestBase;
import org.junit.Test;

public class ProxyTrailersTest extends TestBase {

  private HttpClient client;

  @Override
  public void tearDown(TestContext context) {
    super.tearDown(context);
    client = null;
  }

  @Test
  public void testTrailersForwarding(TestContext ctx) {
    testTrailers(ctx, null);
  }

  @Test
  public void testTrailersNotForwardedWhenBodyIsModified(TestContext ctx) {
    testTrailers(ctx, new ProxyInterceptor() {
      @Override
      public Future<Void> handleProxyResponse(ProxyContext context) {
        ProxyResponse proxyResponse = context.response();
        proxyResponse.setBody(Body.body(io.vertx.core.buffer.Buffer.buffer("modified body")));
        return context.sendResponse();
      }
    });
  }

  @Test
  public void testTrailersNotForwardedWhenBodyIsTransformed(TestContext ctx) {
    ProxyInterceptor interceptor = ProxyInterceptor.builder()
      .transformingResponseBody(BodyTransformers.text(String::toUpperCase, "UTF-8"))
      .build();
    testTrailers(ctx, interceptor);
  }

  private void testTrailers(TestContext ctx, ProxyInterceptor interceptor) {
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.response()
        .setChunked(true)
        .putHeader("content-type", "text/plain")
        .write("response body");
      req.response().trailers()
        .add("X-Custom-Trailer", "trailer-value")
        .add("X-Another-Trailer", "another-value");
      req.response().end();
    });
    startProxy(proxy -> {
      proxy.origin(backend);
      if (interceptor != null) {
        proxy.addInterceptor(interceptor);
      }
    });
    client = vertx.createHttpClient();
    client.request(HttpMethod.GET, 8080, "localhost", "/")
      .compose(req -> req.send().compose(resp -> {
        ctx.assertEquals(200, resp.statusCode());
        return resp.body().map(body -> resp);
      }))
      .onComplete(ctx.asyncAssertSuccess(resp -> {
        if (interceptor == null) {
          ctx.assertEquals("trailer-value", resp.trailers().get("X-Custom-Trailer"));
          ctx.assertEquals("another-value", resp.trailers().get("X-Another-Trailer"));
        } else {
          ctx.assertNull(resp.trailers().get("X-Custom-Trailer"));
          ctx.assertNull(resp.trailers().get("X-Another-Trailer"));
        }
      }));
  }
}
