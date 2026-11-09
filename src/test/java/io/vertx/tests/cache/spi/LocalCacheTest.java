package io.vertx.tests.cache.spi;

import io.vertx.core.Vertx;
import io.vertx.httpproxy.impl.CacheImpl;
import org.junit.jupiter.api.BeforeEach;

public class LocalCacheTest extends CacheSpiTestBase {

  @BeforeEach
  @Override
  public void setUp(Vertx vertx) {
    super.setUp(vertx);
    cache = new CacheImpl(cacheOptions);
  }

}
