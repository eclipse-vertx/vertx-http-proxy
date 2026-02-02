/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.httpproxy.impl;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.http.HttpServerRequestInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.streams.Pipe;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;

import java.util.Map;
import java.util.Objects;

import static io.vertx.core.http.HttpHeaders.*;

public class ProxiedRequest implements ProxyRequest {

  private static final CharSequence X_FORWARDED_HOST = HttpHeaders.createOptimized("x-forwarded-host");

  private static final MultiMap HOP_BY_HOP_HEADERS = MultiMap.caseInsensitiveMultiMap()
    .add(CONNECTION, "whatever")
    .add(KEEP_ALIVE, "whatever")
    .add(PROXY_AUTHENTICATE, "whatever")
    .add(PROXY_AUTHORIZATION, "whatever")
    .add("te", "whatever")
    .add("trailer", "whatever")
    .add(TRANSFER_ENCODING, "whatever")
    .add(UPGRADE, "whatever");

  final ContextInternal context;
  private HttpMethod method;
  private final HttpVersion version;
  private String uri;
  private final String absoluteURI;
  private Body body;
  private HostAndPort authority;
  private final MultiMap headers;
  HttpClientRequest request;
  private final HttpServerRequest proxiedRequest;

  public ProxiedRequest(HttpServerRequest proxiedRequest) {

    // Determine content length
    long contentLength = -1L;
    String contentLengthHeader = proxiedRequest.getHeader(CONTENT_LENGTH);
    if (contentLengthHeader != null) {
      try {
        contentLength = Long.parseLong(contentLengthHeader);
      } catch (NumberFormatException e) {
        // Ignore ???
      }
    }

    // Content type
    String contentType = proxiedRequest.getHeader(HttpHeaders.CONTENT_TYPE);

    this.method = proxiedRequest.method();
    this.version = proxiedRequest.version();
    this.body = Body.body(proxiedRequest, contentLength, contentType);
    this.uri = proxiedRequest.uri();
    this.headers = MultiMap.caseInsensitiveMultiMap().addAll(proxiedRequest.headers());
    this.absoluteURI = proxiedRequest.absoluteURI();
    this.proxiedRequest = proxiedRequest;
    this.context = ((HttpServerRequestInternal) proxiedRequest).context();
    this.authority = null; // null is used as a signal to indicate an unchanged authority
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
  public ProxyRequest setAuthority(HostAndPort authority) {
    Objects.requireNonNull(authority);
    this.authority = authority;
    return this;
  }

  @Override
  public HostAndPort getAuthority() {
    return authority;
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
    if (body != null) {
      body.stream().resume();
      headers.clear();
      body = null;
    }
    return this;
  }

  @Override
  public ProxyResponse response() {
    return new ProxiedResponse(this, proxiedRequest.response());
  }

  Future<ProxyResponse> sendRequest() {
    proxiedRequest.response().exceptionHandler(throwable -> request.reset(0L, throwable));

    request.setMethod(method);
    request.setURI(uri);

    // Add all headers
    for (Map.Entry<String, String> header : headers) {
      String name = header.getKey();
      String value = header.getValue();
      if (!HOP_BY_HOP_HEADERS.contains(name) && !name.equalsIgnoreCase(HttpHeaders.HOST.toString())) {
        request.headers().add(name, value);
      }
    }

    //
    if (authority != null) {
      request.authority(authority);
      HostAndPort proxiedAuthority = proxiedRequest.authority();
      if (!equals(authority, proxiedAuthority)) {
        // Should cope with existing forwarded host headers
        request.putHeader(X_FORWARDED_HOST, proxiedAuthority.toString());
      }
    }

    if (body == null) {
      if (proxiedRequest.headers().contains(CONTENT_LENGTH)) {
        request.putHeader(CONTENT_LENGTH, "0");
      }
      request.end();
    } else {
      long len = body.length();
      if (len >= 0) {
        request.putHeader(CONTENT_LENGTH, Long.toString(len));
      } else {
        Boolean isChunked = HttpUtils.isChunked(proxiedRequest.headers());
        request.setChunked(len == -1 && Boolean.TRUE == isChunked);
      }

      Pipe<Buffer> pipe = body.stream().pipe();
      pipe.endOnComplete(true);
      pipe.endOnFailure(false);
      pipe.to(request).onComplete(ar -> {
        if (ar.failed()) {
          request.reset();
        }
      });
    }

    return request.response().map(r -> {
      r.pause(); // Pause it
      return new ProxiedResponse(this, proxiedRequest.response(), r);
    });
  }

  private static boolean equals(HostAndPort hp1, HostAndPort hp2) {
    if (hp1 == null || hp2 == null) {
      return false;
    }
    return hp1.host().equals(hp2.host()) && hp1.port() == hp2.port();
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
    this.request = request;
    return sendRequest();
  }
}
