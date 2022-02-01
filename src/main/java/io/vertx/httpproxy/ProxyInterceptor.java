package io.vertx.httpproxy;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;

/**
 * A {@link HttpProxy} interceptor.
 */
@VertxGen
public interface ProxyInterceptor {

  /**
   * Handle the proxy request at the stage of this interceptor.
   *
   * @param context the proxy context
   * @return when the request has actually been sent to the origin
   */
  default Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
    return context.sendRequest();
  }

  /**
   * Handle the proxy response at the stage of this interceptor.
   *
   * @param context the proxy context
   * @return when the response has actually been sent to the user-agent
   */
  default Future<Void> handleProxyResponse(ProxyContext context) {
    return context.sendResponse();
  }
}
