package io.vertx.httpproxy;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.SocketAddress;

import java.util.function.Function;

/**
 * A provider that creates the request to the <i><b>origin</b></i> server based on {@link ProxyContext}.
 */
@VertxGen
@FunctionalInterface
public interface OriginRequestProvider {

  /**
   * Creates a simple provider for a fixed {@code port} and {@code host}.
   */
  static OriginRequestProvider fixedAddress(int port, String host) {
    return fixedAddress(SocketAddress.inetSocketAddress(port, host));
  }

  /**
   * Creates a simple provider for a fixed {@link SocketAddress}.
   */
  static OriginRequestProvider fixedAddress(SocketAddress address) {
    return new OriginRequestProvider() {
      @Override
      public Future<HttpClientRequest> create(ProxyContext proxyContext) {
        return proxyContext.client().request(new RequestOptions().setServer(address));
      }
    };
  }

  /**
   * Creates a provider that selects the <i><b>origin</b></i> server based on {@link ProxyContext}.
   */
  static OriginRequestProvider selector(Function<ProxyContext, Future<SocketAddress>> selector) {
    return new OriginRequestProvider() {
      @Override
      public Future<HttpClientRequest> create(ProxyContext proxyContext) {
        return selector.apply(proxyContext).flatMap(server -> proxyContext.client().request(new RequestOptions().setServer(server)));
      }
    };
  }

  /**
   * Create the {@link HttpClientRequest} to the origin server for a given {@link ProxyContext}.
   *
   * @param proxyContext the context of the proxied request and response
   * @return a future, completed with the {@link HttpClientRequest} or failed
   */
  Future<HttpClientRequest> create(ProxyContext proxyContext);
}
