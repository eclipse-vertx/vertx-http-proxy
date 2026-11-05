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
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunnerWithParametersFactory;
import io.vertx.httpproxy.impl.ParseUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Freshness tests, run with both ways of declaring the lifetime of a response: the {@code Expires} header
 * and the {@code Cache-Control: max-age} directive.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(VertxUnitRunnerWithParametersFactory.class)
public class CacheFreshnessTest extends CacheTestBase {

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
      {"Expires"},
      {"max-age"},
    });
  }

  private final boolean useMaxAge;
  private AtomicInteger hits = new AtomicInteger();
  private HttpClient client;

  public CacheFreshnessTest(String freshnessHeader) {
    this.useMaxAge = freshnessHeader.equals("max-age");
  }

  @Override
  public void setUp() {
    super.setUp();
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
  public void testPublicGet(TestContext ctx) throws Exception {
    testPublic(ctx, HttpMethod.GET);
  }

  @Test
  public void testPublicHead(TestContext ctx) throws Exception {
    testPublic(ctx, HttpMethod.HEAD);
  }

  private void testPublic(TestContext ctx, HttpMethod method) throws Exception {
    Async latch = ctx.async();
    testPublic(ctx, responseHeaders -> {
      vertx.setTimer(2000, id -> {
        client.request(method, 8080, "localhost", "/")
            .compose(req2 -> req2.send().compose(resp2 -> {
              ctx.assertEquals(200, resp2.statusCode());
              ctx.assertEquals(responseHeaders.get(HttpHeaders.DATE), resp2.getHeader(HttpHeaders.DATE));
              ctx.assertEquals(1, hits.get());
              return resp2.body();
            })).onComplete(ctx.asyncAssertSuccess(body2 -> {
          if (method == HttpMethod.HEAD) {
            ctx.assertEquals("", body2.toString());
          } else {
            ctx.assertEquals("content", body2.toString());
          }
          latch.complete();
        }));
      });
    });
  }

  @Test
  public void testPublicExpiration(TestContext ctx) throws Exception {
    Async latch = ctx.async();
    testPublic(ctx, responseHeaders -> {
      vertx.setTimer(6000, id -> {
        client.request(HttpMethod.GET, 8080, "localhost", "/")
            .compose(req2 ->
                req2.send().compose(resp2 -> {
                  ctx.assertEquals(200, resp2.statusCode());
                  ctx.assertEquals(2, hits.get());
                  ctx.assertNotEquals(responseHeaders.get(HttpHeaders.DATE), resp2.getHeader(HttpHeaders.DATE));
                  return resp2.body();
                })
            ).onComplete(ctx.asyncAssertSuccess(body2 -> {
          ctx.assertEquals("content", body2.toString());
          latch.complete();
        }));
      });
    });
  }

  @Test
  public void testPublicValidClientMaxAge(TestContext ctx) throws Exception {
    Async latch = ctx.async();
    testPublic(ctx, responseHeaders -> {
      vertx.setTimer(1000, id -> {
        client.request(HttpMethod.GET, 8080, "localhost", "/").compose(req2 ->
            req2.putHeader(HttpHeaders.CACHE_CONTROL, "max-age=2")
                .send().compose(resp2 -> {
              ctx.assertEquals(200, resp2.statusCode());
              ctx.assertEquals(1, hits.get());
              ctx.assertEquals(responseHeaders.get(HttpHeaders.DATE), resp2.getHeader(HttpHeaders.DATE));
              return resp2.body();
            })
        ).onComplete(ctx.asyncAssertSuccess(body2 -> {
          ctx.assertEquals("content", body2.toString());
          latch.complete();
        }));
      });
    });
  }

  @Test
  public void testPublicInvalidClientMaxAge(TestContext ctx) throws Exception {
    Async latch = ctx.async();
    testPublic(ctx, responseHeaders -> {
      vertx.setTimer(1100, id -> {
        client.request(HttpMethod.GET, 8080, "localhost", "/").compose(req2 ->
          req2.putHeader(HttpHeaders.CACHE_CONTROL, "max-age=1")
              .send()
              .compose(resp2 -> {
                ctx.assertEquals(200, resp2.statusCode());
                ctx.assertEquals(2, hits.get());
                ctx.assertNotEquals(responseHeaders.get(HttpHeaders.DATE), resp2.getHeader(HttpHeaders.DATE));
                return resp2.body();
              })
        ).onComplete(ctx.asyncAssertSuccess(body2 -> {
          ctx.assertEquals("content", body2.toString());
          latch.complete();
        }));
      });
    });
  }

  private void testPublic(TestContext ctx, Handler<MultiMap> respHandler) throws Exception {
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      hits.incrementAndGet();
      ctx.assertEquals(HttpMethod.GET, req.method());
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
          ctx.assertEquals(200, resp.statusCode());
          return resp.body().onSuccess(body -> respHandler.handle(resp.headers()));
        })).onComplete(ctx.asyncAssertSuccess(body -> {
      ctx.assertEquals("content", body.toString());
    }));
  }

  @Test
  public void testPublicInvalidClientMaxAgeRevalidationNotModified(TestContext ctx) throws Exception {
    testPublicInvalidClientMaxAge(ctx, 5);
  }

  // Commented out since the initial import of the project (2020-11-30), never revisited. Probable reason (from reading
  // the code, not verified by running it): with maxAge = 3 the backend answers 200 "content2" instead of 304 to the
  // revalidation sent after 3 s, while the test expects the body "content" and 2 hits.
