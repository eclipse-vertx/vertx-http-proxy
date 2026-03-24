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

/**
 * Options for configuring forwarded headers support in the proxy.
 * <p>
 * These headers are used to preserve information about the original client request when proxying through one or more intermediaries.
 * <p>
 * The proxy can add either {@code X-Forwarded-*} headers (de-facto standard) or the RFC 7239 {@code Forwarded} header.
 */
@DataObject
@JsonGen(publicConverter = false)
public class ForwardedHeadersOptions {

  /**
   * Default enabled = {@code false}
   */
  public static final boolean DEFAULT_ENABLED = false;

  /**
   * Default forward for = {@code true}
   */
  public static final boolean DEFAULT_FORWARD_FOR = true;

  /**
   * Default forward proto = {@code true}
   */
  public static final boolean DEFAULT_FORWARD_PROTO = true;

  /**
   * Default forward host = {@code true}
   */
  public static final boolean DEFAULT_FORWARD_HOST = true;

  /**
   * Default forward port = {@code true}
   */
  public static final boolean DEFAULT_FORWARD_PORT = true;

  /**
   * Default use RFC 7239 = {@code false}
   */
  public static final boolean DEFAULT_USE_RFC7239 = false;

  private boolean enabled = DEFAULT_ENABLED;
  private boolean forwardFor = DEFAULT_FORWARD_FOR;
  private boolean forwardProto = DEFAULT_FORWARD_PROTO;
  private boolean forwardHost = DEFAULT_FORWARD_HOST;
  private boolean forwardPort = DEFAULT_FORWARD_PORT;
  private boolean useRfc7239 = DEFAULT_USE_RFC7239;

  /**
   * Default constructor.
   */
  public ForwardedHeadersOptions() {
  }

  /**
   * Copy constructor.
   *
   * @param other the options to copy
   */
  public ForwardedHeadersOptions(ForwardedHeadersOptions other) {
    this.enabled = other.isEnabled();
    this.forwardFor = other.isForwardFor();
    this.forwardProto = other.isForwardProto();
    this.forwardHost = other.isForwardHost();
    this.forwardPort = other.isForwardPort();
    this.useRfc7239 = other.isUseRfc7239();
  }

  /**
   * Constructor to create an options from JSON.
   *
   * @param json the JSON
   */
  public ForwardedHeadersOptions(JsonObject json) {
    ForwardedHeadersOptionsConverter.fromJson(json, this);
  }

  /**
   * @return whether forwarded headers support is enabled
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Set whether forwarded headers support is enabled.
   * <p>
   * When disabled, no forwarded headers will be added to proxied requests.
   *
   * @param enabled {@code true} to enable forwarded headers
   * @return a reference to this, so the API can be used fluently
   */
  public ForwardedHeadersOptions setEnabled(boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  /**
   * @return whether to forward client IP address
   */
  public boolean isForwardFor() {
    return forwardFor;
  }

  /**
   * Set whether to forward the client IP address.
   * <p>
   * When enabled, adds the {@code X-Forwarded-For} header (or the 'for' parameter in RFC 7239 Forwarded header) with the client's IP address.
   * If the header already exists, the client IP is appended to preserve the proxy chain.
   *
   * @param forwardFor {@code true} to forward client IP
   * @return a reference to this, so the API can be used fluently
   */
  public ForwardedHeadersOptions setForwardFor(boolean forwardFor) {
    this.forwardFor = forwardFor;
    return this;
  }

  /**
   * @return whether to forward the original protocol (http/https)
   */
  public boolean isForwardProto() {
    return forwardProto;
  }

  /**
   * Set whether to forward the original protocol (http/https).
   * <p>
   * When enabled, adds the {@code X-Forwarded-Proto} header (or the 'proto' parameter in RFC 7239 Forwarded header) with the original request scheme.
   *
   * @param forwardProto {@code true} to forward protocol
   * @return a reference to this, so the API can be used fluently
   */
  public ForwardedHeadersOptions setForwardProto(boolean forwardProto) {
    this.forwardProto = forwardProto;
    return this;
  }

  /**
   * @return whether to forward the original host
   */
  public boolean isForwardHost() {
    return forwardHost;
  }

  /**
   * Set whether to forward the original host.
   * <p>
   * When enabled, adds the {@code X-Forwarded-Host} header (or the 'host' parameter in RFC 7239 Forwarded header) with the original request host.
   * This is only added if the host was not already set by {@link ProxyRequest#setAuthority(io.vertx.core.net.HostAndPort)}.
   *
   * @param forwardHost {@code true} to forward host
   * @return a reference to this, so the API can be used fluently
   */
  public ForwardedHeadersOptions setForwardHost(boolean forwardHost) {
    this.forwardHost = forwardHost;
    return this;
  }

  /**
   * @return whether to forward the original port
   */
  public boolean isForwardPort() {
    return forwardPort;
  }

  /**
   * Set whether to forward the original port.
   * <p>
   * When enabled, adds the {@code X-Forwarded-Port} header with the original request port.
   * This parameter is not included in RFC 7239 Forwarded header.
   *
   * @param forwardPort {@code true} to forward port
   * @return a reference to this, so the API can be used fluently
   */
  public ForwardedHeadersOptions setForwardPort(boolean forwardPort) {
    this.forwardPort = forwardPort;
    return this;
  }

  /**
   * @return whether to use RFC 7239 Forwarded header instead of {@code X-Forwarded-*} headers
   */
  public boolean isUseRfc7239() {
    return useRfc7239;
  }

  /**
   * Set whether to use RFC 7239 Forwarded header instead of {@code X-Forwarded-*} headers.
   * <p>
   * When enabled, uses the standardized {@code Forwarded} header instead of the de-facto
   * {@code X-Forwarded-For}, {@code X-Forwarded-Proto}, and {@code X-Forwarded-Host} headers. The {@code X-Forwarded-Port}
   * header is not included in RFC 7239.
   *
   * @param useRfc7239 {@code true} to use RFC 7239 format
   * @return a reference to this, so the API can be used fluently
   */
  public ForwardedHeadersOptions setUseRfc7239(boolean useRfc7239) {
    this.useRfc7239 = useRfc7239;
    return this;
  }

  @Override
  public String toString() {
    return toJson().toString();
  }

  /**
   * Convert to JSON.
   *
   * @return the JSON
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    ForwardedHeadersOptionsConverter.toJson(this, json);
    return json;
  }
}
