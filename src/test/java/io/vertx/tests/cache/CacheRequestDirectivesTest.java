package io.vertx.tests.cache;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.*;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class CacheRequestDirectivesTest extends CacheTestBase {

  private HttpClient client;
  private AtomicInteger backendResult;

  @Override
  public void setUp() {
    super.setUp();
    backendResult = new AtomicInteger(INIT);
    client = vertx.createHttpClient();
  }

  private void testWithRequestHeaders(TestContext ctx, MultiMap reqHeaders, int expectedBackendStatus) {
    Async latch = ctx.async();
    SocketAddress backend = etagBackend(ctx, backendResult, MultiMap.caseInsensitiveMultiMap().add(
      HttpHeaders.CACHE_CONTROL, "max-age=60"
    ));
    startProxy(backend);
    call(client).onComplete(v -> {
      vertx.setTimer(1500, t -> {
        call(client, reqHeaders)
          .onComplete(ctx.asyncAssertSuccess(resp -> {
            ctx.assertEquals(backendResult.get(), expectedBackendStatus);
            latch.complete();
          }));
      });
    });
  }

  @Test
  public void testNoDirectives(TestContext ctx) {
    MultiMap additionalHeader = MultiMap.caseInsensitiveMultiMap();
    testWithRequestHeaders(ctx, additionalHeader, NOT_CALLED);
  }

  @Test
  public void testMaxAgeNotExceed(TestContext ctx) {
    MultiMap additionalHeader = MultiMap.caseInsensitiveMultiMap()
      .add(HttpHeaders.CACHE_CONTROL, "max-age=6");
    testWithRequestHeaders(ctx, additionalHeader, NOT_CALLED);
  }

  @Test
  public void testMaxAgeExceed(TestContext ctx) {
    MultiMap additionalHeader = MultiMap.caseInsensitiveMultiMap()
      .add(HttpHeaders.CACHE_CONTROL, "max-age=1");
    testWithRequestHeaders(ctx, additionalHeader, REVALIDATE_SUCCESS);
  }

  @Test
  public void testMaxStale(TestContext ctx) {
    MultiMap additionalHeader = MultiMap.caseInsensitiveMultiMap()
      .add(HttpHeaders.CACHE_CONTROL, "max-age=1, max-stale=999");
    testWithRequestHeaders(ctx, additionalHeader, NOT_CALLED);
  }

  @Test
  public void testMinFresh(TestContext ctx) {
    MultiMap additionalHeader = MultiMap.caseInsensitiveMultiMap()
      .add(HttpHeaders.CACHE_CONTROL, "max-age=6, min-fresh=5");
    testWithRequestHeaders(ctx, additionalHeader, REVALIDATE_SUCCESS);
  }

  @Test
  public void testNoCache(TestContext ctx) {
    MultiMap additionalHeader = MultiMap.caseInsensitiveMultiMap()
      .add(HttpHeaders.CACHE_CONTROL, "no-cache");
    testWithRequestHeaders(ctx, additionalHeader, REVALIDATE_SUCCESS);
  }

  @Test
  public void testNoStore(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = etagBackend(ctx, backendResult, MultiMap.caseInsensitiveMultiMap().add(
      HttpHeaders.CACHE_CONTROL, "max-age=60"
    ));
    startProxy(backend);
    call(client, MultiMap.caseInsensitiveMultiMap().add(
      HttpHeaders.CACHE_CONTROL, "no-store"
    )).onComplete(v -> {
      call(client)
        .onComplete(ctx.asyncAssertSuccess(resp -> {
          ctx.assertEquals(backendResult.get(), NORMAL);
          latch.complete();
        }));
    });
  }

  @Test
  public void testOnlyIfCached(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = etagBackend(ctx, backendResult, MultiMap.caseInsensitiveMultiMap().add(
      HttpHeaders.CACHE_CONTROL, "max-age=60"
    ));
    startProxy(backend);
    call(client, MultiMap.caseInsensitiveMultiMap().add(
      HttpHeaders.CACHE_CONTROL, "only-if-cached"
    )).onComplete(ctx.asyncAssertSuccess(resp -> {
      ctx.assertEquals(resp.statusCode(), 504);
      latch.complete();
    }));
  }
}