/*
  @Test
  public void testPublicInvalidClientMaxAgeOverwrite(TestContext ctx) throws Exception {
    testPublicInvalidClientMaxAge(ctx, 3);
  }
*/

  private void testPublicInvalidClientMaxAge(TestContext ctx, long maxAge) throws Exception {
    Async latch = ctx.async();
    long now = System.currentTimeMillis();
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      ctx.assertEquals(HttpMethod.GET, req.method());
      setCacheControl(req.response().headers(), now, 5);
      switch (hits.getAndIncrement()) {
        case 0:
          ctx.assertEquals(null, req.getHeader(HttpHeaders.ETAG));
          req.response()
              .putHeader(HttpHeaders.LAST_MODIFIED, ParseUtils.formatHttpDate(Instant.ofEpochMilli(now)))
              .putHeader(HttpHeaders.DATE, ParseUtils.formatHttpDate(Instant.ofEpochMilli(now)))
              .putHeader(HttpHeaders.ETAG, "" + now)
              .end("content");
          break;
        case 1:
          ctx.assertEquals("" + now, req.getHeader(HttpHeaders.IF_NONE_MATCH));
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
          ctx.fail();
      }
    });
    startProxy(backend);
    client.request(HttpMethod.GET, 8080, "localhost", "/").compose(req1 ->
        req1.send().compose(resp1 -> {
          ctx.assertEquals(200, resp1.statusCode());
          return resp1.body();
        })).onComplete(ctx.asyncAssertSuccess(body1 -> {
      ctx.assertEquals("content", body1.toString());
      vertx.setTimer(3000, id -> {
        client.request(HttpMethod.GET, 8080, "localhost", "/").compose(req2 ->
            req2.putHeader(HttpHeaders.CACHE_CONTROL, "max-age=1")
                .send()
                .compose(resp2 -> {
                  ctx.assertEquals(200, resp2.statusCode());
                return resp2.body();
            })).onComplete(ctx.asyncAssertSuccess(body2 -> {
          ctx.assertEquals("content", body2.toString());
          ctx.assertEquals(2, hits.get());
//              ctx.assertNotEquals(resp1.getHeader(HttpHeaders.DATE), resp2.getHeader(HttpHeaders.DATE));
          latch.complete();
        }));
      });
    }));
  }

}
