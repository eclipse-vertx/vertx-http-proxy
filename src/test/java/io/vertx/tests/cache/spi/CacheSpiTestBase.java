package io.vertx.tests.cache.spi;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.httpproxy.cache.CacheOptions;
import io.vertx.httpproxy.spi.cache.Cache;
import io.vertx.httpproxy.spi.cache.Resource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;

@RunWith(VertxUnitRunner.class)
public abstract class CacheSpiTestBase {

  protected Cache cache;
  protected CacheOptions cacheOptions;
  protected Vertx vertx;
  private Instant now;

  private final String URL1 = "http://k1.exmaple.com";
  private final String URL2 = "http://k2.exmaple.com";
  private final String URL3 = "http://k3.exmaple.com";

  @Before
  public void setUp() {
    vertx = Vertx.vertx();
    now = Instant.now();
    cacheOptions = new CacheOptions().setMaxSize(2);
  }

  @After
  public void tearDown(TestContext context) {
    vertx.close().onComplete(context.asyncAssertSuccess());
  }

  private Resource generateResource(String absoluteURI, long maxAge) {
    return new Resource(
      absoluteURI,
      MultiMap.caseInsensitiveMultiMap(),
      200,
      "OK",
      MultiMap.caseInsensitiveMultiMap(),
      now.toEpochMilli(),
      maxAge
    );
  }

  @Test
  public void testAddAndGet(TestContext ctx) {
    Async latch = ctx.async();
    cache.put(URL1, generateResource(URL1, 100L)).compose(v -> {
      return cache.get(URL1);
    }).onComplete(ctx.asyncAssertSuccess(res1 -> {
      ctx.assertNotNull(res1);
      ctx.assertEquals(res1.getMaxAge(), 100L);
    })).compose(v -> cache.get(URL2))
      .onComplete(ctx.asyncAssertSuccess(res2 -> {
        ctx.assertNull(res2);
      })).compose(v -> cache.put(URL1, generateResource(URL1, 200L)))
      .compose(v -> cache.get(URL1)).onComplete(ctx.asyncAssertSuccess(res1 -> {
        ctx.assertNotNull(res1);
        ctx.assertEquals(res1.getMaxAge(), 200L);
        latch.complete();
      }));
  }

  @Test
  public void testRemove(TestContext ctx) {
    Async latch = ctx.async();
    cache.put(URL1, generateResource(URL1, 100L))
      .compose(v -> cache.put(URL2, generateResource(URL2, 200L)))
      .compose(v -> cache.remove(URL1))
      .onSuccess(v -> {
        cache.get(URL1).onSuccess(resp1 -> {
          cache.get(URL2).onComplete(ctx.asyncAssertSuccess(resp2 -> {
            ctx.assertNull(resp1);
            ctx.assertEquals(resp2.getMaxAge(), 200L);
          })).compose(x -> cache.remove(URL1)).onComplete(ctx.asyncAssertSuccess(r -> latch.complete()));
        });
      });
  }

  @Test
  public void testMaxSize(TestContext ctx) {
    Async latch = ctx.async();
    cache.put(URL1, generateResource(URL1, 100L))
      .compose(v -> cache.put(URL2, generateResource(URL2, 200L)))
      .compose(v -> cache.put(URL3, generateResource(URL3, 300L)))
      .onSuccess(v -> {
        cache.get(URL1).onSuccess(resp1 -> {
          cache.get(URL2).onSuccess(resp2 -> {
            cache.get(URL3).onSuccess(resp3 -> {
              int cnt = 0;
              if (resp1 != null) cnt++;
              if (resp2 != null) cnt++;
              if (resp3 != null) cnt++;
              ctx.assertEquals(cnt, 2);
              latch.complete();
            });
          });
        });
      });
  }
}
