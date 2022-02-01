package io.vertx.httpproxy;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;

/**
 * A controller for proxy interception.
 */
@VertxGen
public interface ProxyContext {

  /**
   * @return the proxy request
   */
  ProxyRequest request();

  /**
   * @return the proxy response, it might be {@code null} if the response has not been sent
   */
  ProxyResponse response();

  /**
   *
   */
  Future<ProxyResponse> sendRequest();

  /**
   *
   */
  Future<Void> sendResponse();

  /**
   * Attach a payload to the context
   *
   * @param name the payload name
   * @param value any payload value
   */
  void set(String name, Object value);

  /**
   * Get a payload attached to this context
   *
   * @param name the payload name
   * @param type the expected payload type
   * @return the attached payload
   */
  <T> T get(String name, Class<T> type);
}
