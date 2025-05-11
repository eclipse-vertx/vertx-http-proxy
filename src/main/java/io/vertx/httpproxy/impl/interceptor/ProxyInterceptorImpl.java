/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.httpproxy.impl.interceptor;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.QueryStringEncoder;
import io.vertx.core.*;
import io.vertx.core.http.HttpHeaders;
import io.vertx.httpproxy.*;
import io.vertx.httpproxy.MediaType;
import io.vertx.httpproxy.impl.ProxyFailure;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

class ProxyInterceptorImpl implements ProxyInterceptor {

  private final List<Handler<MultiMap>> queryUpdaters;
  private final List<Function<String, String>> pathUpdaters;
  private final List<Handler<MultiMap>> requestHeadersUpdaters;
  private final List<Handler<MultiMap>> responseHeadersUpdaters;
  private final BodyTransformer modifyRequestBody;
  private final BodyTransformer modifyResponseBody;

  ProxyInterceptorImpl(
    List<Handler<MultiMap>> queryUpdaters,
    List<Function<String, String>> pathUpdaters,
    List<Handler<MultiMap>> requestHeadersUpdaters,
    List<Handler<MultiMap>> responseHeadersUpdaters,
    BodyTransformer modifyRequestBody,
    BodyTransformer modifyResponseBody) {
    this.queryUpdaters = Objects.requireNonNull(queryUpdaters);
    this.pathUpdaters = Objects.requireNonNull(pathUpdaters);
    this.requestHeadersUpdaters = Objects.requireNonNull(requestHeadersUpdaters);
    this.responseHeadersUpdaters = Objects.requireNonNull(responseHeadersUpdaters);
    this.modifyRequestBody = modifyRequestBody;
    this.modifyResponseBody = modifyResponseBody;
  }

  private MediaType safeMT(String mt) {
    try {
      return mt == null ? null : MediaType.parse(mt);
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
    try {
      queryHandleProxyRequest(context);
      pathHandleProxyRequest(context);
      headersHandleProxyRequest(context);
    } catch (Exception e) {
      return Future.failedFuture(new ProxyFailure(500, e));
    }
    Body requestBody = context.request().getBody();
    if (modifyRequestBody != null && requestBody != null) {
      boolean transform;
      MediaType bodyMediaType = safeMT(requestBody.mediaType());
      try {
        transform =
            (bodyMediaType != null || !context.request().headers().contains(HttpHeaders.CONTENT_TYPE)) &&
            modifyRequestBody.consumes(bodyMediaType);
      } catch (Exception e) {
        return Future.failedFuture(new ProxyFailure(500, e));
      }
      if (transform) {
        Promise<ProxyResponse> ret = Promise.promise();
        Future<Body> fut = modifyRequestBody.transform(context.request().getBody());
        fut.onComplete(ar -> {
          if (ar.succeeded()) {
            ProxyRequest request = context.request();
            request.setBody(ar.result());
            context.sendRequest().onComplete(ret);
          } else {
            ret.fail(ar.cause());
          }
        });
        return ret.future();
      }
    }
    return context.sendRequest();
  }

  private MediaType resolveMediaType(String acceptHeader, MediaType mt) throws IllegalArgumentException {
    List<MediaType> acceptedMediaTypes = MediaType.parseAcceptHeader(acceptHeader);
    MediaType produced = modifyResponseBody.produces(mt);
    for (MediaType acceptedMediaType : acceptedMediaTypes) {
      if (acceptedMediaType.accepts(produced)) {
        return produced;
      }
    }
    return null;
  }

  @Override
  public Future<Void> handleProxyResponse(ProxyContext context) {
    try {
      headersHandleProxyResponse(context);
    } catch (Exception e) {
      return Future.failedFuture(new ProxyFailure(500, e));
    }
    Body responseBody = context.response().getBody();
    if (modifyResponseBody != null && responseBody != null) {
      MediaType bodyMediaType = safeMT(responseBody.mediaType());
      boolean checkTransform;
      try {
        checkTransform =
            (bodyMediaType != null || !context.response().headers().contains(HttpHeaders.CONTENT_TYPE)) &&
            modifyResponseBody.consumes(bodyMediaType);
      } catch (Exception e) {
        return Future.failedFuture(new ProxyFailure(500, e));
      }
      if (checkTransform) {
        String acceptHeader = context.request().headers().get(HttpHeaderNames.ACCEPT);
        MediaType mediaType;
        boolean transform;
        if (acceptHeader != null) {
          transform = false;
          MediaType resolved = null;
          try {
            resolved = resolveMediaType(acceptHeader, bodyMediaType);
            transform = resolved != null;
          } catch (IllegalArgumentException e) {
            // Invalid
          }
          mediaType = resolved;
        } else {
          transform = true;
          try {
            mediaType = modifyResponseBody.produces(bodyMediaType);
          } catch (Exception e) {
            return Future.failedFuture(new ProxyFailure(500, e));
          }
        }
        if (transform) {
          Promise<Void> ret = Promise.promise();
          Future<Body> fut = modifyResponseBody.transform(responseBody);
          fut.onComplete(ar -> {
            ProxyResponse response = context.response();
            if (ar.succeeded()) {
              response.setBody(ar.result());
              context.sendResponse();
            } else {
              ret.fail(ar.cause());
            }
          });
          return ret.future();
        }
      }
    }
    return context.sendResponse();
  }

  private void queryHandleProxyRequest(ProxyContext context) {
    String rawUri = context.request().getURI();
    MultiMap params = queryParams(rawUri);
    String cleanedUri = cleanedUri(rawUri);

    for (Handler<MultiMap> queryUpdater : queryUpdaters) {
      queryUpdater.handle(params);
    }

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

  private void pathHandleProxyRequest(ProxyContext context) {
    ProxyRequest proxyRequest = context.request();
    for (Function<String, String> pathUpdater : pathUpdaters) {
      proxyRequest.setURI(pathUpdater.apply(proxyRequest.getURI()));
    }
  }

  private void headersHandleProxyRequest(ProxyContext context) {
    ProxyRequest request = context.request();
    for (Handler<MultiMap> requestHeadersUpdater : requestHeadersUpdaters) {
      requestHeadersUpdater.handle(request.headers());
    }
  }

  private void headersHandleProxyResponse(ProxyContext context) {
    ProxyResponse response = context.response();
    for (Handler<MultiMap> responseHeadersUpdater : responseHeadersUpdaters) {
      responseHeadersUpdater.handle(response.headers());
    }
  }
}
