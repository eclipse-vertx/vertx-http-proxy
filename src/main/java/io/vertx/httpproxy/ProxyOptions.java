package io.vertx.httpproxy;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import io.vertx.httpproxy.cache.CacheOptions;

/**
 * Proxy options.
 */
@DataObject(generateConverter = true)
public class ProxyOptions {

  /**
   * Enable WebSocket support : {@code true}
   */
  public static final boolean DEFAULT_SUPPORT_WEBSOCKET = true;

  private CacheOptions cacheOptions;
  private boolean supportWebSocket;

  public ProxyOptions(JsonObject json) {
    ProxyOptionsConverter.fromJson(json, this);
  }

  public ProxyOptions() {
    supportWebSocket = DEFAULT_SUPPORT_WEBSOCKET;
  }

  /**
   * @return the cache options
   */
  public CacheOptions getCacheOptions() {
    return cacheOptions;
  }

  /**
   * Set the cache options that configures the proxy.
   *
   * {@code null} cache options disables caching, by default cache is disabled.
   *
   * @param cacheOptions the cache options
   * @return a reference to this, so the API can be used fluently
   */
  public ProxyOptions setCacheOptions(CacheOptions cacheOptions) {
    this.cacheOptions = cacheOptions;
    return this;
  }

  /**
   * @return whether WebSocket are supported
   */
  public boolean getSupportWebSocket() {
    return supportWebSocket;
  }

  /**
   * Set whether WebSocket are supported.
   *
   * @param supportWebSocket {@code true} to enable WebSocket support, {@code false} otherwise
   * @return a reference to this, so the API can be used fluently
   */
  public ProxyOptions setSupportWebSocket(boolean supportWebSocket) {
    this.supportWebSocket = supportWebSocket;
    return this;
  }

  @Override
  public String toString() {
    return toJson().toString();
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    ProxyOptionsConverter.toJson(this, json);
    return json;
  }
}
