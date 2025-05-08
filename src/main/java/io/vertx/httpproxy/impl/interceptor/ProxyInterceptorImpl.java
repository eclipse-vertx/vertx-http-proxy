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

import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.QueryStringEncoder;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.streams.ReadStream;
import io.vertx.httpproxy.*;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

class ProxyInterceptorImpl implements ProxyInterceptor {

  private static final Function<Buffer, Buffer> NO_OP = buffer -> buffer;

  private final List<Handler<MultiMap>> queryUpdaters;
  private final List<Function<String, String>> pathUpdaters;
  private final List<Handler<MultiMap>> requestHeadersUpdaters;
  private final List<Handler<MultiMap>> responseHeadersUpdaters;
  private final long requestMaxBufferedSize;
  private final Function<Buffer, Buffer> modifyRequestBody;
  private final long responseMaxBufferedSize;
  private final Function<Buffer, Buffer> modifyResponseBody;

  ProxyInterceptorImpl(
    List<Handler<MultiMap>> queryUpdaters,
    List<Function<String, String>> pathUpdaters,
    List<Handler<MultiMap>> requestHeadersUpdaters,
    List<Handler<MultiMap>> responseHeadersUpdaters,
    long requestMaxBufferedSize,
    Function<Buffer, Buffer> modifyRequestBody,
    long responseMaxBufferedSize,
    Function<Buffer, Buffer> modifyResponseBody) {
    this.queryUpdaters = Objects.requireNonNull(queryUpdaters);
    this.pathUpdaters = Objects.requireNonNull(pathUpdaters);
    this.requestHeadersUpdaters = Objects.requireNonNull(requestHeadersUpdaters);
    this.responseHeadersUpdaters = Objects.requireNonNull(responseHeadersUpdaters);
    this.requestMaxBufferedSize = requestMaxBufferedSize;
    this.modifyRequestBody = modifyRequestBody;
    this.responseMaxBufferedSize = responseMaxBufferedSize;
    this.modifyResponseBody = modifyResponseBody;
  }

  @Override
  public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
    try {
      queryHandleProxyRequest(context);
      pathHandleProxyRequest(context);
      headersHandleProxyRequest(context);
    } catch (Exception e) {
      failRequest(context);
      return Future.failedFuture(e);
    }
    if (modifyRequestBody != null) {
      Promise<ProxyResponse> ret = Promise.promise();
      BodyAccumulator bodyAccumulator = new BodyAccumulator(modifyResponseBody, requestMaxBufferedSize, (body, err) -> {
        if (err == null) {
          ProxyRequest request = context.request();
          request.setBody(Body.body(body));
          context.sendRequest().onComplete(ret);
        } else {
          failRequest(context);
          ret.fail(err);
        }
      });
      Body body = context.request().getBody();
      ReadStream<Buffer> stream = body.stream();
      stream.handler(bodyAccumulator::handleBuffer);
      stream.endHandler(bodyAccumulator::handleEnd);
      stream.resume();
      return ret.future();
    } else {
      return context.sendRequest();
    }
  }

  private void failRequest(ProxyContext context) {
    // Currently there is no way to achieve this using the API (which is an issue)
    // So we need to bypass the whole proxy mechanism until we get better
    HttpServerRequest actualRequest = context.request().proxiedRequest();
    actualRequest.response().setStatusCode(500).end();
  }

  private static class BodyAccumulator {

    private final long maxBufferedBytes;
    private final Function<Buffer, Buffer> transformer;
    private final Completable<Buffer> completion;

    private Buffer accumulator = Buffer.buffer();

    BodyAccumulator(Function<Buffer, Buffer> transformer, long maxBufferedBytes, Completable<Buffer> completion) {
      this.transformer = transformer;
      this.maxBufferedBytes = maxBufferedBytes;
      this.completion = completion;
    }

    void handleBuffer(Buffer buffer) {
      if (accumulator != null) {
        accumulator.appendBuffer(buffer);
        if (buffer.length() > maxBufferedBytes) {
          accumulator = null;
        }
      }
    }

    void handleEnd(Void end) {
      if (accumulator != null) {
        Buffer body = transformBody(accumulator);
        accumulator = null;
        if (body != null) {
          completion.succeed(body);
          return;
        }
      } else {
        // Overflow
      }
      completion.fail(new VertxException("", true));
    }

    private Buffer transformBody(Buffer body) {
      try {
        return transformer.apply(body);
      } catch (Exception e) {
        return null;
      }
    }
  }

  @Override
  public Future<Void> handleProxyResponse(ProxyContext context) {
    try {
      headersHandleProxyResponse(context);
    } catch (Exception e) {
      return failResponse(context);
    }
    if (modifyResponseBody != null) {
      Promise<Void> ret = Promise.promise();
      BodyAccumulator bodyAccumulator = new BodyAccumulator(modifyResponseBody, responseMaxBufferedSize, (body, err) -> {
        ProxyResponse response = context.response();
        if (err == null) {
          response.setBody(Body.body(body));
          context.sendResponse();
        } else {
          failResponse(context).onComplete(ret);
        }
      });
      ReadStream<Buffer> stream = context.response().getBody().stream();
      stream.handler(bodyAccumulator::handleBuffer);
      stream.endHandler(bodyAccumulator::handleEnd);
      stream.resume();
      return ret.future();
    } else {
      return context.sendResponse();
    }
  }

  private Future<Void> failResponse(ProxyContext context) {
    ProxyResponse response = context.response();
    response.setStatusCode(500);
    response.setStatusMessage(null);
    response.setBody(Body.body(Buffer.buffer())); // Empty
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
