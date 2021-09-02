package io.vertx.httpproxy.cache;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonObject;
import io.vertx.httpproxy.impl.CacheImpl;
import io.vertx.httpproxy.spi.cache.Cache;

/**
 * Cache options.
 */
@DataObject(generateConverter = true)
public class CacheOptions {

  public static final int DEFAULT_MAX_SIZE = 1000;

  private int maxSize = DEFAULT_MAX_SIZE;

  public CacheOptions() {
  }

  public CacheOptions(JsonObject json) {
    CacheOptionsConverter.fromJson(json, this);
  }

  /**
   * @return the max number of entries the cache can hold
   */
  public int getMaxSize() {
    return maxSize;
  }

  /**
   * Set the max number of entries the cache can hold.
   *
   * @param maxSize the max size
   * @return a reference to this, so the API can be used fluently
   */
  public CacheOptions setMaxSize(int maxSize) {
    Arguments.require(maxSize > 0, "Max size must be > 0");
    this.maxSize = maxSize;
    return this;
  }

  public <K, V> Cache<K, V> newCache() {
    return new CacheImpl<>(this);
  }

  @Override
  public String toString() {
    return toJson().toString();
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    CacheOptionsConverter.toJson(this, json);
    return json;
  }
}
