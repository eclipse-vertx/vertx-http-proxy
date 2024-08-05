package io.vertx.tests.cache.spi;

import io.vertx.core.http.HttpClientOptions;
import io.vertx.ext.unit.TestContext;
import io.vertx.httpproxy.cache.CacheOptions;
import io.vertx.httpproxy.impl.CacheImpl;

public class LocalCacheTest extends CacheSpiBase {

  @Override
  public void setUp() {
    super.setUp();
    cache = new CacheImpl(cacheOptions);
  }

}
