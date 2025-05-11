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

package io.vertx.httpproxy;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;

import java.util.Set;
import java.util.function.Function;

import static io.vertx.codegen.annotations.GenIgnore.PERMITTED_TYPE;

/**
 * <p>>Builder class for a customizable interceptor capable of transforming HTTP request/response head attributes
 * (headers, path, query params) as well as transforming the HTTP body.</p
 *
 * <p>Head transformation methods can be invoked multiple times. Operations on the path will be invoked in the order
 * of configuration, that goes for operations on request headers, response headers and query parameters.</p>
 *
 * <p>Body transformation can be achieved with {@link #transformingResponseBody(BodyTransformer)} and
 * {@link #transformingRequestBody(BodyTransformer)}. Body transformation buffer the body content and then applies
 * a transforming function before sending the response.</p>
 */
@VertxGen
@Unstable
public interface ProxyInterceptorBuilder {

  /**
   * @return the proxy interceptor build according to builder requirements
   */
  ProxyInterceptor build();

  /**
   * Apply modifications to the query parameters.
   *
   * @param updater the operation to apply to the request query parameters (can be null, but in this case nothing happens)
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ProxyInterceptorBuilder transformingQueryParams(Handler<MultiMap> updater);

  /**
   * Add a query parameter to the request.
   *
   * @param name the parameter name (can be null, but in this case nothing happens)
   * @param value the parameter value (can be null, but in this case nothing happens)
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ProxyInterceptorBuilder settingQueryParam(String name, String value);

  /**
   * Remove a query parameter from the request.
   *
   * @param name the parameter name (can be null, but in this case nothing happens)
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ProxyInterceptorBuilder removingQueryParam(String name);

  /**
   * Apply a callback to change the request URI when the proxy receives it.
   *
   * @param mutator the operation that applied to the path (can be null, but in this case nothing happens)
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ProxyInterceptorBuilder transformingPath(Function<String, String> mutator);

  /**
   * Add a prefix to the URI.
   *
   * @param prefix the prefix that need to be added (can be null, but in this case nothing happens)
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ProxyInterceptorBuilder addingPathPrefix(String prefix);

  /**
   * Remove a prefix to the URI. Do nothing if it doesn't exist.
   *
   * @param prefix the prefix that need to be removed (can be null, but in this case nothing happens)
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ProxyInterceptorBuilder removingPathPrefix(String prefix);

  /**
   * Apply callbacks to change the request headers when the proxy receives them.
   *
   * @param requestHeadersUpdater the operation to apply to the request headers (can be null, but in this case nothing happens)
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ProxyInterceptorBuilder transformingRequestHeaders(Handler<MultiMap> requestHeadersUpdater);

  /**
   * Apply callbacks to change the response headers when the proxy receives them.
   *
   * @param responseHeadersUpdater the operation to apply to the response headers (can be null, but in this case nothing happens)
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  ProxyInterceptorBuilder transformingResponseHeaders(Handler<MultiMap> responseHeadersUpdater);

  /**
   * Filter the request headers in the given set.
   *
   * @param forbiddenRequestHeaders a set of the headers that need to be filtered (can be null, but in this case nothing happens)
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore(PERMITTED_TYPE)
  @Fluent
  ProxyInterceptorBuilder filteringRequestHeaders(Set<CharSequence> forbiddenRequestHeaders);

  /**
   * Filter the response headers in the given set.
   *
   * @param forbiddenResponseHeaders a set of the headers that need to be filtered (can be null, but in this case nothing happens)
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore(PERMITTED_TYPE)
  @Fluent
  ProxyInterceptorBuilder filteringResponseHeaders(Set<CharSequence> forbiddenResponseHeaders);

  /**
   * <p>Apply a transformation to change the request body when the proxy receives it.</p>
   *
   * <p>The interceptor fully buffers the request body and then applies the transformation.</p>
   *
   * @param requestTransformer the operation to apply to the request body
   * @return the created interceptor
   */
  @Fluent
  ProxyInterceptorBuilder transformingRequestBody(BodyTransformer requestTransformer);

  /**
   * <p>Apply a transformation to change the response body when the proxy receives it.</p>
   *
   * <p>The interceptor fully buffers the response body and then applies the transformation.</p>
   *
   * @param responseTransformer the operation to apply to the response body
   * @return the created interceptor
   */
  @Fluent
  ProxyInterceptorBuilder transformingResponseBody(BodyTransformer responseTransformer);

}
