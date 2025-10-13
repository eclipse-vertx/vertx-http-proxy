package io.vertx.httpproxy;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;
import io.vertx.httpproxy.cache.CacheOptions;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static io.vertx.core.http.HttpHeaders.CONNECTION;
import static io.vertx.core.http.HttpHeaders.KEEP_ALIVE;
import static io.vertx.core.http.HttpHeaders.PROXY_AUTHENTICATE;
import static io.vertx.core.http.HttpHeaders.PROXY_AUTHORIZATION;
import static io.vertx.core.http.HttpHeaders.TRANSFER_ENCODING;
import static io.vertx.core.http.HttpHeaders.UPGRADE;

/**
 * Proxy options.
 *
 */
@DataObject
@JsonGen(publicConverter = false)
public class ProxyOptions {

  /**
   * Enable WebSocket support : {@code true}
   */
  public static final boolean DEFAULT_SUPPORT_WEBSOCKET = true;

  public static final Set<String> DEFAULT_HOP_BY_HOP_HEADERS = new HashSet<>(Arrays.asList(
    CONNECTION.toString(),
    KEEP_ALIVE.toString(),
    PROXY_AUTHENTICATE.toString(),
    PROXY_AUTHORIZATION.toString(),
    TRANSFER_ENCODING.toString(),
    UPGRADE.toString(),
    "te",
    "trailer"
  ));

  private CacheOptions cacheOptions;
  private boolean supportWebSocket;
  private Set<String> customHopHeaders;

  public ProxyOptions(JsonObject json) {
    ProxyOptionsConverter.fromJson(json, this);
  }

  public ProxyOptions() {
    supportWebSocket = DEFAULT_SUPPORT_WEBSOCKET;
    customHopHeaders = DEFAULT_HOP_BY_HOP_HEADERS;
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

  /**
   * @return custom hop-by-hop headers
   */
  public Set<String> getCustomHopHeaders() {
    return customHopHeaders;
  }

  /**
   * Sets custom hop-by-hop headers, overriding the default {@code DEFAULT_HOP_BY_HOP_HEADERS} headers.
   * <p>
   * <b>Warning:</b> Please read the following specification before removing or modifying
   * any hop-by-hop header:
   * <a href="https://datatracker.ietf.org/doc/html/rfc2616#section-13.5.1">
   * RFC 2616, Section 13.5.1
   * </a>
   * </p>
   *
   * @param customHopHeaders the list of hop-by-hop headers to set
   * @return a reference to this, so the API can be used fluently
   */
  public ProxyOptions setCustomHopHeaders(Set<String> customHopHeaders){
    this.customHopHeaders = new HashSet<String>(customHopHeaders);
    return this;
  }

  /**
   * Add a custom hop-by-hop header
   *
   * @param customHopHeader a custom hop-by-hop header
   * @return a reference to this, so the API can be used fluently
   */
  public ProxyOptions addCustomHopHeader(String customHopHeader) {
    this.customHopHeaders.add(customHopHeader);
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
