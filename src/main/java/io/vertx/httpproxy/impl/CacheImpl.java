package io.vertx.httpproxy.impl;

import io.vertx.httpproxy.cache.CacheOptions;
import io.vertx.httpproxy.spi.cache.Cache;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Simplistic implementation.
 */
public class CacheImpl<K, V> extends LinkedHashMap<K, V> implements Cache<K, V> {

  private final int maxSize;

  public CacheImpl(CacheOptions options) {
    this.maxSize = options.getMaxSize();
  }

  protected boolean removeEldestEntry(Map.Entry eldest) {
    return size() > maxSize;
  }
}
