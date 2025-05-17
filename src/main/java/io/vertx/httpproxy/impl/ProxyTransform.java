package io.vertx.httpproxy.impl;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyResponse;

public class ProxyTransform implements ProxyInterceptor {
  @Override
  public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
    operateConnectionHeader(context.request().headers());
    return context.sendRequest();
  }

  @Override
  public Future<Void> handleProxyResponse(ProxyContext context) {
    return context.sendResponse();
  }

  private static void operateConnectionHeader(MultiMap headers) {
    String connection = headers.get(HttpHeaders.CONNECTION);
    if (connection == null || connection.trim().equals("close")) return;

    String[] toRemoveArr = connection.split(",");
    for (String toRemove : toRemoveArr) {
      headers.remove(toRemove.trim());
    }
  }
}
