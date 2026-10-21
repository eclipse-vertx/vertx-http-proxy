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

import io.vertx.core.Future;
import io.vertx.core.http.*;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;
import io.vertx.tests.TestBase;
import org.junit.Test;

import static io.vertx.core.http.HttpMethod.GET;

public class ProxyHeadersTest extends TestBase {

  private HttpClient client;

  @Override
  public void tearDown(TestContext context) {
    super.tearDown(context);
    client = null;
  }

  @Test
  public void testSuppressIncorrectWarningHeaders(TestContext ctx) {
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.response()
          .putHeader("date", "Tue, 15 Nov 1994 08:12:30 GMT")
          .putHeader("warning", "199 Miscellaneous warning \"Tue, 15 Nov 1994 08:12:31 GMT\"")
        .end();
    });
    startProxy(backend);
    client = vertx.createHttpClient();
    client.request(GET, 8080, "localhost", "/").onComplete(ctx.asyncAssertSuccess(req -> {
      req.send().onComplete(ctx.asyncAssertSuccess(resp -> {
        ctx.assertNotNull(resp.getHeader("date"));
        ctx.assertNull(resp.getHeader("warning"));
      }));
    }));
  }

  @Test
  public void testAddMissingHeaderDate(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.response()
//          .putHeader("date", "Tue, 15 Nov 1994 08:12:30 GMT")
//          .putHeader("warning", "199 Miscellaneous warning \"Tue, 15 Nov 1994 08:12:31 GMT\"")
          .end();
    });
    startProxy(backend);
    client = vertx.createHttpClient();
    client.request(GET, 8080, "localhost", "/").onComplete(ctx.asyncAssertSuccess(req -> {
      req.send().onComplete(ctx.asyncAssertSuccess(resp -> {
        ctx.assertNotNull(resp.getHeader("date"));
        latch.complete();
      }));
    }));
  }

  @Test
  public void testAddMissingHeaderDateFromWarning(TestContext ctx) {
    String expectedDate = "Tue, 15 Nov 1994 08:12:31 GMT";
    String expectedWarning = "199 Miscellaneous warning \"" + expectedDate + "\"";
    Async latch = ctx.async();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.response()
          .putHeader("warning", expectedWarning)
          .end();
    });
    startProxy(backend);
    client = vertx.createHttpClient();
    client.request(GET, 8080, "localhost", "/").onComplete(ctx.asyncAssertSuccess(req -> {
      req.send().onComplete(ctx.asyncAssertSuccess(resp -> {
        ctx.assertEquals(expectedDate, resp.getHeader("date"));
        ctx.assertEquals(expectedWarning, resp.getHeader("warning"));
        latch.complete();
      }));
    }));
  }

  @Test
  public void testPropagateHeaders(TestContext ctx) {
    SocketAddress backend = startHttpBackend(ctx, new HttpServerOptions().setPort(8081).setMaxInitialLineLength(10000), req -> {
      ctx.assertEquals("request_header_value", req.getHeader("request_header"));
      ctx.assertNull(req.getHeader("proxy-authenticate"));
      req.response().putHeader("response_header", "response_header_value").end();
    });
    startProxy(backend);
    client = vertx.createHttpClient();
    Async latch = ctx.async();
    client.request(GET, 8080, "localhost", "/").onComplete(ctx.asyncAssertSuccess(req -> {
      req
        .putHeader("request_header", "request_header_value")
        .putHeader("proxy-authenticate", "proxy-authenticate_value")
        .send().onComplete(ctx.asyncAssertSuccess(resp -> {
        ctx.assertEquals(200, resp.statusCode());
        ctx.assertEquals("response_header_value", resp.getHeader("response_header"));
        latch.complete();
      }));
    }));
  }

  @Test
  public void testIgnoreHopByHopResponseHeaders(TestContext ctx) {
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      req.response()
        .putHeader("Connection", "keep-alive")
        .putHeader("Keep-Alive", "timeout=5")
        .putHeader("X-End-To-End", "should-forward")
        .end("ok");
    });
    startProxy(backend);
    client = vertx.createHttpClient();
    Async latch = ctx.async();
    client.request(GET, 8080, "localhost", "/").onComplete(ctx.asyncAssertSuccess(req -> {
      req.send()
        .compose(resp -> {
          ctx.assertEquals(200, resp.statusCode());
          ctx.assertEquals("should-forward", resp.getHeader("x-end-to-end"));
          ctx.assertNull(resp.getHeader("connection"));
          ctx.assertNull(resp.getHeader("keep-alive"));
          return resp.body();
        })
        .onComplete(ctx.asyncAssertSuccess(body -> {
          ctx.assertEquals("ok", body.toString());
          latch.complete();
        }));
    }));
  }

  @Test
  public void testIPV6Authority(TestContext ctx) {
    testAuthority(ctx, HostAndPort.authority("[7a03:908:671:b520:ba27:bbff:ffff:fed2]", 1234));
  }

  @Test
  public void testIPV4Authority(TestContext ctx) {
    testAuthority(ctx, HostAndPort.authority("192.168.0.1", 1234));
  }

  @Test
  public void testMissingPortAuthority(TestContext ctx) {
    testAuthority(ctx, HostAndPort.authority("localhost", -1));
  }

  private void testAuthority(TestContext ctx, HostAndPort requestAuthority) {
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      ctx.assertEquals("/somepath", req.uri());
      ctx.assertEquals("localhost", req.authority().host());
      ctx.assertEquals(8081, req.authority().port());
      ctx.assertEquals(null, req.getHeader("x-forwarded-host"));
      req.response().end("Hello World");
    });
    startProxy(proxy -> {
      proxy.origin(backend);
    });
    client = vertx.createHttpClient();
    client.request(GET, 8080, "localhost", "/somepath")
      .compose(req -> req
        .authority(requestAuthority)
        .send()
        .compose(resp -> {
          ctx.assertEquals(200, resp.statusCode());
          return resp.body();
        }))
      .onComplete(ctx.asyncAssertSuccess(body -> {
        ctx.assertEquals("Hello World", body.toString());
      }));
  }

  @Test
  public void testAuthorityOverride1(TestContext ctx) {
    testAuthorityOverride(ctx, HostAndPort.authority("foo", 8080), "foo:8080", "localhost:8080");
  }

  @Test
  public void testAuthorityOverride2(TestContext ctx) {
    testAuthorityOverride(ctx, HostAndPort.authority("foo"), "foo", "localhost:8080");
  }

  @Test
  public void testAuthorityOverride3(TestContext ctx) {
    testAuthorityOverride(ctx, HostAndPort.authority("localhost", 8080), "localhost:8080", null);
  }

  private void testAuthorityOverride(TestContext ctx, HostAndPort authority, String expectedAuthority, String expectedForwardedHost) {
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      ctx.assertEquals("/somepath", req.uri());
      ctx.assertEquals(expectedAuthority, req.authority().toString());
      ctx.assertEquals(expectedForwardedHost, req.getHeader("x-forwarded-host"));
      req.response().end("Hello World");
    });
    startProxy(proxy -> {
      proxy.origin(backend);
      proxy.addInterceptor(new ProxyInterceptor() {
        @Override
        public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
          ProxyRequest request = context.request();
          ctx.assertEquals("localhost", request.proxiedRequest().authority().host());
          ctx.assertEquals(8080, request.proxiedRequest().authority().port());
          request.setAuthority(authority);
          return ProxyInterceptor.super.handleProxyRequest(context);
        }
      });
    });
    client = vertx.createHttpClient();
    client.request(GET, 8080, "localhost", "/somepath")
      .compose(req -> req
        .send()
        .compose(resp -> {
          ctx.assertEquals(200, resp.statusCode());
          return resp.body();
        }))
      .onComplete(ctx.asyncAssertSuccess(body -> {
        ctx.assertEquals("Hello World", body.toString());
      }));
  }
}
