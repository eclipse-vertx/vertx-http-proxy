package io.vertx.httpproxy.interceptors;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;

import java.util.Set;
import java.util.function.Function;

import static io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE;

@VertxGen
@Unstable
public interface HeadInterceptorBuilder {

  /**
   * @return an interceptor build according to builder requirements
   */
  HeadInterceptor build();

  /**
   * Apply modifications to the query parameters.
   *
   * @param updater the operation to apply to the request query parameters
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HeadInterceptorBuilder updatingQueryParams(Handler<MultiMap> updater);

  /**
   * Add a query parameter to the request.
   *
   * @param name the parameter name
   * @param value the parameter value
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HeadInterceptorBuilder settingQueryParam(String name, String value);

  /**
   * Remove a query parameter from the request.
   *
   * @param name the parameter name
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HeadInterceptorBuilder removingQueryParam(String name);

  /**
   * Apply a callback to change the request URI when the proxy receives it.
   *
   * @param mutator the operation that applied to the path
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HeadInterceptorBuilder updatingPath(Function<String, String> mutator);

  /**
   * Add a prefix to the URI.
   *
   * @param prefix the prefix that need to be added
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HeadInterceptorBuilder addingPathPrefix(String prefix);

  /**
   * Remove a prefix to the URI. Do nothing if it doesn't exist.
   *
   * @param prefix the prefix that need to be removed
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HeadInterceptorBuilder removingPathPrefix(String prefix);

  /**
   * Apply callbacks to change the request and response headers when the proxy receives them.
   *
   * @param requestHeadersMutator the operation to apply to the request headers
   * @param responseHeadersUpdater the operation to apply to the response headers
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HeadInterceptorBuilder updatingHeaders(Handler<MultiMap> requestHeadersMutator, Handler<MultiMap> responseHeadersUpdater);

  /**
   * Filter the request headers in the given set.
   *
   * @param forbiddenRequestHeaders a set of the headers that need to be filtered
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore(PERMITTED_TYPE)
  @Fluent
  HeadInterceptorBuilder filteringRequestHeaders(Set<CharSequence> forbiddenRequestHeaders);

  /**
   * Filter the response headers in the given set.
   *
   * @param forbiddenResponseHeaders a set of the headers that need to be filtered
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore(PERMITTED_TYPE)
  @Fluent
  HeadInterceptorBuilder filteringResponseHeaders(Set<CharSequence> forbiddenResponseHeaders);

  /**
   * Filter the request and response headers in the given sets.
   *
   * @param forbiddenRequestHeaders a set of the request headers that need to be filtered
   * @param forbiddenResponseHeaders a set of the response headers that need to be filtered
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore(PERMITTED_TYPE)
  @Fluent
  HeadInterceptorBuilder filteringHeaders(Set<CharSequence> forbiddenRequestHeaders, Set<CharSequence> forbiddenResponseHeaders);
}
