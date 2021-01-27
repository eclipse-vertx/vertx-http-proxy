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
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.streams.ReadStream;
import io.vertx.httpproxy.impl.ProxyRequestImpl;

import java.util.function.Function;

/**
 *
 * Handles the interoperability of the <b>request</b> between the <i><b>edge</b></i> and the <i><b>origin</b></i>.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface ProxyRequest {

  /**
   * Create a new {@code ProxyRequest} instance.
   *
   * @param request the {@code HttpServerRequest} of the <i><b>edge</b></i>
   * @return a reference to this, so the API can be used fluently
   */
  static ProxyRequest reverseProxy(HttpServerRequest request) {
    request.pause();
    ProxyRequestImpl proxyRequest = new ProxyRequestImpl(request);
    return proxyRequest;
  }

  /**
   * @return the HTTP version of the <i><b>edge</b></i> request
   */
  HttpVersion version();

  /**
   * @return the absolute URI of the <i><b>edge</b></i> request
   */
  String absoluteURI();

  /**
   * @return the HTTP method to be sent to the <i><b>origin</b></i> server.
   */
  HttpMethod getMethod();

  /**
   * Set the HTTP method to be sent to the <i><b>origin</b></i> server.
   *
   * <p>The initial HTTP method value is the <i><b>edge</b></i> request HTTP method.
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
   * <p>The initial request URI value is the <i><b>edge</b></i> request URI.
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
   * <p>The initial request body value is the <i><b>edge</b></i> request body.
   *
   * @param body the new body
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ProxyRequest setBody(Body body);

  /**
   * @return the headers that will be sent to the origin server, the returned headers can be modified. The headers
   *         map is populated with the edge request headers
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
   * Set a body filter.
   *
   * <p> The body filter can rewrite the request body sent to the <i><b>origin</b></i> server.
   *
   * @param filter the filter
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ProxyRequest bodyFilter(Function<ReadStream<Buffer>, ReadStream<Buffer>> filter);

  /**
   * Proxy this request and response to the <i><b>origin</b></i> server using the specified request.
   *
   * @param request the request connected to the <i><b>origin</b></i> server
   * @param completionHandler the completion handler
   */
  default void proxy(HttpClientRequest request, Handler<AsyncResult<Void>> completionHandler) {
    send(request, ar -> {
      if (ar.succeeded()) {
        ProxyResponse resp = ar.result();
        resp.send(completionHandler);
      } else {
        completionHandler.handle(ar.mapEmpty());
      }
    });
  }

  /**
   * Send this request to the <i><b>origin</b></i> server using the specified request.
   *
   * <p> The {@code completionHandler} will be called with the proxy response sent by the <i><b>origin</b></i>.
   *
   * @param request the request connected to the <i><b>origin</b></i> server
   * @param completionHandler the completion handler
   */
  void send(HttpClientRequest request, Handler<AsyncResult<ProxyResponse>> completionHandler);

  /**
   * Release the proxy request.
   *
   * <p> The HTTP server request is resumed, no HTTP server response is sent.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ProxyRequest release();

  /**
   * Create and return a default proxy response.
   *
   * @return a default proxy response
   */
  ProxyResponse response();

}
