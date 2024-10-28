package io.vertx.httpproxy.interceptors.impl;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.httpproxy.interceptors.HeadInterceptor;
import io.vertx.httpproxy.interceptors.HeadInterceptorBuilder;

import java.util.Set;
import java.util.function.Function;

public class HeadInterceptorBuilderImpl implements HeadInterceptorBuilder {

  private Handler<MultiMap> queryUpdater;
  private Function<String, String> pathUpdater;
  private Handler<MultiMap> requestHeadersUpdater;
  private Handler<MultiMap> responseHeadersUpdater;

  @Override
  public HeadInterceptor build() {
    return new HeadInterceptorImpl(queryUpdater, pathUpdater, requestHeadersUpdater, responseHeadersUpdater);
  }

  @Override
  public HeadInterceptorBuilder updatingQueryParams(Handler<MultiMap> updater) {
    queryUpdater = updater;
    return this;
  }

  @Override
  public HeadInterceptorBuilder settingQueryParam(String name, String value) {
    return updatingQueryParams(map -> map.set(name, value));
  }

  @Override
  public HeadInterceptorBuilder removingQueryParam(String name) {
    return updatingQueryParams(map -> map.remove(name));
  }

  @Override
  public HeadInterceptorBuilder updatingPath(Function<String, String> mutator) {
    pathUpdater = mutator;
    return this;
  }

  @Override
  public HeadInterceptorBuilder addingPathPrefix(String prefix) {
    return updatingPath(path -> prefix + path);
  }

  @Override
  public HeadInterceptorBuilder removingPathPrefix(String prefix) {
    return updatingPath(path -> {
      if (path.startsWith(prefix)) {
        return path.substring(prefix.length());
      } else {
        return prefix;
      }
    });
  }

  @Override
  public HeadInterceptorBuilder updatingHeaders(Handler<MultiMap> requestHeadersMutator, Handler<MultiMap> responseHeadersUpdater) {
    requestHeadersUpdater = requestHeadersMutator;
    this.responseHeadersUpdater = responseHeadersUpdater;
    return this;
  }

  @Override
  public HeadInterceptorBuilder filteringRequestHeaders(Set<CharSequence> forbiddenRequestHeaders) {
    return filteringHeaders(forbiddenRequestHeaders, null);
  }

  @Override
  public HeadInterceptorBuilder filteringResponseHeaders(Set<CharSequence> forbiddenResponseHeaders) {
    return filteringHeaders(null, forbiddenResponseHeaders);
  }

  @Override
  public HeadInterceptorBuilder filteringHeaders(Set<CharSequence> forbiddenRequestHeaders, Set<CharSequence> forbiddenResponseHeaders) {
    return updatingHeaders(forbiddenRequestHeaders != null ? headers -> {
      for (CharSequence cs : forbiddenRequestHeaders) {
        headers.remove(cs);
      }
    } : requestHeadersUpdater, forbiddenResponseHeaders != null ? headers -> {
      for (CharSequence cs : forbiddenResponseHeaders) {
        headers.remove(cs);
      }
    } : responseHeadersUpdater);
  }
}
