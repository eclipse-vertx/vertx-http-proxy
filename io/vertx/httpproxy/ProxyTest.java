package io.vertx.httpproxy;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ProxyTest extends ProxyTestBase {

  @Test
  public void testRoundRobinSelector(TestContext ctx) {
    int numRequests = 10;
    SocketAddress[] backends = new SocketAddress[3];
    for (int i = 0;i < backends.length;i++) {
      int value = i;
      backends[i] = startHttpBackend(ctx, 8081 + value, req -> req.response().end("" + value));
    }
    AtomicInteger count = new AtomicInteger();
    startProxy(req -> Future.succeededFuture(backends[count.getAndIncrement() % backends.length]));
    HttpClient client = vertx.createHttpClient();
    Map<String, AtomicInteger> result = Collections.synchronizedMap(new HashMap<>());
    Async latch = ctx.async();
    for (int i = 0;i < backends.length * numRequests;i++) {
      client
          .request(HttpMethod.GET, 8080, "localhost", "/")
          .compose(req -> req
              .send()
              .compose(HttpClientResponse::body)
          ).onSuccess(buff -> {
        result.computeIfAbsent(buff.toString(), k -> new AtomicInteger()).getAndIncrement();
        synchronized (result) {
          int total = result.values().stream().reduce(0, (a, b) -> a + b.get(), (a, b) -> a + b);
          if (total == backends.length * numRequests) {
            for (int j = 0;j < backends.length;j++) {
              AtomicInteger val = result.remove("" + j);
              ctx.assertEquals(numRequests, val.get());
            }
            ctx.assertEquals(result, Collections.emptyMap());
            latch.complete();
          }
        }
      });
    }
  }
}
