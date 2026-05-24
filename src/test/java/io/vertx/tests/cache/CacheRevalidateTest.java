package io.vertx.tests.cache;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.httpproxy.impl.ParseUtils;
import org.junit.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CacheRevalidateTest extends CacheTestBase {

  private Instant lastModified;
  private HttpClient client;
  private AtomicInteger backendResult;

  @Override
  public void setUp() {
    super.setUp();
    lastModified = Instant.now().minus(1, ChronoUnit.DAYS);
    backendResult = new AtomicInteger(INIT);
    client = vertx.createHttpClient();
  }

  @Test
  public void clientCacheValidETag(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = etagBackend(ctx, backendResult, MultiMap.caseInsensitiveMultiMap()
      .add(HttpHeaders.CACHE_CONTROL, "max-age=3600"));
    startProxy(backend);
    call(client, MultiMap.caseInsensitiveMultiMap().add(HttpHeaders.IF_NONE_MATCH, ETAG_0)).onComplete(ctx.asyncAssertSuccess(resp -> {
      ctx.assertEquals(resp.statusCode(), 304);
      ctx.assertEquals(resp.headers().get(HttpHeaders.ETAG), ETAG_0);
      latch.complete();
    }));
  }

  @Test
  public void clientCacheInvalidETag(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = etagBackend(ctx, backendResult, MultiMap.caseInsensitiveMultiMap()
      .add(HttpHeaders.CACHE_CONTROL, "max-age=3600"));
    startProxy(backend);
    call(client, MultiMap.caseInsensitiveMultiMap().add(HttpHeaders.IF_NONE_MATCH, ETAG_1)).onComplete(ctx.asyncAssertSuccess(resp -> {
      ctx.assertEquals(resp.statusCode(), 200);
      latch.complete();
    }));
  }

  protected SocketAddress lastModifiedBackend(TestContext ctx, AtomicInteger backendResult, MultiMap additionalHeaders) {
    return startHttpBackend(ctx, 8081, req -> {
      Instant now = Instant.now();
      String ifModifiedSince = req.headers().get(HttpHeaders.IF_MODIFIED_SINCE);
      if (ifModifiedSince != null && ParseUtils.parseHeaderDate(ifModifiedSince).isAfter(lastModified)) {
        backendResult.set(REVALIDATE_SUCCESS);
        req.response().setStatusCode(304);
      } else {
        if (ifModifiedSince != null) {
          backendResult.set(REVALIDATE_FAIL);
        } else {
          if (backendResult.get() == INIT) {
            backendResult.set(NOT_CALLED);
          } else {
            backendResult.set(NORMAL);
          }
        }
      }
      req.response().headers().setAll(additionalHeaders);
      req.response()
        .putHeader(HttpHeaders.LAST_MODIFIED, ParseUtils.formatHttpDate(lastModified))
        .putHeader(HttpHeaders.DATE, ParseUtils.formatHttpDate(now))
        .end();
    });
  }

  @Test
  public void clientCacheValidModifiedSince(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = lastModifiedBackend(ctx, backendResult, MultiMap.caseInsensitiveMultiMap()
      .add(HttpHeaders.CACHE_CONTROL, "max-age=3600"));
    startProxy(backend);
    call(client).compose(s -> call(client, MultiMap.caseInsensitiveMultiMap().add(HttpHeaders.IF_MODIFIED_SINCE, ParseUtils.formatHttpDate(lastModified)))
      .onComplete(ctx.asyncAssertSuccess(resp -> {
        ctx.assertEquals(resp.statusCode(), 304);
        ctx.assertNotNull(resp.headers().get(HttpHeaders.LAST_MODIFIED));
        latch.complete();
      })));
  }

  @Test
  public void clientCacheInvalidModifiedSince(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = lastModifiedBackend(ctx, backendResult, MultiMap.caseInsensitiveMultiMap()
      .add(HttpHeaders.CACHE_CONTROL, "max-age=3600"));
    startProxy(backend);
    call(client).compose(s -> call(client, MultiMap.caseInsensitiveMultiMap().add(HttpHeaders.IF_MODIFIED_SINCE, ParseUtils.formatHttpDate(lastModified.minus(2, ChronoUnit.DAYS))))
      .onComplete(ctx.asyncAssertSuccess(resp -> {
        ctx.assertEquals(resp.statusCode(), 200);
        latch.complete();
      })));
  }

  protected SocketAddress newEtagBackend(TestContext ctx, AtomicInteger backendResult, MultiMap additionalHeaders) {
    return startHttpBackend(ctx, 8081, req -> {
      Instant now = Instant.now();
      String ifNoneMatch = req.headers().get(HttpHeaders.IF_NONE_MATCH);
      if (ifNoneMatch != null) {
        backendResult.set(REVALIDATE_FAIL);
      } else {
        if (backendResult.get() == INIT) {
          backendResult.set(NOT_CALLED);
        } else {
          backendResult.set(NORMAL);
        }
      }
      req.response().headers().setAll(additionalHeaders);
      req.response()
        .putHeader(HttpHeaders.ETAG, "\"" + System.currentTimeMillis() + "\"")
        .putHeader(HttpHeaders.DATE, ParseUtils.formatHttpDate(now))
        .end();
    });
  }

  @Test
  public void serverCacheInvalidETag(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = newEtagBackend(ctx, backendResult, MultiMap.caseInsensitiveMultiMap()
      .add(HttpHeaders.CACHE_CONTROL, "no-cache, max-age=3600"));
    startProxy(backend);
    call(client).onSuccess(resp1 -> {
      vertx.setTimer(500, t -> {
        call(client).onComplete(ctx.asyncAssertSuccess(resp2 -> {
          ctx.assertEquals(backendResult.get(), REVALIDATE_FAIL);
          String oldETag = resp1.getHeader(HttpHeaders.ETAG);
          String newETag = resp2.getHeader(HttpHeaders.ETAG);
          ctx.assertNotNull(oldETag);
          ctx.assertNotNull(newETag);
          ctx.assertNotEquals(oldETag, newETag);
          latch.complete();
        }));
      });
    });
  }

}
