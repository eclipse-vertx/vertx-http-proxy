/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.httpproxy;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;
import io.vertx.httpproxy.cache.CacheOptions;

/**
 * Proxy options.
 */
@DataObject
@JsonGen(publicConverter = false)
public class ProxyOptions {

  /**
   * Enable WebSocket support : {@code true}
   */
  public static final boolean DEFAULT_SUPPORT_WEBSOCKET = true;

  private CacheOptions cacheOptions;
  private boolean supportWebSocket;
  private ForwardedHeadersOptions forwardedHeadersOptions;

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

  /**
   * @return the forwarded headers options
   */
  public ForwardedHeadersOptions getForwardedHeadersOptions() {
    return forwardedHeadersOptions;
  }

  /**
   * Set the forwarded headers options that configures how the proxy handles
   * X-Forwarded-* or RFC 7239 Forwarded headers.
   * <p>
   * {@code null} forwarded headers options disables forwarded headers support,
   * by default forwarded headers support is disabled.
   *
   * @param forwardedHeadersOptions the forwarded headers options
   * @return a reference to this, so the API can be used fluently
   */
  public ProxyOptions setForwardedHeadersOptions(ForwardedHeadersOptions forwardedHeadersOptions) {
    this.forwardedHeadersOptions = forwardedHeadersOptions;
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
