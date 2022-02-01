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
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;

import java.util.function.Function;

/**
 *
 * Handles the interoperability of the <b>response</b> between the <i><b>origin</b></i> and the <i><b>user agent</b></i>.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface ProxyResponse {

  /**
   *
   * Return the corresponding {@code ProxyRequest}.
   *
   * @return the proxy request
   */
  ProxyRequest request();

  /**
   * Get the status code.
   *
   * @return the status code to be sent to the <i><b>user agent</b></i>
   */
  int getStatusCode();

  /**
   * Set the status code to be sent to the <i><b>user agent</b></i>.
   *
   * <p> The initial value is the proxied response status code.
   *
   * @param sc the status code
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ProxyResponse setStatusCode(int sc);

  /**
   * Get the status message.
   *
   * @return the status message to be sent to the <i><b>user agent</b></i>
   */
  String getStatusMessage();

  /**
   * Set the status message to be sent to the <i><b>user agent</b></i>.
   *
   * <p> The initial value is the proxied response status message.
   *
   * @param statusMessage the status message
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ProxyResponse setStatusMessage(String statusMessage);

  /**
   * @return the headers that will be sent to the <i><b>user agent</b></i>, the returned headers can be modified. The headers
   *         map is populated with the proxied response headers
   */
  MultiMap headers();

  /**
   * Put an HTTP header.
   *
   * @param name  The header name
   * @param value The header value
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  @Fluent
  ProxyResponse putHeader(CharSequence name, CharSequence value);

  /**
   * Get the body of the response.
   *
   * @return the response body to be sent to the <i><b>user agent</b></i>
   */
  Body getBody();

  /**
   * Set the request body to be sent to the <i><b>user agent</b></i>.
   *
   * <p>The initial request body value is the proxied response body.
   *
   * @param body the new body
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ProxyResponse setBody(Body body);

  boolean publicCacheControl();

  long maxAge();

  /**
   * @return the {@code etag} sent by the <i><b>origin</b></i> response
   */
  String etag();

  /**
   * Send the proxies response to the <i><b>user agent</b></i>.
   */
  Future<Void> send();

  /**
   * Release the proxy response.
   *
   * <p> The proxied response is resumed, no HTTP response is sent to the <i><b>user-agent</b></i>
   */
  @Fluent
  ProxyResponse release();

}
