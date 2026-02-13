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
import java.util.concurrent.atomic.AtomicInteger;

public class CacheVaryTest extends CacheTestBase {
  private HttpClient client;
  private AtomicInteger backendResult;

  private static final String X_CUSTOM_HEADER = "X-Custom-Header";

  @Override
  public void setUp() {
    super.setUp();
    backendResult = new AtomicInteger(INIT);
    client = vertx.createHttpClient();
  }

  protected SocketAddress varyBackend(TestContext ctx, AtomicInteger backendResult, MultiMap additionalHeaders, String vary) {
    return startHttpBackend(ctx, 8081, req -> {
      Instant now = Instant.now();
      String custom = req.headers().get(X_CUSTOM_HEADER);
      String output = custom;
      String ifNoneMatch = req.headers().get(HttpHeaders.IF_NONE_MATCH);

      if (ifNoneMatch != null && ifNoneMatch.equals(ETAG_0)) {
        backendResult.set(REVALIDATE_SUCCESS);
        output = "";
        req.response().setStatusCode(304);
      } else {
        if (ifNoneMatch != null) {
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
        .putHeader(HttpHeaders.ETAG, ETAG_0)
        .putHeader(HttpHeaders.VARY, vary)
        .putHeader(HttpHeaders.DATE, ParseUtils.formatHttpDate(now))
        .end(output);
    });
  }

  @Test
  public void testCustomVaryMatch(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = varyBackend(ctx, backendResult,
      MultiMap.caseInsensitiveMultiMap()
        .add(HttpHeaders.CACHE_CONTROL, "max-age=999"), X_CUSTOM_HEADER);
    startProxy(backend);
    call(client, MultiMap.caseInsensitiveMultiMap().add(X_CUSTOM_HEADER, "A"))
      .compose(r1 -> call(client, MultiMap.caseInsensitiveMultiMap().add(X_CUSTOM_HEADER, "A")))
      .onComplete(ctx.asyncAssertSuccess(r2 -> {
        r2.body().onSuccess(buffer -> {
          ctx.assertEquals(buffer.toString(), "A");
          ctx.assertEquals(backendResult.get(), NOT_CALLED);
          latch.complete();
        });
      }));
  }

  @Test
  public void testCustomVaryNotMatch(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = varyBackend(ctx, backendResult,
      MultiMap.caseInsensitiveMultiMap()
        .add(HttpHeaders.CACHE_CONTROL, "max-age=999"), X_CUSTOM_HEADER);
    startProxy(backend);
    call(client, MultiMap.caseInsensitiveMultiMap().add(X_CUSTOM_HEADER, "A"))
      .compose(r1 -> call(client, MultiMap.caseInsensitiveMultiMap().add(X_CUSTOM_HEADER, "B")))
      .onComplete(ctx.asyncAssertSuccess(r2 -> {
        r2.body().onSuccess(buffer -> {
          ctx.assertEquals(buffer.toString(), "B");
          ctx.assertEquals(backendResult.get(), NORMAL);
          latch.complete();
        });
      }));
  }

  @Test
  public void testCustomVaryWildcard(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = varyBackend(ctx, backendResult,
      MultiMap.caseInsensitiveMultiMap()
        .add(HttpHeaders.CACHE_CONTROL, "max-age=999"), "*");
    startProxy(backend);
    call(client, MultiMap.caseInsensitiveMultiMap().add(X_CUSTOM_HEADER, "A"))
      .compose(r1 -> call(client, MultiMap.caseInsensitiveMultiMap().add(X_CUSTOM_HEADER, "B")))
      .onComplete(ctx.asyncAssertSuccess(r2 -> {
        r2.body().onSuccess(buffer -> {
          ctx.assertEquals(buffer.toString(), "B");
          ctx.assertEquals(backendResult.get(), NORMAL);
          latch.complete();
        });
      }));
  }


}
