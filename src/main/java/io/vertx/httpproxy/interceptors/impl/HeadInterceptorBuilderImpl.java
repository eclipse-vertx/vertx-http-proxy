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

package io.vertx.httpproxy.interceptors.impl;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.httpproxy.interceptors.HeadInterceptor;
import io.vertx.httpproxy.interceptors.HeadInterceptorBuilder;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toUnmodifiableList;

public class HeadInterceptorBuilderImpl implements HeadInterceptorBuilder {

  // Fine to use stream builders here, since interceptors are typically configured on application startup and never modified
  private final Stream.Builder<Handler<MultiMap>> queryUpdaters = Stream.builder();
  private final Stream.Builder<Function<String, String>> pathUpdaters = Stream.builder();
  private final Stream.Builder<Handler<MultiMap>> requestHeadersUpdaters = Stream.builder();
  private final Stream.Builder<Handler<MultiMap>> responseHeadersUpdaters = Stream.builder();

  @Override
  public HeadInterceptor build() {
    return new HeadInterceptorImpl(
      queryUpdaters.build().collect(toUnmodifiableList()),
      pathUpdaters.build().collect(toUnmodifiableList()),
      requestHeadersUpdaters.build().collect(toUnmodifiableList()),
      responseHeadersUpdaters.build().collect(toUnmodifiableList())
    );
  }

  @Override
  public HeadInterceptorBuilder updatingQueryParams(Handler<MultiMap> updater) {
    if (updater != null) {
      queryUpdaters.add(updater);
    }
    return this;
  }

  @Override
  public HeadInterceptorBuilder settingQueryParam(String name, String value) {
    if (name != null && value != null) {
      return updatingQueryParams(map -> map.set(name, value));
    }
    return this;
  }

  @Override
  public HeadInterceptorBuilder removingQueryParam(String name) {
    if (name != null) {
      return updatingQueryParams(map -> map.remove(name));
    }
    return this;
  }

  @Override
  public HeadInterceptorBuilder updatingPath(Function<String, String> mutator) {
    if (mutator != null) {
      pathUpdaters.add(mutator);
    }
    return this;
  }

  @Override
  public HeadInterceptorBuilder addingPathPrefix(String prefix) {
    if (prefix != null) {
      return updatingPath(path -> prefix + path);
    }
    return this;
  }

  @Override
  public HeadInterceptorBuilder removingPathPrefix(String prefix) {
    if (prefix != null) {
      return updatingPath(path -> {
        return path.startsWith(prefix) ? path.substring(prefix.length()) : path;
      });
    }
    return this;
  }

  @Override
  public HeadInterceptorBuilder updatingRequestHeaders(Handler<MultiMap> requestHeadersUpdater) {
    if (requestHeadersUpdater != null) {
      requestHeadersUpdaters.add(requestHeadersUpdater);
    }
    return this;
  }

  @Override
  public HeadInterceptorBuilder updatingResponseHeaders(Handler<MultiMap> responseHeadersUpdater) {
    if (responseHeadersUpdater != null) {
      responseHeadersUpdaters.add(responseHeadersUpdater);
    }
    return this;
  }

  @Override
  public HeadInterceptorBuilder filteringRequestHeaders(Set<CharSequence> forbiddenRequestHeaders) {
    if (forbiddenRequestHeaders != null) {
      return updatingRequestHeaders(headers -> {
        for (CharSequence cs : forbiddenRequestHeaders) {
          headers.remove(cs);
        }
      });
    }
    return this;
  }

  @Override
  public HeadInterceptorBuilder filteringResponseHeaders(Set<CharSequence> forbiddenResponseHeaders) {
    if (forbiddenResponseHeaders != null) {
      return updatingResponseHeaders(headers -> {
        for (CharSequence cs : forbiddenResponseHeaders) {
          headers.remove(cs);
        }
      });
    }
    return this;
  }
}
