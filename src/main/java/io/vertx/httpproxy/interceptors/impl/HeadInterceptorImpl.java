package io.vertx.httpproxy.interceptors.impl;

import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.QueryStringEncoder;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;
import io.vertx.httpproxy.interceptors.HeadInterceptor;

import java.util.function.Function;

class HeadInterceptorImpl implements HeadInterceptor {

  private final Handler<MultiMap> queryUpdater;
  private final Function<String, String> pathUpdater;
  private final Handler<MultiMap> requestHeadersUpdater;
  private final Handler<MultiMap> responseHeadersUpdater;


  public HeadInterceptorImpl(Handler<MultiMap> queryUpdater, Function<String, String> pathUpdater, Handler<MultiMap> requestHeadersUpdater, Handler<MultiMap> responseHeadersUpdater) {
    this.queryUpdater = queryUpdater;
    this.pathUpdater = pathUpdater;
    this.requestHeadersUpdater = requestHeadersUpdater;
    this.responseHeadersUpdater = responseHeadersUpdater;
  }

  @Override
  public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
    if (queryUpdater != null) {
      queryHandleProxyRequest(context);
    }
    if (pathUpdater != null) {
      pathHandleProxyRequest(context);
    }
    if (requestHeadersUpdater != null) {
      headersHandleProxyRequest(context);
    }
    return context.sendRequest();
  }

  @Override
  public Future<Void> handleProxyResponse(ProxyContext context) {
    if (responseHeadersUpdater != null) {
      headersHandleProxyResponse(context);
    }
    return context.sendResponse();
  }

  public void queryHandleProxyRequest(ProxyContext context) {
    String rawUri = context.request().getURI();
    MultiMap params = queryParams(rawUri);
    String cleanedUri = cleanedUri(rawUri);

    queryUpdater.handle(params);
    String newUri = buildUri(cleanedUri, params);
    context.request().setURI(newUri);
  }

  // ref: https://github.com/vert-x3/vertx-web/blob/master/vertx-web-client/src/main/java/io/vertx/ext/web/client/impl/HttpRequestImpl.java
  private static MultiMap queryParams(String uri) {
    MultiMap queryParams = MultiMap.caseInsensitiveMultiMap();
    int idx = uri.indexOf('?');
    if (idx >= 0) {
      QueryStringDecoder dec = new QueryStringDecoder(uri);
      dec.parameters().forEach(queryParams::add);
    }
    return queryParams;
  }

  // ref: https://github.com/vert-x3/vertx-web/blob/master/vertx-web-client/src/main/java/io/vertx/ext/web/client/impl/HttpRequestImpl.java
  private static String cleanedUri(String uri) {
    int idx = uri.indexOf('?');
    if (idx >= 0) {
      uri = uri.substring(0, idx);
    }
    return uri;
  }

  // ref: https://github.com/vert-x3/vertx-web/blob/master/vertx-web-client/src/main/java/io/vertx/ext/web/client/impl/HttpRequestImpl.java
  private static String buildUri(String uri, MultiMap queryParams) {
    QueryStringDecoder decoder = new QueryStringDecoder(uri);
    QueryStringEncoder encoder = new QueryStringEncoder(decoder.rawPath());
    decoder.parameters().forEach((name, values) -> {
      for (String value : values) {
        encoder.addParam(name, value);
      }
    });
    queryParams.forEach(param -> {
      encoder.addParam(param.getKey(), param.getValue());
    });
    uri = encoder.toString();
    return uri;
  }

  public void pathHandleProxyRequest(ProxyContext context) {
    ProxyRequest proxyRequest = context.request();
    proxyRequest.setURI(pathUpdater.apply(proxyRequest.getURI()));
  }

  private void headersHandleProxyRequest(ProxyContext context) {
    ProxyRequest request = context.request();
    requestHeadersUpdater.handle(request.headers());
  }

  private void headersHandleProxyResponse(ProxyContext context) {
    ProxyResponse response = context.response();
    responseHeadersUpdater.handle(response.headers());
  }
}
