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
package io.vertx.httpproxy.cache;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.impl.SocketAddressImpl;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.httpproxy.impl.ParseUtils;
import org.junit.Rule;
import org.junit.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CacheExpires2Test extends CacheTestBase {

  private AtomicInteger hits = new AtomicInteger();
  private HttpClient client;

  @Rule
  public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().port(8081));

  @Override
  public void setUp() {
    super.setUp();
    hits.set(0);
    client = vertx.createHttpClient(new HttpClientOptions().setKeepAlive(true));
  }

  protected void setCacheControl(MultiMap headers, long now, long delaySeconds) {
    headers.set(HttpHeaders.CACHE_CONTROL, "public");
    headers.set(HttpHeaders.EXPIRES, ParseUtils.formatHttpDate(Instant.now().plus(delaySeconds * 1000, ChronoUnit.MILLIS)));
  }

  @Test
  public void testPublicInvalidClientMaxAgeRevalidation(TestContext ctx) throws Exception {
    stubFor(get(urlEqualTo("/img.jpg")).inScenario("s")
        .willReturn(
            aResponse()
                .withStatus(200)
                .withHeader("Cache-Control", "public")
                .withHeader("ETag", "tag0")
                .withHeader("Date", ParseUtils.formatHttpDate(Instant.now()))
                .withHeader("Expires", ParseUtils.formatHttpDate(Instant.now().plus(5000, ChronoUnit.MILLIS)))
                .withBody("content")));
    stubFor(get(urlEqualTo("/img.jpg")).withHeader("If-None-Match", equalTo("tag0")).inScenario("s")
        .willReturn(
            aResponse()
                .withStatus(200)
                .withHeader("Cache-Control", "public")
                .withHeader("Etag", "tag1")
                .withHeader("Date", ParseUtils.formatHttpDate(Instant.now()))
                .withHeader("Expires", ParseUtils.formatHttpDate(Instant.now().plus(5000, ChronoUnit.MILLIS)))
                .withBody("content2")));
    startProxy(new SocketAddressImpl(8081, "localhost"));
    Async latch = ctx.async();
    client.request(HttpMethod.GET, 8080, "localhost", "/img.jpg")
        .compose(req -> req
            .send()
            .compose(resp1 -> {
              ctx.assertEquals(200, resp1.statusCode());
              return resp1.body();
            }))
        .onComplete(ctx.asyncAssertSuccess(body1 -> {
          ctx.assertEquals("content", body1.toString());
          vertx.setTimer(3000, id -> {
            client.request(HttpMethod.GET, 8080, "localhost", "/img.jpg")
                .compose(req -> req
                    .putHeader(HttpHeaders.CACHE_CONTROL, "max-age=1")
                    .putHeader(HttpHeaders.CONTENT_ENCODING, "identity")
                    .send().compose(resp2 -> {
                      ctx.assertEquals(200, resp2.statusCode());
                      return resp2.body();
                    })).onComplete(ctx.asyncAssertSuccess(body2 -> {
              ctx.assertEquals("content2", body2.toString());
//              ctx.assertNotEquals(resp1.getHeader(HttpHeaders.DATE), resp2.getHeader(HttpHeaders.DATE));
              latch.complete();
            }));
          });
        }));

    latch.awaitSuccess(10000);
/*
    ServeEvent event1 = getAllServeEvents().get(1);
    assertNull(event1.getRequest().getHeader("If-None-Match"));
    assertEquals(200, event1.getResponse().getStatus());
    ServeEvent event0 = getAllServeEvents().get(0);
    assertEquals("tag0", event0.getRequest().getHeader("If-None-Match"));
    assertEquals(304, event0.getResponse().getStatus());
*/
  }

  @Test
  public void testUncacheableGetInvalidatesEntryOnOk(TestContext ctx) throws Exception {
    testUncacheableRequestInvalidatesEntryOnOk(ctx, HttpMethod.GET);
  }

  @Test
  public void testUncacheableHeadInvalidatesEntryOnOk(TestContext ctx) throws Exception {
    testUncacheableRequestInvalidatesEntryOnOk(ctx, HttpMethod.HEAD);
  }

  private void testUncacheableRequestInvalidatesEntryOnOk(TestContext ctx, HttpMethod method) throws Exception {
    stubFor(get(urlEqualTo("/img.jpg")).inScenario("s").whenScenarioStateIs(STARTED)
        .willReturn(
            aResponse()
                .withStatus(200)
                .withHeader("Cache-Control", "public")
                .withHeader("ETag", "tag0")
                .withHeader("Date", ParseUtils.formatHttpDate(Instant.now()))
                .withHeader("Expires", ParseUtils.formatHttpDate(Instant.now().plus(5000, ChronoUnit.MILLIS)))
                .withBody("content"))
        .willSetStateTo("abc"));
    stubFor(get(urlEqualTo("/img.jpg")).inScenario("s").whenScenarioStateIs("abc")
        .willReturn(
            aResponse()
                .withStatus(200)
                .withHeader("Cache-Control", "public")
                .withHeader("Etag", "tag1")
                .withHeader("Date", ParseUtils.formatHttpDate(Instant.now()))
                .withHeader("Expires", ParseUtils.formatHttpDate(Instant.now().plus(5000, ChronoUnit.MILLIS)))
                .withBody("content2")));
    stubFor(head(urlEqualTo("/img.jpg")).inScenario("s").whenScenarioStateIs("abc")
        .willReturn(
            aResponse()
                .withStatus(200)
                .withHeader("Cache-Control", "public")
                .withHeader("Etag", "tag1")
                .withHeader("Date", ParseUtils.formatHttpDate(Instant.now()))
                .withHeader("Expires", ParseUtils.formatHttpDate(Instant.now().plus(5000, ChronoUnit.MILLIS)))));
    startProxy(new SocketAddressImpl(8081, "localhost"));
    Async latch = ctx.async();
    client.request(HttpMethod.GET, 8080, "localhost", "/img.jpg")
        .compose(req1 -> req1
            .send()
            .compose(resp1 -> {
              ctx.assertEquals(200, resp1.statusCode());
              return resp1.body();
            }))
        .onComplete(ctx.asyncAssertSuccess(body1 -> {
          ctx.assertEquals("content", body1.toString());
          vertx.setTimer(3000, id -> {
            client.request(method, 8080, "localhost", "/img.jpg")
                .compose(req2 ->
                    req2.putHeader(HttpHeaders.CACHE_CONTROL, "max-age=1")
                        .send().compose(resp2 -> {
                      ctx.assertEquals(200, resp2.statusCode());
                      return resp2.body();
                    }))
                .compose(body2 -> {
                  ctx.assertEquals(method == HttpMethod.GET ? "content2" : "", body2.toString());
                  return client.request(HttpMethod.GET, 8080, "localhost", "/img.jpg");
                })
                .compose(req3 -> req3
                    .send()
                    .compose(resp3 -> {
                      ctx.assertEquals(200, resp3.statusCode());
                      return resp3.body();
                    })).onComplete(ctx.asyncAssertSuccess(body3 -> {
              ctx.assertEquals("content2", body3.toString());
              latch.complete();
            }));
          });
        }));
    latch.awaitSuccess(10000);
/*
    ServeEvent event1 = getAllServeEvents().get(1);
    assertNull(event1.getRequest().getHeader("If-None-Match"));
    assertEquals(200, event1.getResponse().getStatus());
    ServeEvent event0 = getAllServeEvents().get(0);
    assertEquals("tag0", event0.getRequest().getHeader("If-None-Match"));
    assertEquals(304, event0.getResponse().getStatus());
*/
  }

  @Test
  public void testUncacheableHeadRevalidatesEntryOnOk(TestContext ctx) throws Exception {
    testUncacheableHeadRevalidatesEntry(ctx, 200);
  }

  @Test
  public void testUncacheableHeadRevalidatesEntryOnNotModified(TestContext ctx) throws Exception {
    testUncacheableHeadRevalidatesEntry(ctx, 304);
  }

  private void testUncacheableHeadRevalidatesEntry(TestContext ctx, int status) throws Exception {
    stubFor(get(urlEqualTo("/img.jpg"))
        .willReturn(
            aResponse()
                .withStatus(200)
                .withHeader("Cache-Control", "public")
                .withHeader("ETag", "tag0")
                .withHeader("Date", ParseUtils.formatHttpDate(Instant.now()))
                .withHeader("Expires", ParseUtils.formatHttpDate(Instant.now().plus(5000, ChronoUnit.MILLIS)))
                .withBody("content")));
    stubFor(head(urlEqualTo("/img.jpg"))
        .willReturn(
            aResponse()
                .withStatus(status)
                .withHeader("Cache-Control", "public")
                .withHeader("ETag", "tag0")
                .withHeader("Date", ParseUtils.formatHttpDate(Instant.now()))
                .withHeader("Expires", ParseUtils.formatHttpDate(Instant.now().plus(5000, ChronoUnit.MILLIS)))));
    startProxy(new SocketAddressImpl(8081, "localhost"));
    Async latch = ctx.async();
    client.request(HttpMethod.GET, 8080, "localhost", "/img.jpg")
        .compose(req1 -> req1.send()
            .compose(resp1 -> {
              ctx.assertEquals(200, resp1.statusCode());
              return resp1.body();
            }))
        .onComplete(ctx.asyncAssertSuccess(body1 -> {
          ctx.assertEquals("content", body1.toString());
          vertx.setTimer(3000, id -> {
            client.request(HttpMethod.HEAD, 8080, "localhost", "/img.jpg")
                .compose(req2 -> req2
                    .putHeader(HttpHeaders.CACHE_CONTROL, "max-age=1")
                    .send().compose(resp2 -> {
                      ctx.assertEquals(200, resp2.statusCode());
                      return resp2.body();
                    })).onComplete(ctx.asyncAssertSuccess(body2 -> {
              ctx.assertEquals("", body2.toString());
              latch.complete();
            }));
          });
        }));
    latch.awaitSuccess(10000);
    ServeEvent event1 = getAllServeEvents().get(1);
    assertNull(event1.getRequest().getHeader("If-None-Match"));
    assertEquals(200, event1.getResponse().getStatus());
    ServeEvent event0 = getAllServeEvents().get(0);
    assertEquals("tag0", event0.getRequest().getHeader("If-None-Match"));
    assertEquals(status, event0.getResponse().getStatus());
  }

  @Test
  public void testHeadDoesNotPopulateCache(TestContext ctx) throws Exception {
    stubFor(get(urlEqualTo("/img.jpg"))
        .willReturn(
            aResponse()
                .withStatus(200)
                .withHeader("Cache-Control", "public")
                .withHeader("ETag", "tag0")
                .withHeader("Date", ParseUtils.formatHttpDate(Instant.now()))
                .withHeader("Expires", ParseUtils.formatHttpDate(Instant.now().plus(5000, ChronoUnit.MILLIS)))
                .withBody("content")));
    stubFor(head(urlEqualTo("/img.jpg"))
        .willReturn(
            aResponse()
                .withStatus(200)
                .withHeader("Cache-Control", "public")
                .withHeader("ETag", "tag0")
                .withHeader("Date", ParseUtils.formatHttpDate(Instant.now()))
                .withHeader("Expires", ParseUtils.formatHttpDate(Instant.now().plus(5000, ChronoUnit.MILLIS)))));
    startProxy(new SocketAddressImpl(8081, "localhost"));
    Async latch = ctx.async();
    client.request(HttpMethod.HEAD, 8080, "localhost", "/img.jpg")
        .compose(req1 -> req1.send().compose(resp1 -> {
          ctx.assertEquals(200, resp1.statusCode());
          return resp1.body();
        }))
        .compose(body1 -> {
          ctx.assertEquals("", body1.toString());
          return client.request(HttpMethod.GET, 8080, "localhost", "/img.jpg");
        })
        .compose(req2 -> req2.send().compose(resp2 -> {
          ctx.assertEquals(200, resp2.statusCode());
          return resp2.body();
        }))
        .onComplete(ctx.asyncAssertSuccess(body2 -> {
          ctx.assertEquals("content", body2.toString());
          latch.complete();
        }));
    latch.awaitSuccess(10000);
    ServeEvent event1 = getAllServeEvents().get(1);
    assertNull(event1.getRequest().getHeader("If-None-Match"));
    assertEquals(200, event1.getResponse().getStatus());
    ServeEvent event0 = getAllServeEvents().get(0);
    assertEquals(null, event0.getRequest().getHeader("If-None-Match"));
    assertEquals(200, event0.getResponse().getStatus());
  }
}
