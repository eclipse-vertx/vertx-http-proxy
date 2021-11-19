package io.vertx.httpproxy.impl;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;
import io.vertx.httpproxy.ProxyEvent;

public final class ProxyEventImpl implements ProxyEvent {

  private int statusCode;

  private final ProxyRequest request;

  private boolean isFailed = false;

  public ProxyEventImpl(final ProxyResponse response) {
    this.request = response.request();
    this.statusCode = response.getStatusCode();
  }

  public ProxyEventImpl(final ProxyRequest request) {
    this.request = request;
    this.statusCode = request.outboundRequest().response().getStatusCode();
  }

  @Override
  public int getStatusCode() {
    return this.statusCode;
  }

  @Override
  public HttpServerRequest outboundRequest() {
    return this.request.outboundRequest();
  }

  @Override
  public ProxyEvent withStatusCode(int statusCode) {
    this.statusCode = statusCode;
    return this;
  }

  public ProxyEvent setFailed() {
    this.isFailed = true;
    return this;
  }

  @Override
  public boolean failed() {
    return this.isFailed;
  }

}
