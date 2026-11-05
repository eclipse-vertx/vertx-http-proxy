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

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.httpproxy.impl.ParseUtils;
import org.junit.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CacheRevalidationTest extends CacheWireMockTestBase {

  @Test
  public void testIfModifiedSinceRespondsNotModified(TestContext ctx) throws Exception {
    long now = System.currentTimeMillis();
    stubFor(get(urlEqualTo("/img.jpg")).inScenario("s").whenScenarioStateIs(STARTED)
        .willReturn(
            aResponse()
                .withStatus(200)
                .withHeader("Cache-Control", "public")
                .withHeader("ETag", "tag0")
                .withHeader("Date", ParseUtils.formatHttpDate(Instant.ofEpochMilli(now)))
                .withHeader("Last-Modified", ParseUtils.formatHttpDate(Instant.ofEpochMilli(now).minus(5000, ChronoUnit.MILLIS)))
                .withHeader("Expires", ParseUtils.formatHttpDate(Instant.ofEpochMilli(now).plus(5000, ChronoUnit.MILLIS)))
                .withBody("content")));
    startProxy(SocketAddress.inetSocketAddress(8081, "localhost"));
    Async latch = ctx.async();
    client.request(HttpMethod.GET, 8080, "localhost", "/img.jpg").compose(req1 ->
      req1.send().compose(resp1 -> {
        ctx.verify(v -> {
          assertEquals(200, resp1.statusCode());
        });
        return resp1.body();
      })
    ).onComplete(ctx.asyncAssertSuccess(body1 -> {
      ctx.verify(v -> {
        assertEquals("content", body1.toString());
      });
      vertx.setTimer(3000, id -> {
        client.request(HttpMethod.GET, 8080, "localhost", "/img.jpg")
            .compose(req2 -> req2
                .putHeader(HttpHeaders.IF_MODIFIED_SINCE, ParseUtils.formatHttpDate(Instant.ofEpochMilli(now).minus(5000, ChronoUnit.MILLIS)))
                .send()
                .compose(resp2 -> {
              ctx.verify(v -> {
                assertEquals(304, resp2.statusCode());
              });
              return resp2.body();
            })).onComplete(ctx.asyncAssertSuccess(body2 -> {
          ctx.verify(v -> {
            assertEquals("", body2.toString());
          });
          latch.complete();
        }));
      });
    }));
    latch.awaitSuccess(10000);
    // Commented out since the initial import of the project (2020-11-30), never revisited. Probable reason (from
    // reading the test, not verified by running it): the assertions expect two backend exchanges, the second being a
    // conditional request answered with 304, but this test only stubs a 200 and its second client request is
    // answered by the proxy from its cache, without reaching the backend.
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
  public void testPublicInvalidClientMaxAgeRevalidationReplacedByNewVersion(TestContext ctx) throws Exception {
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
    startProxy(SocketAddress.inetSocketAddress(8081, "localhost"));
    Async latch = ctx.async();
    client.request(HttpMethod.GET, 8080, "localhost", "/img.jpg")
        .compose(req -> req
            .send()
            .compose(resp1 -> {
              ctx.verify(v -> {
                assertEquals(200, resp1.statusCode());
              });
              return resp1.body();
            }))
        .onComplete(ctx.asyncAssertSuccess(body1 -> {
          ctx.verify(v -> {
            assertEquals("content", body1.toString());
          });
          vertx.setTimer(3000, id -> {
            client.request(HttpMethod.GET, 8080, "localhost", "/img.jpg")
                .compose(req -> req
                    .putHeader(HttpHeaders.CACHE_CONTROL, "max-age=1")
                    .putHeader(HttpHeaders.CONTENT_ENCODING, "identity")
                    .send().compose(resp2 -> {
                      ctx.verify(v -> {
                        assertEquals(200, resp2.statusCode());
                      });
                      return resp2.body();
                    })).onComplete(ctx.asyncAssertSuccess(body2 -> {
              ctx.verify(v -> {
                assertEquals("content2", body2.toString());
              });
//              assertNotEquals(resp1.getHeader(HttpHeaders.DATE), resp2.getHeader(HttpHeaders.DATE));
              latch.complete();
            }));
          });
        }));

    latch.awaitSuccess(10000);
    // Commented out since the initial import of the project (2020-11-30), never revisited. Probable reason (from
    // reading the stubs, not verified by running it): the assertions expect the revalidation to be answered with 304,
    // but this test's stub answers the conditional request with 200, a new ETag and a new body.
/*
    ServeEvent event1 = getAllServeEvents().get(1);
    assertNull(event1.getRequest().getHeader("If-None-Match"));
    assertEquals(200, event1.getResponse().getStatus());
    ServeEvent event0 = getAllServeEvents().get(0);
    assertEquals("tag0", event0.getRequest().getHeader("If-None-Match"));
    assertEquals(304, event0.getResponse().getStatus());
*/
  }
}
