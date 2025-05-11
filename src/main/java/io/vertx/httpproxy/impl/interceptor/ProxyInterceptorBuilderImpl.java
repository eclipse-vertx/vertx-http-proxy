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

package io.vertx.httpproxy.impl.interceptor;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.BodyTransformer;
import io.vertx.httpproxy.ProxyInterceptorBuilder;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toUnmodifiableList;

public class ProxyInterceptorBuilderImpl implements ProxyInterceptorBuilder {


  // Fine to use stream builders here, since interceptors are typically configured on application startup and never modified
  private final Stream.Builder<Handler<MultiMap>> queryUpdaters = Stream.builder();
  private final Stream.Builder<Function<String, String>> pathUpdaters = Stream.builder();
  private final Stream.Builder<Handler<MultiMap>> requestHeadersUpdaters = Stream.builder();
  private final Stream.Builder<Handler<MultiMap>> responseHeadersUpdaters = Stream.builder();
  private BodyTransformer modifyRequestBody;
  private BodyTransformer modifyResponseBody;

  @Override
  public ProxyInterceptor build() {
    return new ProxyInterceptorImpl(
      queryUpdaters.build().collect(toUnmodifiableList()),
      pathUpdaters.build().collect(toUnmodifiableList()),
      requestHeadersUpdaters.build().collect(toUnmodifiableList()),
      responseHeadersUpdaters.build().collect(toUnmodifiableList()),
      modifyRequestBody,
      modifyResponseBody
    );
  }

  @Override
  public ProxyInterceptorBuilder transformingQueryParams(Handler<MultiMap> updater) {
    if (updater != null) {
      queryUpdaters.add(updater);
    }
    return this;
  }

  @Override
  public ProxyInterceptorBuilder settingQueryParam(String name, String value) {
    if (name != null && value != null) {
      return transformingQueryParams(map -> map.set(name, value));
    }
    return this;
  }

  @Override
  public ProxyInterceptorBuilder removingQueryParam(String name) {
    if (name != null) {
      return transformingQueryParams(map -> map.remove(name));
    }
    return this;
  }

  @Override
  public ProxyInterceptorBuilder transformingPath(Function<String, String> mutator) {
    if (mutator != null) {
      pathUpdaters.add(mutator);
    }
    return this;
  }

  @Override
  public ProxyInterceptorBuilder addingPathPrefix(String prefix) {
    if (prefix != null) {
      return transformingPath(path -> prefix + path);
    }
    return this;
  }

  @Override
  public ProxyInterceptorBuilder removingPathPrefix(String prefix) {
    if (prefix != null) {
      return transformingPath(path -> {
        return path.startsWith(prefix) ? path.substring(prefix.length()) : path;
      });
    }
    return this;
  }

  @Override
  public ProxyInterceptorBuilder transformingRequestHeaders(Handler<MultiMap> requestHeadersUpdater) {
    if (requestHeadersUpdater != null) {
      requestHeadersUpdaters.add(requestHeadersUpdater);
    }
    return this;
  }

  @Override
  public ProxyInterceptorBuilder transformingResponseHeaders(Handler<MultiMap> responseHeadersUpdater) {
    if (responseHeadersUpdater != null) {
      responseHeadersUpdaters.add(responseHeadersUpdater);
    }
    return this;
  }

  @Override
  public ProxyInterceptorBuilder filteringRequestHeaders(Set<CharSequence> forbiddenRequestHeaders) {
    if (forbiddenRequestHeaders != null) {
      return transformingRequestHeaders(headers -> {
        for (CharSequence cs : forbiddenRequestHeaders) {
          headers.remove(cs);
        }
      });
    }
    return this;
  }

  @Override
  public ProxyInterceptorBuilder filteringResponseHeaders(Set<CharSequence> forbiddenResponseHeaders) {
    if (forbiddenResponseHeaders != null) {
      return transformingResponseHeaders(headers -> {
        for (CharSequence cs : forbiddenResponseHeaders) {
          headers.remove(cs);
        }
      });
    }
    return this;
  }

  @Override
  public ProxyInterceptorBuilder transformingRequestBody(BodyTransformer requestTransformer) {
    this.modifyRequestBody = requestTransformer;
    return this;
  }

  @Override
  public ProxyInterceptorBuilder transformingResponseBody(BodyTransformer responseTransformer) {
    this.modifyResponseBody = responseTransformer;
    return this;
  }
}
