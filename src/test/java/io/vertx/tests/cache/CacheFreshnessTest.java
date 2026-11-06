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

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.SocketAddress;
import io.vertx.httpproxy.impl.ParseUtils;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Freshness tests, run with both ways of declaring the lifetime of a response: the {@code Expires} header
 * and the {@code Cache-Control: max-age} directive.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@ParameterizedClass(name = "{0}")
@ValueSource(strings = {"Expires", "max-age"})
public class CacheFreshnessTest extends CacheTestBase {

  @Parameter
  String freshnessHeader;

  private boolean useMaxAge;
  private AtomicInteger hits = new AtomicInteger();
  private HttpClient client;

  @BeforeEach
  @Override
  public void setUp(Vertx vertx) {
    super.setUp(vertx);
    useMaxAge = freshnessHeader.equals("max-age");
    hits.set(0);
    client = vertx.createHttpClient();
  }

  private void setCacheControl(MultiMap headers, long now, long delaySeconds) {
    if (useMaxAge) {
      headers.set(HttpHeaders.CACHE_CONTROL, "public, max-age=" + delaySeconds);
    } else {
      headers.set(HttpHeaders.CACHE_CONTROL, "public");
      headers.set(HttpHeaders.EXPIRES, ParseUtils.formatHttpDate(Instant.now().plus(delaySeconds, ChronoUnit.SECONDS)));
    }
  }

  @Test
  public void testPublicGet(VertxTestContext ctx) throws Exception {
    testPublic(ctx, HttpMethod.GET);
  }

  @Test
  public void testPublicHead(VertxTestContext ctx) throws Exception {
    testPublic(ctx, HttpMethod.HEAD);
  }

  private void testPublic(VertxTestContext ctx, HttpMethod method) throws Exception {
    Checkpoint latch = ctx.checkpoint();
    testPublic(ctx, responseHeaders -> {
      vertx.setTimer(2000, id -> {
        client.request(method, 8080, "localhost", "/")
            .compose(req2 -> req2.send().compose(resp2 -> {
              assertEquals(200, resp2.statusCode());
              assertEquals(responseHeaders.get(HttpHeaders.DATE), resp2.getHeader(HttpHeaders.DATE));
              assertEquals(1, hits.get());
              return resp2.body();
            })).onComplete(ctx.succeeding(body2 -> {
          if (method == HttpMethod.HEAD) {
            assertEquals("", body2.toString());
          } else {
            assertEquals("content", body2.toString());
          }
          latch.flag();
        }));
      });
    });
  }

  @Test
  public void testPublicExpiration(VertxTestContext ctx) throws Exception {
    Checkpoint latch = ctx.checkpoint();
    testPublic(ctx, responseHeaders -> {
      vertx.setTimer(6000, id -> {
        client.request(HttpMethod.GET, 8080, "localhost", "/")
            .compose(req2 ->
                req2.send().compose(resp2 -> {
                  assertEquals(200, resp2.statusCode());
                  assertEquals(2, hits.get());
                  assertNotEquals(responseHeaders.get(HttpHeaders.DATE), resp2.getHeader(HttpHeaders.DATE));
                  return resp2.body();
                })
            ).onComplete(ctx.succeeding(body2 -> {
          assertEquals("content", body2.toString());
          latch.flag();
        }));
      });
    });
  }

  @Test
  public void testPublicValidClientMaxAge(VertxTestContext ctx) throws Exception {
    Checkpoint latch = ctx.checkpoint();
    testPublic(ctx, responseHeaders -> {
      vertx.setTimer(1000, id -> {
        client.request(HttpMethod.GET, 8080, "localhost", "/").compose(req2 ->
            req2.putHeader(HttpHeaders.CACHE_CONTROL, "max-age=2")
                .send().compose(resp2 -> {
              assertEquals(200, resp2.statusCode());
              assertEquals(1, hits.get());
              assertEquals(responseHeaders.get(HttpHeaders.DATE), resp2.getHeader(HttpHeaders.DATE));
              return resp2.body();
            })
        ).onComplete(ctx.succeeding(body2 -> {
          assertEquals("content", body2.toString());
          latch.flag();
        }));
      });
    });
  }

  @Test
  public void testPublicInvalidClientMaxAge(VertxTestContext ctx) throws Exception {
    Checkpoint latch = ctx.checkpoint();
    testPublic(ctx, responseHeaders -> {
      vertx.setTimer(1100, id -> {
        client.request(HttpMethod.GET, 8080, "localhost", "/").compose(req2 ->
          req2.putHeader(HttpHeaders.CACHE_CONTROL, "max-age=1")
              .send()
              .compose(resp2 -> {
                assertEquals(200, resp2.statusCode());
                assertEquals(2, hits.get());
                assertNotEquals(responseHeaders.get(HttpHeaders.DATE), resp2.getHeader(HttpHeaders.DATE));
                return resp2.body();
              })
        ).onComplete(ctx.succeeding(body2 -> {
          assertEquals("content", body2.toString());
          latch.flag();
        }));
      });
    });
  }

