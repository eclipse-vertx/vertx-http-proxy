package io.vertx.httpproxy;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.unit.TestContext;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CacheMaxAgeTest extends CacheExpiresTest {

  @Override
  protected void setCacheControl(MultiMap headers, long now, long delaySeconds) {
    headers.set(HttpHeaders.CACHE_CONTROL, "public, max-age=" + delaySeconds);
  }

  @Override
  public void testPublicGet(TestContext ctx) throws Exception {
    super.testPublicGet(ctx);
  }

  @Override
  public void testPublicHead(TestContext ctx) throws Exception {
    super.testPublicHead(ctx);
  }

  @Override
  public void testPublicExpiration(TestContext ctx) throws Exception {
    super.testPublicExpiration(ctx);
  }
}
