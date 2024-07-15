package io.vertx.httpproxy.impl;

import io.vertx.core.Future;
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
  private final Map<String, Resource> data;
  private final LinkedList<String> records;

  public CacheImpl(CacheOptions options) {
    this.maxSize = options.getMaxSize();
    this.data = new HashMap<>();
    this.records = new LinkedList<>();
  }


  @Override
  public Future<Void> put(String key, Resource value) {
    while (records.size() >= maxSize) {
      String toRemove = records.removeLast();
      data.remove(toRemove);
    }

    data.put(key, value);
    records.addFirst(key);
    return Future.succeededFuture();
  }

  @Override
  public Future<Resource> get(String key) {
    return Future.succeededFuture(data.get(key));
  }

  @Override
  public Future<Void> remove(String key) {
    records.remove(key);
    data.remove(key);
    return Future.succeededFuture();
  }
}
