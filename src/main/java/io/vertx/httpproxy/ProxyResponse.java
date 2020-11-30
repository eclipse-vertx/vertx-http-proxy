package io.vertx.httpproxy;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.streams.ReadStream;

import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface ProxyResponse {

  /**
   * @return the proxy request
   */
  ProxyRequest request();

  /**
   * @return the status code to be sent to the edge client
   */
  int getStatusCode();

  /**
   * Set the status code to be sent to the edge client.
   *
   * <p> The initial value is the origin response status code.
   *
   * @param sc the status code
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ProxyResponse setStatusCode(int sc);

  /**
   * @return the headers that will be sent to the edge client, the returned headers can be modified. The headers
   *         map is populated with the origin response headers
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
  ProxyResponse putHeader(CharSequence name, CharSequence value);

  /**
   * @return the response body to be sent to the edge client
   */
  Body getBody();

  /**
   * Set the request body to be sent to the edge client.
   *
   * <p>The initial request body value is the origin response body.
   *
   * @param body the new body
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ProxyResponse setBody(Body body);

  /**
   * Set a body filter.
   *
   * <p> The body filter can rewrite the response body sent to the edge client.
   *
   * @param filter the filter
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ProxyResponse bodyFilter(Function<ReadStream<Buffer>, ReadStream<Buffer>> filter);

  boolean publicCacheControl();

  long maxAge();

  /**
   * @return the {@code etag} sent by the origin response
   */
  String etag();

  /**
   * Send the proxy response to the edge client.
   *
   * @param completionHandler the handler to be called when the response has been sent
   */
  void send(Handler<AsyncResult<Void>> completionHandler);

  /**
   * Release the proxy response.
   *
   * <p> The HTTP client response is resumed, no HTTP server response is sent.
   */
  @Fluent
  ProxyResponse release();

}
