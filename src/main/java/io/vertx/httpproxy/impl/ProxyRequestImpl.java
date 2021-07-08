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

import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.HttpServerRequestInternal;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.streams.Pipe;
import io.vertx.core.streams.ReadStream;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;

import java.util.function.Function;

public class ProxyRequestImpl implements ProxyRequest {

  final ContextInternal context;
  private HttpMethod method;
  private HttpVersion version;
  private String uri;
  private String absoluteURI;
  private Body body;
  private MultiMap headers;
  HttpClientRequest inboundRequest;
  private HttpServerRequest outboundRequest;

  public ProxyRequestImpl(HttpServerRequest outboundRequest) {

    // Determine content length
    long contentLength = -1L;
    String contentLengthHeader = outboundRequest.getHeader(HttpHeaders.CONTENT_LENGTH);
    if (contentLengthHeader != null) {
      try {
        contentLength = Long.parseLong(contentLengthHeader);
      } catch (NumberFormatException e) {
        // Ignore ???
      }
    }

    this.method = outboundRequest.method();
    this.version = outboundRequest.version();
    this.body = Body.body(outboundRequest, contentLength);
    this.uri = outboundRequest.uri();
    this.headers = MultiMap.caseInsensitiveMultiMap().addAll(outboundRequest.headers());
    this.absoluteURI = outboundRequest.absoluteURI();
    this.outboundRequest = outboundRequest;
    this.context = (ContextInternal) ((HttpServerRequestInternal) outboundRequest).context();
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
  public HttpServerRequest outboundRequest() {
    return outboundRequest;
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
    return new ProxyResponseImpl(this, outboundRequest.response());
  }

  void sendRequest(Handler<AsyncResult<ProxyResponse>> responseHandler) {

    inboundRequest.response().<ProxyResponse>map(r -> {
      r.pause(); // Pause it
      return new ProxyResponseImpl(this, outboundRequest.response(), r);
    }).onComplete(responseHandler);


    inboundRequest.setMethod(method);
    inboundRequest.setURI(uri);

    // Add all end-to-end headers
    headers.forEach(header -> {
      String name = header.getKey();
      String value = header.getValue();
      if (name.equalsIgnoreCase("host")) {
        // Skip
      } else {
        inboundRequest.headers().add(name, value);
      }
    });

    long len = body.length();
    if (len >= 0) {
      inboundRequest.putHeader(HttpHeaders.CONTENT_LENGTH, Long.toString(len));
    } else {
      inboundRequest.setChunked(true);
    }

    Pipe<Buffer> pipe = body.stream().pipe();
    pipe.endOnComplete(true);
    pipe.endOnFailure(false);
    pipe.to(inboundRequest, ar -> {
      if (ar.failed()) {
        inboundRequest.reset();
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
  public ProxyRequest bodyFilter(Function<ReadStream<Buffer>, ReadStream<Buffer>> filter) {
    return this;
  }

  @Override
  public Future<ProxyResponse> send(HttpClientRequest inboundRequest) {
    Promise<ProxyResponse> promise = context.promise();
    send(inboundRequest, promise);
    return promise.future();
  }

  void send(HttpClientRequest inboundRequest, Handler<AsyncResult<ProxyResponse>> completionHandler) {
    this.inboundRequest = inboundRequest;
    sendRequest(completionHandler);
  }

  void sendWebSocket(WebSocket inboundWebSocket, ServerWebSocket outboundWebSocket) {
    outboundWebSocket.frameHandler(inboundWebSocket::writeFrame);
    outboundWebSocket.closeHandler(x -> inboundWebSocket.close());
    inboundWebSocket.frameHandler(outboundWebSocket::writeFrame);
    inboundWebSocket.closeHandler(x -> outboundWebSocket.close());
  }
}
