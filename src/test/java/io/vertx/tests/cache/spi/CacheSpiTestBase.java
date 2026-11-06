package io.vertx.tests.cache.spi;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.httpproxy.cache.CacheOptions;
import io.vertx.httpproxy.spi.cache.Cache;
import io.vertx.httpproxy.spi.cache.Resource;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTest;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@VertxTest
public abstract class CacheSpiTestBase {

  protected Cache cache;
  protected CacheOptions cacheOptions;
  protected Vertx vertx;
  private Instant now;

  private final String URL1 = "http://k1.exmaple.com";
  private final String URL2 = "http://k2.exmaple.com";
  private final String URL3 = "http://k3.exmaple.com";

  @BeforeEach
  public void setUp(Vertx vertx) {
    this.vertx = vertx;
    now = Instant.now();
    cacheOptions = new CacheOptions().setMaxSize(2);
  }

  private Resource generateResource(String absoluteURI, long maxAge) {
    return new Resource(
      absoluteURI,
      200,
      "OK",
      MultiMap.caseInsensitiveMultiMap(),
      now.toEpochMilli(),
      maxAge
    );
  }

  @Test
  public void testAddAndGet(VertxTestContext ctx) {
    Checkpoint latch = ctx.checkpoint();
    cache.put(URL1, generateResource(URL1, 100L)).compose(v -> {
      return cache.get(URL1);
    }).onComplete(ctx.succeeding(res1 -> {
      assertNotNull(res1);
      assertEquals(res1.getMaxAge(), 100L);
    })).compose(v -> cache.get(URL2))
      .onComplete(ctx.succeeding(res2 -> {
        assertNull(res2);
      })).compose(v -> cache.put(URL1, generateResource(URL1, 200L)))
      .compose(v -> cache.get(URL1)).onComplete(ctx.succeeding(res1 -> {
        assertNotNull(res1);
        assertEquals(res1.getMaxAge(), 200L);
        latch.flag();
      }));
  }

  @Test
  public void testRemove(VertxTestContext ctx) {
    Checkpoint latch = ctx.checkpoint();
    cache.put(URL1, generateResource(URL1, 100L))
      .compose(v -> cache.put(URL2, generateResource(URL2, 200L)))
      .compose(v -> cache.remove(URL1))
      .onSuccess(removed -> {
        cache.get(URL1).onSuccess(resp1 -> {
          cache.get(URL2).onComplete(ctx.succeeding(resp2 -> {
            assertNull(resp1);
            assertEquals(resp2.getMaxAge(), 200L);
          })).compose(x -> cache.remove(URL1)).onComplete(ctx.succeeding(r -> latch.flag()));
        });
      });
  }

  @Test
  public void testMaxSize(VertxTestContext ctx) {
    Checkpoint latch = ctx.checkpoint();
    cache.put(URL1, generateResource(URL1, 100L))
      .compose(v -> cache.put(URL2, generateResource(URL2, 200L)))
      .compose(v -> cache.put(URL3, generateResource(URL3, 300L)))
      .onSuccess(stored -> {
        cache.get(URL1).onSuccess(resp1 -> {
          cache.get(URL2).onSuccess(resp2 -> {
            cache.get(URL3).onSuccess(resp3 -> {
              int cnt = 0;
              if (resp1 != null) cnt++;
              if (resp2 != null) cnt++;
              if (resp3 != null) cnt++;
              int found = cnt;
              assertEquals(found, 2);
              latch.flag();
            });
          });
        });
      });
  }
}