  private void testPublic(VertxTestContext ctx, Handler<MultiMap> respHandler) throws Exception {
    SocketAddress backend = startHttpBackend(8081, req -> {
      hits.incrementAndGet();
      assertEquals(HttpMethod.GET, req.method());
      Instant now = Instant.now();
      setCacheControl(req.response().headers(), now.toEpochMilli(), 5);
      req.response()
          .putHeader(HttpHeaders.LAST_MODIFIED, ParseUtils.formatHttpDate(now))
          .putHeader(HttpHeaders.DATE, ParseUtils.formatHttpDate(now))
          .end("content");
    });
    startProxy(backend);
    client.request(HttpMethod.GET, 8080, "localhost", "/").compose(req ->
        req.send().compose(resp -> {
          assertEquals(200, resp.statusCode());
          return resp.body().onSuccess(body -> respHandler.handle(resp.headers()));
        })).onComplete(ctx.succeeding(body -> {
      assertEquals("content", body.toString());
    }));
  }

  @Test
  public void testPublicInvalidClientMaxAgeRevalidationNotModified(VertxTestContext ctx) throws Exception {
    testPublicInvalidClientMaxAge(ctx, 5);
  }

  // Commented out since the initial import of the project (2020-11-30), never revisited. Probable reason (from reading
  // the code, not verified by running it): with maxAge = 3 the backend answers 200 "content2" instead of 304 to the
  // revalidation sent after 3 s, while the test expects the body "content" and 2 hits.
/*
  @Test
  public void testPublicInvalidClientMaxAgeOverwrite(VertxTestContext ctx) throws Exception {
    testPublicInvalidClientMaxAge(ctx, 3);
  }
*/

  private void testPublicInvalidClientMaxAge(VertxTestContext ctx, long maxAge) throws Exception {
    Checkpoint latch = ctx.checkpoint();
    long now = System.currentTimeMillis();
    SocketAddress backend = startHttpBackend(8081, req -> {
      assertEquals(HttpMethod.GET, req.method());
      setCacheControl(req.response().headers(), now, 5);
      switch (hits.getAndIncrement()) {
        case 0:
          assertEquals(null, req.getHeader(HttpHeaders.ETAG));
          req.response()
              .putHeader(HttpHeaders.LAST_MODIFIED, ParseUtils.formatHttpDate(Instant.ofEpochMilli(now)))
              .putHeader(HttpHeaders.DATE, ParseUtils.formatHttpDate(Instant.ofEpochMilli(now)))
              .putHeader(HttpHeaders.ETAG, "" + now)
              .end("content");
          break;
        case 1:
          assertEquals("" + now, req.getHeader(HttpHeaders.IF_NONE_MATCH));
          if (System.currentTimeMillis() < now + maxAge * 1000) {
            req.response()
                .setStatusCode(304)
                .putHeader(HttpHeaders.DATE, ParseUtils.formatHttpDate(Instant.ofEpochMilli(System.currentTimeMillis())))
                .putHeader(HttpHeaders.ETAG, "" + now)
                .end();
          } else {
            req.response()
                .putHeader(HttpHeaders.DATE, ParseUtils.formatHttpDate(Instant.ofEpochMilli(System.currentTimeMillis())))
                .putHeader(HttpHeaders.ETAG, "" + now + "2")
                .end("content2");
          }
          break;
        default:
          fail();
      }
    });
    startProxy(backend);
    client.request(HttpMethod.GET, 8080, "localhost", "/").compose(req1 ->
        req1.send().compose(resp1 -> {
          assertEquals(200, resp1.statusCode());
          return resp1.body();
        })).onComplete(ctx.succeeding(body1 -> {
      assertEquals("content", body1.toString());
      vertx.setTimer(3000, id -> {
        client.request(HttpMethod.GET, 8080, "localhost", "/").compose(req2 ->
            req2.putHeader(HttpHeaders.CACHE_CONTROL, "max-age=1")
                .send()
                .compose(resp2 -> {
                  assertEquals(200, resp2.statusCode());
                return resp2.body();
            })).onComplete(ctx.succeeding(body2 -> {
          assertEquals("content", body2.toString());
          assertEquals(2, hits.get());
//              assertNotEquals(resp1.getHeader(HttpHeaders.DATE), resp2.getHeader(HttpHeaders.DATE));
          latch.flag();
        }));
      });
    }));
  }

}
