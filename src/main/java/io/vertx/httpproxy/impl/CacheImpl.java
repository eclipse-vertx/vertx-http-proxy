package io.vertx.httpproxy.impl;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.httpproxy.cache.CacheOptions;
import io.vertx.httpproxy.spi.cache.Cache;
import io.vertx.httpproxy.spi.cache.Resource;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Simplistic implementation.
 */
public class CacheImpl implements Cache {

  private final int maxSize;
  private final LinkedHashMap<String, Resource> data;

  public CacheImpl(CacheOptions options) {
    this.maxSize = options.getMaxSize();
    this.data = new LinkedHashMap<>() {
      @Override
      protected boolean removeEldestEntry(Map.Entry<String, Resource> eldest) {
        return size() > maxSize;
      }
    };
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
  public Future<Void> close() {
    return Future.succeededFuture();
  }
}
