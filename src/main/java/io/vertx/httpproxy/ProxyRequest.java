package io.vertx.httpproxy;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.streams.ReadStream;
import io.vertx.httpproxy.impl.ProxyRequestImpl;

import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface ProxyRequest {

  static ProxyRequest reverseProxy(HttpServerRequest request) {
    request.pause();
    ProxyRequestImpl proxyRequest = new ProxyRequestImpl(request);
    return proxyRequest;
  }

  /**
   * @return the HTTP version of the edge request
   */
  HttpVersion version();

  /**
   * @return the edge request absolute URI
   */
  String absoluteURI();

  /**
   * @return the HTTP method to be sent to the origin server
   */
  HttpMethod getMethod();

  /**
   * Set the HTTP method to be sent to the origin server.
   *
   * <p>The initial HTTP method value is the edge request HTTP method.
   *
   * @param method the new method
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ProxyRequest setMethod(HttpMethod method);

  /**
   * @return the request URI to be sent to the origin server
   */
  String getURI();

  /**
   * Set the request URI to be sent to the origin server.
   *
   * <p>The initial request URI value is the edge request URI.
   *
   * @param uri the new URI
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ProxyRequest setURI(String uri);

  /**
   * @return the request body to be sent to the origin server
   */
  Body getBody();

  /**
   * Set the request body to be sent to the origin server.
   *
   * <p>The initial request body value is the edge request body.
   *
   * @param body the new body
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ProxyRequest setBody(Body body);

  /**
   * @return the headers that will be sent to the origin server, the returned headers can be modified. The headers
   *         map is populated with the edge request headers
   */
  MultiMap headers();

  /**
   * Put an HTTP header
   *
   * @param name  The header name
   * @param value The header value
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore
  @Fluent
  ProxyRequest putHeader(CharSequence name, CharSequence value);

  /**
   * Set a body filter.
   *
   * <p> The body filter can rewrite the request body sent to the origin server.
   *
   * @param filter the filter
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ProxyRequest bodyFilter(Function<ReadStream<Buffer>, ReadStream<Buffer>> filter);

  /**
   * Proxy this request and response to the origin server using the specified request.
   *
   * @param request the request connected to the origin server
   * @param completionHandler the completion handler
   */
  default void proxy(HttpClientRequest request, Handler<AsyncResult<Void>> completionHandler) {
    send(request, ar -> {
      if (ar.succeeded()) {
        ProxyResponse resp = ar.result();
        resp.send(completionHandler);
      } else {
        completionHandler.handle(ar.mapEmpty());
      }
    });
  }

  /**
   * Send this request to the origin server using the specified request.
   *
   * <p> The {@code completionHandler} will be called with the proxy response sent by the origin server.
   *
   * @param request the request connected to the origin server
   * @param completionHandler the completion handler
   */
  void send(HttpClientRequest request, Handler<AsyncResult<ProxyResponse>> completionHandler);

  /**
   * Release the proxy request.
   *
   * <p> The HTTP server request is resumed, no HTTP server response is sent.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ProxyRequest release();

  /**
   * Create and return a default proxy response.
   *
   * @return a default proxy response
   */
  ProxyResponse response();

}
