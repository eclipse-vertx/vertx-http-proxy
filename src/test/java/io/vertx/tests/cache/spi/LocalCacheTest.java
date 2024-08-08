package io.vertx.tests.cache.spi;

import io.vertx.httpproxy.impl.CacheImpl;

public class LocalCacheTest extends CacheSpiTestBase {

  @Override
  public void setUp() {
    super.setUp();
    cache = new CacheImpl(cacheOptions);
  }

}
