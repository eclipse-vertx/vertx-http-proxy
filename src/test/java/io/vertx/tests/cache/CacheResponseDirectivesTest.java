package io.vertx.tests.cache;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class CacheResponseDirectivesTest extends CacheTestBase {

  private HttpClient client;
  private AtomicInteger backendResult;

  @Override
  public void setUp() {
    super.setUp();
    backendResult = new AtomicInteger(INIT);
    client = vertx.createHttpClient();
  }

  private void testWithResponseHeaders(TestContext ctx, MultiMap respHeaders, int delay, int expectedBackendStatus) {
    Async latch = ctx.async();
    SocketAddress backend = etagBackend(ctx, backendResult, respHeaders);
    startProxy(backend);
    call(client).onComplete(v -> {
      vertx.setTimer(delay, t -> {
        call(client).onComplete(ctx.asyncAssertSuccess(resp -> {
          ctx.assertEquals(backendResult.get(), expectedBackendStatus);
          latch.complete();
        }));
      });
    });
  }

  @Test
  public void testMaxAgeNotExceed(TestContext ctx) {
    MultiMap additionalHeader = MultiMap.caseInsensitiveMultiMap()
      .add(HttpHeaders.CACHE_CONTROL, "max-age=6");
    testWithResponseHeaders(ctx, additionalHeader, 1500, NOT_CALLED);
  }

  @Test
  public void testMaxAgeExceed(TestContext ctx) {
    MultiMap additionalHeader = MultiMap.caseInsensitiveMultiMap()
      .add(HttpHeaders.CACHE_CONTROL, "max-age=1");
    testWithResponseHeaders(ctx, additionalHeader, 1500, REVALIDATE_SUCCESS);
  }

  @Test
  public void testNoCache(TestContext ctx) {
    MultiMap additionalHeader = MultiMap.caseInsensitiveMultiMap()
      .add(HttpHeaders.CACHE_CONTROL, "max-age=999, no-cache");
    testWithResponseHeaders(ctx, additionalHeader, 100, REVALIDATE_SUCCESS);
  }

  @Test
  public void testNoStore(TestContext ctx) {
    MultiMap additionalHeader = MultiMap.caseInsensitiveMultiMap()
      .add(HttpHeaders.CACHE_CONTROL, "no-store");
    testWithResponseHeaders(ctx, additionalHeader, 100, NORMAL);
  }

  @Test
  public void testPrivate(TestContext ctx) {
    MultiMap additionalHeader = MultiMap.caseInsensitiveMultiMap()
      .add(HttpHeaders.CACHE_CONTROL, "max-age=999, private");
    testWithResponseHeaders(ctx, additionalHeader, 100, NORMAL);
  }

  @Test
  public void testPublic(TestContext ctx) {
    MultiMap additionalHeader = MultiMap.caseInsensitiveMultiMap()
      .add(HttpHeaders.CACHE_CONTROL, "public, max-age=0");
    testWithResponseHeaders(ctx, additionalHeader, 100, REVALIDATE_SUCCESS);
  }

  @Test
  public void testSMaxAgeNotExceed(TestContext ctx) {
    MultiMap additionalHeader = MultiMap.caseInsensitiveMultiMap()
      .add(HttpHeaders.CACHE_CONTROL, "max-age=1, s-maxage=999");
    testWithResponseHeaders(ctx, additionalHeader, 1500, NOT_CALLED);
  }

  @Test
  public void testSMaxAgeExceed(TestContext ctx) {
    MultiMap additionalHeader = MultiMap.caseInsensitiveMultiMap()
      .add(HttpHeaders.CACHE_CONTROL, "max-age=999, s-maxage=1");
    testWithResponseHeaders(ctx, additionalHeader, 1500, REVALIDATE_SUCCESS);
  }

}
