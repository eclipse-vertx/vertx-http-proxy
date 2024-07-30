package io.vertx.httpproxy.interceptors.impl;

import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyResponse;

public class WebSocketInterceptorImpl implements ProxyInterceptor {
  ProxyInterceptor interceptor;

  public WebSocketInterceptorImpl(ProxyInterceptor interceptor) {
    this.interceptor = interceptor;
  }

  @Override
  public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
    return interceptor.handleProxyRequest(context);
  }

  @Override
  public Future<Void> handleProxyResponse(ProxyContext context) {
    return interceptor.handleProxyResponse(context);
  }

  @Override
  public boolean allowApplyToWebSocket() {
    return true;
  }
}
