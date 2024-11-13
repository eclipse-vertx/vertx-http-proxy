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

/**
 * Configuration for an interceptor updating HTTP request/response head attributes (headers, path, query params).
 * <p>
 * All configuration methods can be invoked several times.
 * Operations on the path will be invoked in the order of configuration.
 * That goes for operations on request headers, response headers and query parameters.
 */
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
   * @param updater the operation to apply to the request query parameters (can be null, but in this case nothing happens)
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HeadInterceptorBuilder updatingQueryParams(Handler<MultiMap> updater);

  /**
   * Add a query parameter to the request.
   *
   * @param name the parameter name (can be null, but in this case nothing happens)
   * @param value the parameter value (can be null, but in this case nothing happens)
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HeadInterceptorBuilder settingQueryParam(String name, String value);

  /**
   * Remove a query parameter from the request.
   *
   * @param name the parameter name (can be null, but in this case nothing happens)
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HeadInterceptorBuilder removingQueryParam(String name);

  /**
   * Apply a callback to change the request URI when the proxy receives it.
   *
   * @param mutator the operation that applied to the path (can be null, but in this case nothing happens)
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HeadInterceptorBuilder updatingPath(Function<String, String> mutator);

  /**
   * Add a prefix to the URI.
   *
   * @param prefix the prefix that need to be added (can be null, but in this case nothing happens)
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HeadInterceptorBuilder addingPathPrefix(String prefix);

  /**
   * Remove a prefix to the URI. Do nothing if it doesn't exist.
   *
   * @param prefix the prefix that need to be removed (can be null, but in this case nothing happens)
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HeadInterceptorBuilder removingPathPrefix(String prefix);

  /**
   * Apply callbacks to change the request headers when the proxy receives them.
   *
   * @param requestHeadersUpdater the operation to apply to the request headers (can be null, but in this case nothing happens)
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HeadInterceptorBuilder updatingRequestHeaders(Handler<MultiMap> requestHeadersUpdater);

  /**
   * Apply callbacks to change the response headers when the proxy receives them.
   *
   * @param responseHeadersUpdater the operation to apply to the response headers (can be null, but in this case nothing happens)
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HeadInterceptorBuilder updatingResponseHeaders(Handler<MultiMap> responseHeadersUpdater);

  /**
   * Filter the request headers in the given set.
   *
   * @param forbiddenRequestHeaders a set of the headers that need to be filtered (can be null, but in this case nothing happens)
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore(PERMITTED_TYPE)
  @Fluent
  HeadInterceptorBuilder filteringRequestHeaders(Set<CharSequence> forbiddenRequestHeaders);

  /**
   * Filter the response headers in the given set.
   *
   * @param forbiddenResponseHeaders a set of the headers that need to be filtered (can be null, but in this case nothing happens)
   * @return a reference to this, so the API can be used fluently
   */
  @GenIgnore(PERMITTED_TYPE)
  @Fluent
  HeadInterceptorBuilder filteringResponseHeaders(Set<CharSequence> forbiddenResponseHeaders);
}
