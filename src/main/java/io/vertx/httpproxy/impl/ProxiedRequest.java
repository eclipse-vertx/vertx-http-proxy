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
package io.vertx.httpproxy.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.HttpServerRequestInternal;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.streams.Pipe;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;

import java.util.Map;

public class ProxiedRequest implements ProxyRequest {

  private static final MultiMap HOP_BY_HOP_HEADERS = MultiMap.caseInsensitiveMultiMap()
    .add(HttpHeaders.CONNECTION, "whatever")
    .add(HttpHeaders.KEEP_ALIVE, "whatever")
    .add(HttpHeaders.PROXY_AUTHENTICATE, "whatever")
    .add(HttpHeaders.PROXY_AUTHORIZATION, "whatever")
    .add("te", "whatever")
    .add("trailer", "whatever")
    .add(HttpHeaders.TRANSFER_ENCODING, "whatever")
    .add(HttpHeaders.UPGRADE, "whatever");

  final ContextInternal context;
  private HttpMethod method;
  private HttpVersion version;
  private String uri;
  private String absoluteURI;
  private Body body;
  private MultiMap headers;
  HttpClientRequest request;
  private HttpServerRequest proxiedRequest;

  public ProxiedRequest(HttpServerRequest proxiedRequest) {

    // Determine content length
    long contentLength = -1L;
    String contentLengthHeader = proxiedRequest.getHeader(HttpHeaders.CONTENT_LENGTH);
    if (contentLengthHeader != null) {
      try {
        contentLength = Long.parseLong(contentLengthHeader);
      } catch (NumberFormatException e) {
        // Ignore ???
      }
    }

    this.method = proxiedRequest.method();
    this.version = proxiedRequest.version();
    this.body = Body.body(proxiedRequest, contentLength);
    this.uri = proxiedRequest.uri();
    this.headers = MultiMap.caseInsensitiveMultiMap().addAll(proxiedRequest.headers());
    this.absoluteURI = proxiedRequest.absoluteURI();
    this.proxiedRequest = proxiedRequest;
    this.context = (ContextInternal) ((HttpServerRequestInternal) proxiedRequest).context();
  }

  @Override
  public HttpVersion version() {
    return version;
  }

  @Override
  public String getURI() {
    return uri;
  }

  @Override
  public ProxyRequest setURI(String uri) {
    this.uri = uri;
    return this;
  }

  @Override
  public Body getBody() {
    return body;
  }

  @Override
  public ProxyRequest setBody(Body body) {
    this.body = body;
    return this;
  }

  @Override
  public String absoluteURI() {
    return absoluteURI;
  }

  @Override
  public HttpMethod getMethod() {
    return method;
  }

  @Override
  public ProxyRequest setMethod(HttpMethod method) {
    this.method = method;
    return this;
  }

  @Override
  public HttpServerRequest proxiedRequest() {
    return proxiedRequest;
  }

  @Override
  public ProxyRequest release() {
    body.stream().resume();
    headers.clear();
    body = null;
    return this;
  }

  @Override
  public ProxyResponse response() {
    return new ProxiedResponse(this, proxiedRequest.response());
  }

  void sendRequest(Handler<AsyncResult<ProxyResponse>> responseHandler) {

    request.response().<ProxyResponse>map(r -> {
      r.pause(); // Pause it
      return new ProxiedResponse(this, proxiedRequest.response(), r);
    }).onComplete(responseHandler);


    request.setMethod(method);
    request.setURI(uri);

    // Add all headers
    for (Map.Entry<String, String> header : headers) {
      String name = header.getKey();
      String value = header.getValue();
      if (!HOP_BY_HOP_HEADERS.contains(name)) {
        request.headers().add(name, value);
      }
    }

    long len = body.length();
    if (len >= 0) {
      request.putHeader(HttpHeaders.CONTENT_LENGTH, Long.toString(len));
    } else {
      Boolean isChunked = HttpUtils.isChunked(proxiedRequest.headers());
      request.setChunked(len == -1 && Boolean.TRUE == isChunked);
    }

    Pipe<Buffer> pipe = body.stream().pipe();
    pipe.endOnComplete(true);
    pipe.endOnFailure(false);
    pipe.to(request, ar -> {
      if (ar.failed()) {
        request.reset();
      }
    });
  }

  @Override
  public ProxyRequest putHeader(CharSequence name, CharSequence value) {
    headers.set(name, value);
    return this;
  }

  @Override
  public MultiMap headers() {
    return headers;
  }

  @Override
  public Future<ProxyResponse> send(HttpClientRequest request) {
    Promise<ProxyResponse> promise = context.promise();
    this.request = request;
    sendRequest(promise);
    return promise.future();
  }
}
