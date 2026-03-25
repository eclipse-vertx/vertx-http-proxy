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

import io.vertx.core.Handler;
import io.vertx.core.http.*;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.httpproxy.ForwardedHeadersOptions;
import io.vertx.httpproxy.ProxyOptions;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.function.Consumer;

@RunWith(VertxUnitRunner.class)
public class ForwardedHeadersTest extends TestBase {

  public ForwardedHeadersTest() {
    super(new ProxyOptions());
  }

  @Test
  public void testForwardedHeadersDisabledByDefault(TestContext ctx) {
    runForwardedHeadersTest(ctx, null, null, null, req -> {
      ctx.assertNull(req.getHeader("X-Forwarded-For"));
      ctx.assertNull(req.getHeader("X-Forwarded-Proto"));
      ctx.assertNull(req.getHeader("X-Forwarded-Host"));
      ctx.assertNull(req.getHeader("X-Forwarded-Port"));
      ctx.assertNull(req.getHeader("Forwarded"));
    });
  }

  @Test
  public void testXForwardedForEnabled(TestContext ctx) {
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
      ctx.assertNotNull(xForwardedFor);
      ctx.assertTrue(xForwardedFor.contains("127.0.0.1") || xForwardedFor.contains("0:0:0:0:0:0:0:1"));
      ctx.assertNull(req.getHeader("X-Forwarded-Proto"));
      ctx.assertNull(req.getHeader("X-Forwarded-Host"));
      ctx.assertNull(req.getHeader("X-Forwarded-Port"));
    });
  }

  @Test
  public void testXForwardedProtoEnabled(TestContext ctx) {
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
      ctx.assertNotNull(xForwardedProto);
      ctx.assertEquals("http", xForwardedProto);
      ctx.assertNull(req.getHeader("X-Forwarded-For"));
    });
  }

  @Test
  public void testXForwardedHostEnabled(TestContext ctx) {
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
      ctx.assertNotNull(xForwardedHost);
      ctx.assertEquals("localhost", xForwardedHost);
    });
  }

  @Test
  public void testXForwardedPortEnabled(TestContext ctx) {
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
      ctx.assertNotNull(xForwardedPort);
      ctx.assertEquals("8080", xForwardedPort);
    });
  }

  @Test
  public void testAllXForwardedHeadersEnabled(TestContext ctx) {
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
      ctx.assertNotNull(xForwardedFor);
      ctx.assertTrue(xForwardedFor.contains("127.0.0.1") || xForwardedFor.contains("0:0:0:0:0:0:0:1"));

      String xForwardedProto = req.getHeader("X-Forwarded-Proto");
      ctx.assertNotNull(xForwardedProto);
      ctx.assertEquals("http", xForwardedProto);

      String xForwardedHost = req.getHeader("X-Forwarded-Host");
      ctx.assertNotNull(xForwardedHost);
      ctx.assertEquals("localhost", xForwardedHost);

      String xForwardedPort = req.getHeader("X-Forwarded-Port");
      ctx.assertNotNull(xForwardedPort);
      ctx.assertEquals("8080", xForwardedPort);
    });
  }

  @Test
  public void testXForwardedForChainPreservation(TestContext ctx) {
    ProxyOptions options = new ProxyOptions()
      .setForwardedHeadersOptions(new ForwardedHeadersOptions()
        .setEnabled(true)
        .setForwardFor(true)
      );

    runForwardedHeadersTest(ctx, options, null, req -> {
      req.putHeader("X-Forwarded-For", "192.168.1.100");
    }, req -> {
      String xForwardedFor = req.getHeader("X-Forwarded-For");
      ctx.assertNotNull(xForwardedFor);
      // Should contain both the original client and the proxy's append
      List<String> parts = List.of(xForwardedFor.split(",\\s*"));
      ctx.assertTrue(parts.size() >= 2);
      ctx.assertEquals("192.168.1.100", parts.get(0));
      ctx.assertTrue(parts.get(1).contains("127.0.0.1") || parts.get(1).contains("0:0:0:0:0:0:0:1"));
    });
  }

  @Test
  public void testRfc7239ForwardedHeader(TestContext ctx) {
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
      ctx.assertNotNull(forwarded);
      ctx.assertTrue(forwarded.contains("for="));
      ctx.assertTrue(forwarded.contains("proto=http"));
      ctx.assertTrue(forwarded.contains("host="));

      // Should NOT have X-Forwarded-* headers when using RFC 7239
      ctx.assertNull(req.getHeader("X-Forwarded-For"));
      ctx.assertNull(req.getHeader("X-Forwarded-Proto"));
    });
  }

  @Test
  public void testRfc7239ForwardedHeaderChainPreservation(TestContext ctx) {
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
      ctx.assertNotNull(forwarded);
      // Should contain both the original and the appended entry
      List<String> parts = List.of(forwarded.split(",\\s*"));
      ctx.assertEquals(2, parts.size());
      ctx.assertTrue(parts.get(0).contains("for=192.0.2.60"));
      ctx.assertTrue(parts.get(1).contains("for="));
      ctx.assertTrue(parts.get(1).contains("proto=http"));
    });
  }

  @Test
  public void testRfc7239ForOnlyEnabled(TestContext ctx) {
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
      ctx.assertNotNull(forwarded);
      ctx.assertTrue(forwarded.contains("for="));
      ctx.assertFalse(forwarded.contains("proto="));
      ctx.assertFalse(forwarded.contains("host="));
    });
  }

  @Test
  public void testForwardedHeadersWithHttps(TestContext ctx) {
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
      ctx.assertNotNull(xForwardedProto);
      ctx.assertEquals("https", xForwardedProto);
    });
  }

  @Test
  public void testEnabledFalseDoesNotAddHeaders(TestContext ctx) {
    ProxyOptions options = new ProxyOptions()
      .setForwardedHeadersOptions(new ForwardedHeadersOptions()
        .setEnabled(false)  // Explicitly disabled
        .setForwardFor(true)
        .setForwardProto(true)
      );

    runForwardedHeadersTest(ctx, options, null, null, req -> {
      ctx.assertNull(req.getHeader("X-Forwarded-For"));
      ctx.assertNull(req.getHeader("X-Forwarded-Proto"));
    });
  }

  private void runForwardedHeadersTest(TestContext ctx, ProxyOptions options, HttpServerOptions serverOpts, Consumer<HttpClientRequest> requestCustomizer, Handler<HttpServerRequest> backendHandler) {
    Async latch = ctx.async();

    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
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
      .onComplete(ctx.asyncAssertSuccess(body -> {
        ctx.assertEquals("OK", body.toString());
        latch.complete();
      }));

    latch.await();
  }
}
