package io.vertx.httpproxy;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.httpproxy.impl.ProxyEventImpl;

/**
 * A ProxyEvent can represent a response between the proxy server and the
 * origin or between the proxy server and the user agent. This will be clear from the context.
 */
@VertxGen
public interface ProxyEvent {

  /**
   * Get the status code.
   *
   * @return the status code
   */
  int getStatusCode();

  /**
   *
   * Return the original outbound {@code HttpServerRequest}. This can be used to associate the event with the original
   * request to be proxied.
   *
   * @return the outbound request
   */
  HttpServerRequest outboundRequest();

  /**
   * Alter the status code associated with the event.
   * @param statusCode the status code
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ProxyEvent withStatusCode(int statusCode);

  /**
   * Whether the outbound or inbound proxy request associated with this event was successful or not.
   *
   * @return whether the proxy was successful
   */
  boolean failed();

  static ProxyEvent fromResponse(ProxyResponse response) {
    return new ProxyEventImpl(response);
  };

  /**
   * Create a ProxyEvent from a ProxyResponse and mark it as failed.
   *
   * @param response
   * @return
   */
  static ProxyEvent failedResponse(ProxyResponse response) {
    return new ProxyEventImpl(response).setFailed();
  };

  static ProxyEvent fromRequest(ProxyRequest request) {
    return new ProxyEventImpl(request);
  };

  /**
   * Create a ProxyEvent from a ProxyRequest and mark it as failed.
   *
   * @param request
   * @return
   */
  static ProxyEvent failedRequest(ProxyRequest request) {
    return new ProxyEventImpl(request).setFailed();
  };
}
