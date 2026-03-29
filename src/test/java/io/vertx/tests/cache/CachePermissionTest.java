package io.vertx.tests.cache;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.httpproxy.impl.ParseUtils;
import org.junit.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public class CachePermissionTest extends CacheTestBase {

  private HttpClient client;
  private AtomicInteger backendResult;

  @Override
  public void setUp() {
    super.setUp();
    backendResult = new AtomicInteger(INIT);
    client = vertx.createHttpClient();
  }

  @Test
  public void testUnsafeMethods(TestContext ctx) {
    Async latch = ctx.async();
    AtomicInteger hits = new AtomicInteger(0);
    SocketAddress backend = startHttpBackend(ctx, 8081, req -> {
      hits.incrementAndGet();
      Instant now = Instant.now();
      req.response()
        .putHeader(HttpHeaders.ETAG, ETAG_0)
        .putHeader(HttpHeaders.CACHE_CONTROL, "max-age=999")
        .putHeader(HttpHeaders.DATE, ParseUtils.formatHttpDate(now))
        .end();
    });
    startProxy(backend);
    client.request(HttpMethod.GET, 8080, "localhost", "/").compose(req -> req.send()).compose(resp1 -> {
      return client.request(HttpMethod.POST, 8080, "localhost", "/").compose(req -> req.send());
    }).compose(resp2 -> {
      return client.request(HttpMethod.GET, 8080, "localhost", "/").compose(req -> req.send());
    }).onComplete(ctx.asyncAssertSuccess(resp3 -> {
      ctx.assertEquals(hits.get(), 3);
      latch.complete();
    }));
  }

  @Test
  public void testAuth(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = etagBackend(ctx, backendResult,
      MultiMap.caseInsensitiveMultiMap()
        .add(HttpHeaders.CACHE_CONTROL, "max-age=999"));
    startProxy(backend);
    call(client, MultiMap.caseInsensitiveMultiMap().add(HttpHeaders.AUTHORIZATION, "Bearer 123"))
      .compose(r1 -> call(client))
      .onComplete(ctx.asyncAssertSuccess(r2 -> {
        ctx.assertEquals(backendResult.get(), NORMAL);
        latch.complete();
      }));
  }

  @Test
  public void testAuthWithPublic(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = etagBackend(ctx, backendResult,
      MultiMap.caseInsensitiveMultiMap()
        .add(HttpHeaders.CACHE_CONTROL, "public, max-age=999"));
    startProxy(backend);
    call(client, MultiMap.caseInsensitiveMultiMap().add(HttpHeaders.AUTHORIZATION, "Bearer 123"))
      .compose(r1 -> call(client))
      .onComplete(ctx.asyncAssertSuccess(r2 -> {
        ctx.assertEquals(backendResult.get(), NOT_CALLED);
        latch.complete();
      }));
  }

  @Test
  public void testStrictSemantics(TestContext ctx) {
    Async latch = ctx.async();
    SocketAddress backend = etagBackend(ctx, backendResult,
      MultiMap.caseInsensitiveMultiMap()
        .add(HttpHeaders.CACHE_CONTROL, "public, private"));
    startProxy(backend);
    call(client).compose(r1 -> call(client))
      .onComplete(ctx.asyncAssertSuccess(r2 -> {
        ctx.assertEquals(backendResult.get(), NORMAL);
        latch.complete();
      }));
  }


}
