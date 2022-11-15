/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.httpproxy;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.httpproxy.impl.ProxiedRequest;

/**
 *
 * Handles the interoperability of the <b>request</b> between the <i><b>user agent</b></i> and the <i><b>origin</b></i>.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface ProxyRequest {

  /**
   * Create a new {@code ProxyRequest} instance, the proxied request will be paused.
   *
   * @param proxiedRequest the {@code HttpServerRequest} that is proxied
   * @return a reference to this, so the API can be used fluently
   */
  static ProxyRequest reverseProxy(HttpServerRequest proxiedRequest) {
    proxiedRequest.pause();
    return new ProxiedRequest(proxiedRequest);
  }

  /**
   * @return the HTTP version of the proxied request
   */
  HttpVersion version();

  /**
   * @return the absolute URI of the proxied request
   */
  String absoluteURI();

  /**
   * @return the HTTP method to be sent to the <i><b>origin</b></i> server.
   */
  HttpMethod getMethod();

  /**
   * Set the HTTP method to be sent to the <i><b>origin</b></i> server.
   *
   * <p>The initial HTTP method value is the proxied request HTTP method.
   *
   * @param method the new HTTP method
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ProxyRequest setMethod(HttpMethod method);

  /**
   * @return the request URI to be sent to the <i><b>origin</b></i> server.
   */
  String getURI();

  /**
   * Set the request URI to be sent to the <i><b>origin</b></i> server.
   *
   * <p>The initial request URI value is the proxied request URI.
   *
   * @param uri the new URI
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ProxyRequest setURI(String uri);

  /**
   * @return the request body to be sent to the <i><b>origin</b></i> server.
   */
  Body getBody();

  /**
   * Set the request body to be sent to the <i><b>origin</b></i> server.
   *
   * <p>The initial request body value is the proxied request body.
   *
   * @param body the new body
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ProxyRequest setBody(Body body);

  /**
   * Set the request authority
   *
   * <ul>
   *   <li>for HTTP/1 the {@literal Host} header</li>
   *   <li>for HTTP/2 the {@literal :authority} pseudo header</li>
   * </ul>
   *
   * The value must follow the {@literal <host>:<port>} syntax.
   *
   * @param authority the authority
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ProxyRequest setAuthority(String authority);

  /**
   * @return the request authority, for HTTP2 the {@literal :authority} pseudo header otherwise the {@literal Host} header
   */
  String getAuthority();

  /**
   * @return the headers that will be sent to the origin server, the returned headers can be modified. The headers
   *         map is populated with the proxied request headers
   */
  MultiMap headers();

  /**
   * Put an HTTP header
   *
   * @param name  The header name
   * @param value The header value
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  @Fluent
  ProxyRequest putHeader(CharSequence name, CharSequence value);

  /**
   * Proxy this request to the <i><b>origin</b></i> server using the specified {@code request} and then send the proxy response.
   *
   * @param request the request connected to the <i><b>origin</b></i> server
   */
  default Future<Void> proxy(HttpClientRequest request) {
    return send(request).flatMap(resp -> resp.send());
  }

  /**
   * Send this request to the <i><b>origin</b></i> server using the specified {@code request}.
   *
   * <p> The returned future will be completed with the proxy response returned by the <i><b>origin</b></i>.
   *
   * @param request the request connected to the <i><b>origin</b></i> server
   */
  Future<ProxyResponse> send(HttpClientRequest request);

  /**
   * Release the proxy request and its associated resources
   *
   * <p> The HTTP server request is resumed, no HTTP server response is sent.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ProxyRequest release();

  /**
   * @return the proxied HTTP server request
   */
  HttpServerRequest proxiedRequest();

  /**
   * Create and return the proxy response.
   *
   * @return the proxy response
   */
  ProxyResponse response();

}
