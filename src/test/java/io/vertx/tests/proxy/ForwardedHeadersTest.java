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

import io.vertx.core.Handler;
import io.vertx.core.http.*;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.httpproxy.ForwardedHeadersOptions;
import io.vertx.httpproxy.ProxyOptions;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;
import io.vertx.tests.VertxTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ForwardedHeadersTest extends VertxTestBase {

  public ForwardedHeadersTest() {
    super(new ProxyOptions());
  }

  @Test
  public void testForwardedHeadersDisabledByDefault(VertxTestContext ctx) {
    runForwardedHeadersTest(ctx, null, null, null, req -> {
      assertNull(req.getHeader("X-Forwarded-For"));
      assertNull(req.getHeader("X-Forwarded-Proto"));
      assertNull(req.getHeader("X-Forwarded-Host"));
      assertNull(req.getHeader("X-Forwarded-Port"));
      assertNull(req.getHeader("Forwarded"));
    });
  }

  @Test
  public void testXForwardedForEnabled(VertxTestContext ctx) {
    ProxyOptions options = new ProxyOptions()
      .setForwardedHeadersOptions(new ForwardedHeadersOptions()
        .setEnabled(true)
        .setForwardFor(true)
        .setForwardProto(false)
        .setForwardHost(false)
        .setForwardPort(false)
      );

    runForwardedHeadersTest(ctx, options, null, null, req -> {
      String xForwardedFor = req.getHeader("X-Forwarded-For");
      assertNotNull(xForwardedFor);
      assertTrue(xForwardedFor.contains("127.0.0.1") || xForwardedFor.contains("0:0:0:0:0:0:0:1"));
      assertNull(req.getHeader("X-Forwarded-Proto"));
      assertNull(req.getHeader("X-Forwarded-Host"));
      assertNull(req.getHeader("X-Forwarded-Port"));
    });
  }

  @Test
  public void testXForwardedProtoEnabled(VertxTestContext ctx) {
    ProxyOptions options = new ProxyOptions()
      .setForwardedHeadersOptions(new ForwardedHeadersOptions()
        .setEnabled(true)
        .setForwardFor(false)
        .setForwardProto(true)
        .setForwardHost(false)
        .setForwardPort(false)
      );

    runForwardedHeadersTest(ctx, options, null, null, req -> {
      String xForwardedProto = req.getHeader("X-Forwarded-Proto");
      assertNotNull(xForwardedProto);
      assertEquals("http", xForwardedProto);
      assertNull(req.getHeader("X-Forwarded-For"));
    });
  }

  @Test
  public void testXForwardedHostEnabled(VertxTestContext ctx) {
    ProxyOptions options = new ProxyOptions()
      .setForwardedHeadersOptions(new ForwardedHeadersOptions()
        .setEnabled(true)
        .setForwardFor(false)
        .setForwardProto(false)
        .setForwardHost(true)
        .setForwardPort(false)
      );

    runForwardedHeadersTest(ctx, options, null, null, req -> {
      String xForwardedHost = req.getHeader("X-Forwarded-Host");
      assertNotNull(xForwardedHost);
      assertEquals("localhost", xForwardedHost);
    });
  }

  @Test
  public void testXForwardedPortEnabled(VertxTestContext ctx) {
    ProxyOptions options = new ProxyOptions()
      .setForwardedHeadersOptions(new ForwardedHeadersOptions()
        .setEnabled(true)
        .setForwardFor(false)
        .setForwardProto(false)
        .setForwardHost(false)
        .setForwardPort(true)
      );

    runForwardedHeadersTest(ctx, options, null, null, req -> {
      String xForwardedPort = req.getHeader("X-Forwarded-Port");
      assertNotNull(xForwardedPort);
      assertEquals("8080", xForwardedPort);
    });
  }

  @Test
  public void testAllXForwardedHeadersEnabled(VertxTestContext ctx) {
    ProxyOptions options = new ProxyOptions()
      .setForwardedHeadersOptions(new ForwardedHeadersOptions()
        .setEnabled(true)
        .setForwardFor(true)
        .setForwardProto(true)
        .setForwardHost(true)
        .setForwardPort(true)
      );

    runForwardedHeadersTest(ctx, options, null, null, req -> {
      String xForwardedFor = req.getHeader("X-Forwarded-For");
      assertNotNull(xForwardedFor);
      assertTrue(xForwardedFor.contains("127.0.0.1") || xForwardedFor.contains("0:0:0:0:0:0:0:1"));

      String xForwardedProto = req.getHeader("X-Forwarded-Proto");
      assertNotNull(xForwardedProto);
      assertEquals("http", xForwardedProto);

      String xForwardedHost = req.getHeader("X-Forwarded-Host");
      assertNotNull(xForwardedHost);
      assertEquals("localhost", xForwardedHost);

      String xForwardedPort = req.getHeader("X-Forwarded-Port");
      assertNotNull(xForwardedPort);
      assertEquals("8080", xForwardedPort);
    });
  }

  @Test
  public void testXForwardedForChainPreservation(VertxTestContext ctx) {
    ProxyOptions options = new ProxyOptions()
      .setForwardedHeadersOptions(new ForwardedHeadersOptions()
        .setEnabled(true)
        .setForwardFor(true)
      );

    runForwardedHeadersTest(ctx, options, null, req -> {
      req.putHeader("X-Forwarded-For", "192.168.1.100");
    }, req -> {
      String xForwardedFor = req.getHeader("X-Forwarded-For");
      assertNotNull(xForwardedFor);
      // Should contain both the original client and the proxy's append
      List<String> parts = List.of(xForwardedFor.split(",\\s*"));
      assertTrue(parts.size() >= 2);
      assertEquals("192.168.1.100", parts.get(0));
      assertTrue(parts.get(1).contains("127.0.0.1") || parts.get(1).contains("0:0:0:0:0:0:0:1"));
    });
  }

  @Test
  public void testRfc7239ForwardedHeader(VertxTestContext ctx) {
    ProxyOptions options = new ProxyOptions()
      .setForwardedHeadersOptions(new ForwardedHeadersOptions()
        .setEnabled(true)
        .setUseRfc7239(true)
        .setForwardFor(true)
        .setForwardProto(true)
        .setForwardHost(true)
      );

    // Should NOT have X-Forwarded-* headers when using RFC 7239
    runForwardedHeadersTest(ctx, options, null, null, req -> {
      String forwarded = req.getHeader("Forwarded");
      assertNotNull(forwarded);
      assertTrue(forwarded.contains("for="));
      assertTrue(forwarded.contains("proto=http"));
      assertTrue(forwarded.contains("host="));

      // Should NOT have X-Forwarded-* headers when using RFC 7239
      assertNull(req.getHeader("X-Forwarded-For"));
      assertNull(req.getHeader("X-Forwarded-Proto"));
    });
  }

  @Test
  public void testRfc7239ForwardedHeaderChainPreservation(VertxTestContext ctx) {
    ProxyOptions options = new ProxyOptions()
      .setForwardedHeadersOptions(new ForwardedHeadersOptions()
        .setEnabled(true)
        .setUseRfc7239(true)
        .setForwardFor(true)
        .setForwardProto(true)
      );

    runForwardedHeadersTest(ctx, options, null, req -> {
      req.putHeader("Forwarded", "for=192.0.2.60;proto=https");
    }, req -> {
      String forwarded = req.getHeader("Forwarded");
      assertNotNull(forwarded);
      // Should contain both the original and the appended entry
      List<String> parts = List.of(forwarded.split(",\\s*"));
      assertEquals(2, parts.size());
      assertTrue(parts.get(0).contains("for=192.0.2.60"));
      assertTrue(parts.get(1).contains("for="));
      assertTrue(parts.get(1).contains("proto=http"));
    });
  }

  @Test
  public void testRfc7239ForOnlyEnabled(VertxTestContext ctx) {
    ProxyOptions options = new ProxyOptions()
      .setForwardedHeadersOptions(new ForwardedHeadersOptions()
        .setEnabled(true)
        .setUseRfc7239(true)
        .setForwardFor(true)
        .setForwardProto(false)
        .setForwardHost(false)
      );

    runForwardedHeadersTest(ctx, options, null, null, req -> {
      String forwarded = req.getHeader("Forwarded");
      assertNotNull(forwarded);
      assertTrue(forwarded.contains("for="));
      assertFalse(forwarded.contains("proto="));
      assertFalse(forwarded.contains("host="));
    });
  }

  @Test
  public void testForwardedHeadersWithHttps(VertxTestContext ctx) {
    ProxyOptions options = new ProxyOptions()
      .setForwardedHeadersOptions(new ForwardedHeadersOptions()
        .setEnabled(true)
        .setForwardProto(true)
      );

    // Load test certificates from classpath (see src/test/resources/SSL_TEST_CERTIFICATES.txt)
    PemKeyCertOptions pemOptions = new PemKeyCertOptions()
      .setCertPath("server.cert.pem")
      .setKeyPath("server.key.pem");

    HttpServerOptions httpsServerOptions = new HttpServerOptions()
      .setPort(8443)
      .setHost("localhost")
      .setSsl(true)
      .setKeyCertOptions(pemOptions);

    runForwardedHeadersTest(ctx, options, httpsServerOptions, null, req -> {
      String xForwardedProto = req.getHeader("X-Forwarded-Proto");
      assertNotNull(xForwardedProto);
      assertEquals("https", xForwardedProto);
    });
  }

  @Test
  public void testEnabledFalseDoesNotAddHeaders(VertxTestContext ctx) {
    ProxyOptions options = new ProxyOptions()
      .setForwardedHeadersOptions(new ForwardedHeadersOptions()
        .setEnabled(false)  // Explicitly disabled
        .setForwardFor(true)
        .setForwardProto(true)
      );

    runForwardedHeadersTest(ctx, options, null, null, req -> {
      assertNull(req.getHeader("X-Forwarded-For"));
      assertNull(req.getHeader("X-Forwarded-Proto"));
    });
  }

  private void runForwardedHeadersTest(VertxTestContext ctx, ProxyOptions options, HttpServerOptions serverOpts, Consumer<HttpClientRequest> requestCustomizer, Handler<HttpServerRequest> backendHandler) {
    Checkpoint latch = ctx.checkpoint();

    SocketAddress backend = startHttpBackend(8081, req -> {
      backendHandler.handle(req);
      req.response().end("OK");
    });

    if (options != null) {
      proxyOptions = options;
    }
    if (serverOpts != null) {
      serverOptions = serverOpts;
    }
    startProxy(backend);

    // Determine client settings based on server options
    boolean isHttps = serverOpts != null && serverOpts.isSsl();
    int port = isHttps ? 8443 : 8080;
    HttpClient client = isHttps
      ? vertx.createHttpClient(new HttpClientOptions().setSsl(true).setTrustAll(true).setVerifyHost(false))
      : vertx.createHttpClient();

    client
      .request(HttpMethod.GET, port, "localhost", "/")
      .compose(req -> {
        if (requestCustomizer != null) {
          requestCustomizer.accept(req);
        }
        return req.send().compose(HttpClientResponse::body);
      })
      .onComplete(ctx.succeeding(body -> {
        assertEquals("OK", body.toString());
        latch.flag();
      }));

    latch.await();
  }
}
