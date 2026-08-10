package io.vertx.httpproxy.impl;

import io.vertx.core.Future;
import io.vertx.core.internal.Closeable;
import io.vertx.httpproxy.cache.CacheOptions;
import io.vertx.httpproxy.spi.cache.Cache;
import io.vertx.httpproxy.spi.cache.Resource;

import java.time.Duration;
import java.util.*;

/**
 * Simplistic implementation.
 */
public class CacheImpl implements Cache, Closeable {

  private final int maxSize;
  private final Map<String, Resource> data;

  public CacheImpl(CacheOptions options) {
    this.maxSize = options.getMaxSize();
    this.data = Collections.synchronizedMap(new LinkedHashMap<>() {
      @Override
      protected boolean removeEldestEntry(Map.Entry<String, Resource> eldest) {
        return size() > maxSize;
      }
    });
  }


  @Override
  public Future<Void> put(String key, Resource value) {
    data.put(key, value);
    return Future.succeededFuture();
  }

  @Override
  public Future<Resource> get(String key) {
    return Future.succeededFuture(data.get(key));
  }

  @Override
  public Future<Void> remove(String key) {
    data.remove(key);
    return Future.succeededFuture();
  }

  @Override
  public Future<Void> shutdown(Duration timeout) {
    return Future.succeededFuture();
  }
}
