package io.vertx.httpproxy;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class ProxyOptions {

  private CacheOptions cacheOptions;

  public ProxyOptions(JsonObject json) {
    throw new UnsupportedOperationException();
  }

  public ProxyOptions() {
  }

  public CacheOptions getCacheOptions() {
    return cacheOptions;
  }

  public ProxyOptions setCacheOptions(CacheOptions cacheOptions) {
    this.cacheOptions = cacheOptions;
    return this;
  }

  @Override
  public String toString() {
    return "ProxyOptions[cacheOptions=" + cacheOptions + "]";
  }
}
