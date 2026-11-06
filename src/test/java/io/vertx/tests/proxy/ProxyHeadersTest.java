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
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;
import io.vertx.junit5.VertxTestContext;
import io.vertx.tests.TestBase;
import org.junit.jupiter.api.Test;

import static io.vertx.core.http.HttpMethod.GET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ProxyHeadersTest extends TestBase {

  private HttpClient client;

  @Test
  public void testSuppressIncorrectWarningHeaders(VertxTestContext ctx) {
    SocketAddress backend = startHttpBackend(8081, req -> {
      req.response()
          .putHeader("date", "Tue, 15 Nov 1994 08:12:30 GMT")
          .putHeader("warning", "199 Miscellaneous warning \"Tue, 15 Nov 1994 08:12:31 GMT\"")
        .end();
    });
    startProxy(backend);
    client = vertx.createHttpClient();
    client.request(GET, 8080, "localhost", "/").onComplete(ctx.succeeding(req -> {
      req.send().onComplete(ctx.succeeding(resp -> {
        assertNotNull(resp.getHeader("date"));
        assertNull(resp.getHeader("warning"));
        ctx.completeNow();
      }));
    }));
  }

  @Test
  public void testAddMissingHeaderDate(VertxTestContext ctx) {
    SocketAddress backend = startHttpBackend(8081, req -> {
      req.response()
//          .putHeader("date", "Tue, 15 Nov 1994 08:12:30 GMT")
//          .putHeader("warning", "199 Miscellaneous warning \"Tue, 15 Nov 1994 08:12:31 GMT\"")
          .end();
    });
    startProxy(backend);
    client = vertx.createHttpClient();
    client.request(GET, 8080, "localhost", "/").onComplete(ctx.succeeding(req -> {
      req.send().onComplete(ctx.succeeding(resp -> {
        assertNotNull(resp.getHeader("date"));
        ctx.completeNow();
      }));
    }));
  }

  @Test
  public void testAddMissingHeaderDateFromWarning(VertxTestContext ctx) {
    String expectedDate = "Tue, 15 Nov 1994 08:12:31 GMT";
    String expectedWarning = "199 Miscellaneous warning \"" + expectedDate + "\"";
    SocketAddress backend = startHttpBackend(8081, req -> {
      req.response()
          .putHeader("warning", expectedWarning)
          .end();
    });
    startProxy(backend);
    client = vertx.createHttpClient();
    client.request(GET, 8080, "localhost", "/").onComplete(ctx.succeeding(req -> {
      req.send().onComplete(ctx.succeeding(resp -> {
        assertEquals(expectedDate, resp.getHeader("date"));
        assertEquals(expectedWarning, resp.getHeader("warning"));
        ctx.completeNow();
      }));
    }));
  }

  @Test
  public void testPropagateHeaders(VertxTestContext ctx) {
    SocketAddress backend = startHttpBackend(new HttpServerOptions().setPort(8081).setMaxInitialLineLength(10000), req -> {
      assertEquals("request_header_value", req.getHeader("request_header"));
      assertNull(req.getHeader("proxy-authenticate"));
      req.response().putHeader("response_header", "response_header_value").end();
    });
    startProxy(backend);
    client = vertx.createHttpClient();
    client.request(GET, 8080, "localhost", "/").onComplete(ctx.succeeding(req -> {
      req
        .putHeader("request_header", "request_header_value")
        .putHeader("proxy-authenticate", "proxy-authenticate_value")
        .send().onComplete(ctx.succeeding(resp -> {
        assertEquals(200, resp.statusCode());
        assertEquals("response_header_value", resp.getHeader("response_header"));
        ctx.completeNow();
      }));
    }));
  }

  @Test
  public void testIgnoreHopByHopResponseHeaders(VertxTestContext ctx) {
    SocketAddress backend = startHttpBackend(8081, req -> {
      req.response()
        .putHeader("Connection", "keep-alive")
        .putHeader("Keep-Alive", "timeout=5")
        .putHeader("X-End-To-End", "should-forward")
        .end("ok");
    });
    startProxy(backend);
    client = vertx.createHttpClient();
    client.request(GET, 8080, "localhost", "/").onComplete(ctx.succeeding(req -> {
      req.send()
        .compose(resp -> {
          assertEquals(200, resp.statusCode());
          assertEquals("should-forward", resp.getHeader("x-end-to-end"));
          assertNull(resp.getHeader("connection"));
          assertNull(resp.getHeader("keep-alive"));
          return resp.body();
        })
        .onComplete(ctx.succeeding(body -> {
          assertEquals("ok", body.toString());
          ctx.completeNow();
        }));
    }));
  }

  @Test
  public void testIPV6Authority(VertxTestContext ctx) {
    testAuthority(ctx, HostAndPort.authority("[7a03:908:671:b520:ba27:bbff:ffff:fed2]", 1234));
  }

  @Test
  public void testIPV4Authority(VertxTestContext ctx) {
    testAuthority(ctx, HostAndPort.authority("192.168.0.1", 1234));
  }

  @Test
  public void testMissingPortAuthority(VertxTestContext ctx) {
    testAuthority(ctx, HostAndPort.authority("localhost", -1));
  }

  private void testAuthority(VertxTestContext ctx, HostAndPort requestAuthority) {
    SocketAddress backend = startHttpBackend(8081, req -> {
      assertEquals("/somepath", req.uri());
      assertEquals("localhost", req.authority().host());
      assertEquals(8081, req.authority().port());
      assertEquals(null, req.getHeader("x-forwarded-host"));
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
          assertEquals(200, resp.statusCode());
          return resp.body();
        }))
      .onComplete(ctx.succeeding(body -> {
        assertEquals("Hello World", body.toString());
        ctx.completeNow();
      }));
  }

  @Test
  public void testAuthorityOverride1(VertxTestContext ctx) {
    testAuthorityOverride(ctx, HostAndPort.authority("foo", 8080), "foo:8080", "localhost:8080");
  }

  @Test
  public void testAuthorityOverride2(VertxTestContext ctx) {
    testAuthorityOverride(ctx, HostAndPort.authority("foo"), "foo", "localhost:8080");
  }

  @Test
  public void testAuthorityOverride3(VertxTestContext ctx) {
    testAuthorityOverride(ctx, HostAndPort.authority("localhost", 8080), "localhost:8080", null);
  }

  private void testAuthorityOverride(VertxTestContext ctx, HostAndPort authority, String expectedAuthority, String expectedForwardedHost) {
    SocketAddress backend = startHttpBackend(8081, req -> {
      assertEquals("/somepath", req.uri());
      assertEquals(expectedAuthority, req.authority().toString());
      assertEquals(expectedForwardedHost, req.getHeader("x-forwarded-host"));
      req.response().end("Hello World");
    });
    startProxy(proxy -> {
      proxy.origin(backend);
      proxy.addInterceptor(new ProxyInterceptor() {
        @Override
        public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
          ProxyRequest request = context.request();
          assertEquals("localhost", request.proxiedRequest().authority().host());
          assertEquals(8080, request.proxiedRequest().authority().port());
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
          assertEquals(200, resp.statusCode());
          return resp.body();
        }))
      .onComplete(ctx.succeeding(body -> {
        assertEquals("Hello World", body.toString());
        ctx.completeNow();
      }));
  }
}
