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
package io.vertx.httpproxy.impl;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.http.HttpServerRequestInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.Pipe;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ForwardedHeadersOptions;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;

import java.util.Map;
import java.util.Objects;

import static io.vertx.core.http.HttpHeaders.*;

public class ProxiedRequest implements ProxyRequest {

  private static final CharSequence X_FORWARDED_HOST = HttpHeaders.createOptimized("x-forwarded-host");
  private static final CharSequence X_FORWARDED_FOR = HttpHeaders.createOptimized("x-forwarded-for");
  private static final CharSequence X_FORWARDED_PROTO = HttpHeaders.createOptimized("x-forwarded-proto");
  private static final CharSequence X_FORWARDED_PORT = HttpHeaders.createOptimized("x-forwarded-port");
  private static final CharSequence FORWARDED = HttpHeaders.createOptimized("forwarded");

  // Forwarded headers bit flags
  private static final int FLAG_FORWARD_FOR = 1;    // bit 0
  private static final int FLAG_FORWARD_PROTO = 2;  // bit 1
  private static final int FLAG_FORWARD_HOST = 4;   // bit 2
  private static final int FLAG_FORWARD_PORT = 8;   // bit 3
  private static final int FLAG_USE_RFC7239 = 16;   // bit 4

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
  private final int forwardedHeadersFlags;

  public ProxiedRequest(HttpServerRequest proxiedRequest, ForwardedHeadersOptions forwardedHeadersOptions) {
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

    // Convert forwarded headers options to bit flags for efficient checking
    this.forwardedHeadersFlags = buildForwardedHeadersFlags(forwardedHeadersOptions);
  }

  private static int buildForwardedHeadersFlags(ForwardedHeadersOptions options) {
    if (options == null || !options.isEnabled()) {
      return 0;
    }
    int flags = 0;
    if (options.isForwardFor()) {
      flags |= FLAG_FORWARD_FOR;
    }
    if (options.isForwardProto()) {
      flags |= FLAG_FORWARD_PROTO;
    }
    if (options.isForwardHost()) {
      flags |= FLAG_FORWARD_HOST;
    }
    if (options.isForwardPort()) {
      flags |= FLAG_FORWARD_PORT;
    }
    if (options.isUseRfc7239()) {
      flags |= FLAG_USE_RFC7239;
    }
    return flags;
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

    // Add forwarded headers if configured
    if (forwardedHeadersFlags != 0) {
      addForwardedHeaders(request);
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

  private void addForwardedHeaders(HttpClientRequest request) {
    if ((forwardedHeadersFlags & FLAG_USE_RFC7239) != 0) {
      addRfc7239ForwardedHeader(request);
    } else {
      addXForwardedHeaders(request);
    }
  }

  private void addRfc7239ForwardedHeader(HttpClientRequest request) {
    int capacity = estimateRfc7239Capacity(forwardedHeadersFlags);
    StringBuilder forwarded = new StringBuilder(capacity);
    if ((forwardedHeadersFlags & FLAG_FORWARD_FOR) != 0) {
      appendRfc7239For(forwarded);
    }
    if ((forwardedHeadersFlags & FLAG_FORWARD_PROTO) != 0) {
      appendRfc7239Proto(forwarded);
    }
    if ((forwardedHeadersFlags & FLAG_FORWARD_HOST) != 0) {
      appendRfc7239Host(forwarded);
    }
    // If we reach here with non-zero flags, at least one component should be added
    appendHeader(request, FORWARDED, forwarded.toString());
  }

  private static int estimateRfc7239Capacity(int flags) {
    // Capacity estimates to avoid StringBuilder resizing:
    // for=IPv6(39) + quotes(2) + prefix(4) ≈ 50
    // proto=https(5) + prefix(6) ≈ 15
    // host=domain(253) + port(6) + quotes(2) + prefix(5) ≈ 260
    int capacity = 0;
    if ((flags & FLAG_FORWARD_FOR) != 0) {
      capacity += 50;
    }
    if ((flags & FLAG_FORWARD_PROTO) != 0) {
      capacity += 15;
    }
    if ((flags & FLAG_FORWARD_HOST) != 0) {
      capacity += 260;
    }
    return capacity;
  }

  private void appendRfc7239For(StringBuilder forwarded) {
    String clientIp = getClientIp();
    if (clientIp != null) {
      forwarded.append("for=").append(quoteIfNeeded(clientIp));
    }
  }

  private void appendRfc7239Proto(StringBuilder forwarded) {
    String proto = proxiedRequest.scheme();
    if (proto != null) {
      if (forwarded.length() > 0) forwarded.append(";");
      forwarded.append("proto=").append(proto);
    }
  }

  private void appendRfc7239Host(StringBuilder forwarded) {
    HostAndPort host = proxiedRequest.authority();
    if (host != null) {
      if (forwarded.length() > 0) forwarded.append(";");
      forwarded.append("host=").append(quoteIfNeeded(host.toString()));
    }
  }

  private void addXForwardedHeaders(HttpClientRequest request) {
    if ((forwardedHeadersFlags & FLAG_FORWARD_FOR) != 0) {
      addXForwardedFor(request);
    }
    if ((forwardedHeadersFlags & FLAG_FORWARD_PROTO) != 0) {
      addXForwardedProto(request);
    }
    if ((forwardedHeadersFlags & FLAG_FORWARD_HOST) != 0) {
      addXForwardedHost(request);
    }
    if ((forwardedHeadersFlags & FLAG_FORWARD_PORT) != 0) {
      addXForwardedPort(request);
    }
  }

  private void addXForwardedFor(HttpClientRequest request) {
    String clientIp = getClientIp();
    if (clientIp != null) {
      appendHeader(request, X_FORWARDED_FOR, clientIp);
    }
  }

  private void addXForwardedProto(HttpClientRequest request) {
    String proto = proxiedRequest.scheme();
    if (proto != null) {
      request.putHeader(X_FORWARDED_PROTO, proto);
    }
  }

  private void addXForwardedHost(HttpClientRequest request) {
    // Only add if not already set by setAuthority() logic
    if (!request.headers().contains(X_FORWARDED_HOST)) {
      HostAndPort host = proxiedRequest.authority();
      if (host != null) {
        request.putHeader(X_FORWARDED_HOST, host.host());
      }
    }
  }

  private void addXForwardedPort(HttpClientRequest request) {
    HostAndPort host = proxiedRequest.authority();
    if (host != null) {
      request.putHeader(X_FORWARDED_PORT, String.valueOf(host.port()));
    }
  }

  private String getClientIp() {
    SocketAddress remoteAddress = proxiedRequest.remoteAddress();
    return remoteAddress != null ? remoteAddress.hostAddress() : null;
  }

  private void appendHeader(HttpClientRequest request, CharSequence name, String value) {
    String existing = request.headers().get(name);
    if (existing != null) {
      request.putHeader(name, existing + ", " + value);
    } else {
      request.putHeader(name, value);
    }
  }

  private String quoteIfNeeded(String value) {
    // Quote IPv6 addresses and values containing special characters
    if (value.contains(":") || value.contains(";") || value.contains(",")) {
      return "\"" + value + "\"";
    }
    return value;
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
