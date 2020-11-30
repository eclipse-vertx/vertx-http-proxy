package io.vertx.httpproxy.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.streams.Pipe;
import io.vertx.core.streams.ReadStream;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;

import java.util.function.Function;

public class ProxyRequestImpl implements ProxyRequest {

  private HttpMethod method;
  private HttpVersion version;
  private String uri;
  private String absoluteURI;
  private Body body;
  private MultiMap headers;
  HttpClientRequest edgeRequest;
  private HttpServerResponse edgeResponse;

  public ProxyRequestImpl(HttpServerRequest edgeRequest) {

    // Determine content length
    long contentLength = -1L;
    String contentLengthHeader = edgeRequest.getHeader(HttpHeaders.CONTENT_LENGTH);
    if (contentLengthHeader != null) {
      try {
        contentLength = Long.parseLong(contentLengthHeader);
      } catch (NumberFormatException e) {
        // Ignore ???
      }
    }

    this.method = edgeRequest.method();
    this.version = edgeRequest.version();
    this.body = Body.body(edgeRequest, contentLength);
    this.uri = edgeRequest.uri();
    this.headers = MultiMap.caseInsensitiveMultiMap().addAll(edgeRequest.headers());
    this.absoluteURI = edgeRequest.absoluteURI();
    this.edgeResponse = edgeRequest.response();
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
  public ProxyRequestImpl setBody(Body body) {
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
  public ProxyRequest release() {
    body.stream().resume();
    headers.clear();
    body = null;
    return this;
  }

  @Override
  public ProxyResponse response() {
    return new ProxyResponseImpl(this, edgeResponse);
  }

  void sendRequest(Handler<AsyncResult<ProxyResponse>> responseHandler) {

    edgeRequest.<ProxyResponse>map(r -> {
      r.pause(); // Pause it
      return new ProxyResponseImpl(this, edgeResponse, r);
    }).onComplete(responseHandler);


    edgeRequest.setMethod(method);
    edgeRequest.setURI(uri);

    // Add all end-to-end headers
    headers.forEach(header -> {
      String name = header.getKey();
      String value = header.getValue();
      if (name.equalsIgnoreCase("host")) {
        // Skip
      } else {
        edgeRequest.headers().add(name, value);
      }
    });

    long len = body.length();
    if (len >= 0) {
      edgeRequest.putHeader(HttpHeaders.CONTENT_LENGTH, Long.toString(len));
    } else {
      edgeRequest.setChunked(true);
    }

    Pipe<Buffer> pipe = body.stream().pipe();
    pipe.endOnComplete(true);
    pipe.endOnFailure(false);
    pipe.to(edgeRequest, ar -> {
      if (ar.failed()) {
        edgeRequest.reset();
      }
    });
  }

  @Override
  public ProxyRequestImpl putHeader(CharSequence name, CharSequence value) {
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
  public void send(HttpClientRequest request, Handler<AsyncResult<ProxyResponse>> completionHandler) {
    edgeRequest = request;
    sendRequest(completionHandler);
  }
}
