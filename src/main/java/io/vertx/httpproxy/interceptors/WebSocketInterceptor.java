package io.vertx.httpproxy.interceptors;

import io.vertx.codegen.annotations.Unstable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.interceptors.impl.WebSocketInterceptorImpl;

/**
 * Interceptor settings for WebSocket.
 */
@Unstable
@VertxGen
public interface WebSocketInterceptor {

  /**
   * A wrapper to allow interceptor apply to WebSocket handshake packages.
   *
   * @param interceptor the original interceptor
   * @return the generated interceptor
   */
  static ProxyInterceptor allow(ProxyInterceptor interceptor) {
    return new WebSocketInterceptorImpl(interceptor);
  }
}
