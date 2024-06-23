package io.vertx.httpproxy.interceptors.impl;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;
import io.vertx.httpproxy.interceptors.MatchInterceptor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

public class MatchInterceptorImpl implements MatchInterceptor {
  private final List<Function<ProxyContext, Future<Boolean>>> requestHandlers = new ArrayList<>();
  private final List<Function<ProxyContext, Future<Boolean>>> responseHandlers = new ArrayList<>();

  private Future<Boolean> iterAsyncHandlers(ProxyContext context, Iterator<Function<ProxyContext, Future<Boolean>>> iterator) {
    if (iterator.hasNext()) {
      return iterator.next().apply(context)
        .compose(useNext -> useNext ? iterAsyncHandlers(context, iterator) : Future.succeededFuture(null));
    } else {
      return Future.succeededFuture(null);
    }
  }

  @Override
  public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
    Iterator<Function<ProxyContext, Future<Boolean>>> iterator = requestHandlers.iterator();
    return iterAsyncHandlers(context, iterator).compose(x -> context.sendRequest());
  }

  @Override
  public Future<Void> handleProxyResponse(ProxyContext context) {
    Iterator<Function<ProxyContext, Future<Boolean>>> iterator = responseHandlers.iterator();
    return iterAsyncHandlers(context, iterator).compose(x -> context.sendResponse());
  }

  @Override
  public MatchInterceptor matchPath(String path) {
    Objects.requireNonNull(path);
    requestHandlers.add(ctx -> {
      boolean matched = StringUtils.matchURL(ctx.request().getURI(), path);
      if (!matched) return Future.succeededFuture(false);
      StringUtils.fillContextWithURL(ctx.request().getURI(), path, ctx);
      return Future.succeededFuture(true);
    });
    return this;
  }

  @Override
  public MatchInterceptor matchPath(BiFunction<ProxyContext, String, Boolean> matcher) {
    Objects.requireNonNull(matcher);
    requestHandlers.add(ctx -> Future.succeededFuture(matcher.apply(ctx, ctx.request().getURI())));
    return this;
  }

  @Override
  public MatchInterceptor transformPath(BiFunction<ProxyContext, String, String> transformer) {
    Objects.requireNonNull(transformer);
    requestHandlers.add(ctx -> {
      ctx.request().setURI(transformer.apply(ctx, ctx.request().getURI()));
      return Future.succeededFuture(true);
    });
    return this;
  }

  @Override
  public MatchInterceptor updatePath(String transformer) {
    Objects.requireNonNull(transformer);
    requestHandlers.add(ctx -> {
      ctx.request().setURI(StringUtils.transformURL(transformer, ctx));
      return Future.succeededFuture(true);
    });
    return this;
  }

  @Override
  public MatchInterceptor addPathPrefix(String prefix) {
    Objects.requireNonNull(prefix);
    requestHandlers.add(ctx -> {
      String prefixActual = StringUtils.transformURL(prefix, ctx);
      ctx.request().setURI(prefixActual + ctx.request().getURI());
      return Future.succeededFuture(true);
    });
    return this;
  }

  @Override
  public MatchInterceptor removePathPrefix(String prefix) {
    Objects.requireNonNull(prefix);
    requestHandlers.add(ctx -> {
      String prefixActual = StringUtils.transformURL(prefix, ctx);
      String oldURI = ctx.request().getURI();
      if (oldURI.startsWith(prefixActual)) {
        ctx.request().setURI(oldURI.substring(prefixActual.length()));
      }
      return Future.succeededFuture(true);
    });
    return this;
  }

  @Override
  public MatchInterceptor matchParams(String paramName, String alias) {
    return null;
  }

  @Override
  public MatchInterceptor matchParams(String paramName) {
    return null;
  }

  @Override
  public MatchInterceptor transformParams(BiFunction<ProxyContext, MultiMap, MultiMap> transformer) {
    return null;
  }

  @Override
  public MatchInterceptor updateParams(String name, String value) {
    return null;
  }

  @Override
  public MatchInterceptor removeParams(String name) {
    return null;
  }

  @Override
  public MatchInterceptor matchRequestHeaders(String headerName, String alias) {
    Objects.requireNonNull(headerName);
    Objects.requireNonNull(alias);
    requestHandlers.add(ctx -> {
      String value = ctx.request().headers().get(StringUtils.substitute(headerName, ctx));
      if (value == null) return Future.succeededFuture(false);
      ctx.set(StringUtils.substitute(headerName, ctx), value);
      return Future.succeededFuture(true);
    });
    return this;
  }

  @Override
  public MatchInterceptor matchRequestHeaders(String headerName) {
    Objects.requireNonNull(headerName);
    return matchRequestHeaders(headerName, headerName);
  }

  @Override
  public MatchInterceptor matchRequestHeaders(BiFunction<ProxyContext, MultiMap, Boolean> matcher) {
    Objects.requireNonNull(matcher);
    requestHandlers.add(ctx -> Future.succeededFuture(matcher.apply(ctx, ctx.request().headers())));
    return this;
  }

  @Override
  public MatchInterceptor matchResponseHeaders(String headerName, String alias) {
    Objects.requireNonNull(headerName);
    Objects.requireNonNull(alias);
    responseHandlers.add(ctx -> {
      String value = ctx.response().headers().get(StringUtils.substitute(headerName, ctx));
      if (value == null) return Future.succeededFuture(false);
      ctx.set(StringUtils.substitute(alias, ctx), value);
      return Future.succeededFuture(true);
    });
    return this;
  }

  @Override
  public MatchInterceptor matchResponseHeaders(String headerName) {
    Objects.requireNonNull(headerName);
    return matchResponseHeaders(headerName, headerName);
  }

  @Override
  public MatchInterceptor matchResponseHeaders(BiFunction<ProxyContext, MultiMap, Boolean> matcher) {
    Objects.requireNonNull(matcher);
    responseHandlers.add(ctx -> Future.succeededFuture(matcher.apply(ctx, ctx.response().headers())));
    return this;
  }

  @Override
  public MatchInterceptor transformRequestHeaders(BiFunction<ProxyContext, MultiMap, MultiMap> transformer) {
    Objects.requireNonNull(transformer);
    requestHandlers.add(ctx -> {
      MultiMap newHeaders = transformer.apply(ctx, ctx.request().headers());
      if (newHeaders != ctx.request().headers()) {
        ctx.request().headers().clear().addAll(newHeaders);
      }
      return Future.succeededFuture(true);
    });
    return this;
  }

  @Override
  public MatchInterceptor transformResponseHeaders(BiFunction<ProxyContext, MultiMap, MultiMap> transformer) {
    Objects.requireNonNull(transformer);
    responseHandlers.add(ctx -> {
      MultiMap newHeaders = transformer.apply(ctx, ctx.response().headers());
      if (newHeaders != ctx.response().headers()) {
        ctx.response().headers().clear().addAll(newHeaders);
      }
      return Future.succeededFuture(true);
    });
    return this;
  }

  @Override
  public MatchInterceptor updateRequestHeaders(String name, String value) {
    Objects.requireNonNull(name);
    Objects.requireNonNull(value);
    requestHandlers.add(ctx -> {
      String realName = StringUtils.substitute(name, ctx);
      String realValue = StringUtils.substitute(value, ctx);
      ctx.request().headers().set(realName, realValue);
      return Future.succeededFuture(true);
    });
    return this;
  }

  @Override
  public MatchInterceptor removeRequestHeaders(String name) {
    Objects.requireNonNull(name);
    requestHandlers.add(ctx -> {
      String realName = StringUtils.substitute(name, ctx);
      ctx.request().headers().remove(realName);
      return Future.succeededFuture(true);
    });
    return this;
  }

  @Override
  public MatchInterceptor updateResponseHeaders(String name, String value) {
    Objects.requireNonNull(name);
    Objects.requireNonNull(value);
    responseHandlers.add(ctx -> {
      String realName = StringUtils.substitute(name, ctx);
      String realValue = StringUtils.substitute(value, ctx);
      ctx.response().headers().set(realName, realValue);
      return Future.succeededFuture(true);
    });
    return this;
  }

  @Override
  public MatchInterceptor removeResponseHeaders(String name) {
    Objects.requireNonNull(name);
    responseHandlers.add(ctx -> {
      String realName = StringUtils.substitute(name, ctx);
      ctx.response().headers().remove(realName);
      return Future.succeededFuture(true);
    });
    return this;
  }

  @Override
  public <T> MatchInterceptor matchRequestBody(BiFunction<ProxyContext, T, Boolean> matcher, Class<T> inputRequestType) {
    return null;
  }

  @Override
  public <T> MatchInterceptor matchResponseBody(BiFunction<ProxyContext, T, Boolean> matcher, Class<T> inputResponseType) {
    return null;
  }

  @Override
  public <T> MatchInterceptor transformRequestBody(BiFunction<ProxyContext, T, Object> transformer, Class<T> inputRequestType) {
    return null;
  }

  @Override
  public <T> MatchInterceptor transformResponseBody(BiFunction<ProxyContext, T, Object> transformer, Class<T> inputResponseType) {
    return null;
  }

  @Override
  public MatchInterceptor updateRequestJsonField(String name, String value) {
    return null;
  }

  @Override
  public MatchInterceptor removeRequestJsonField(String name, String value) {
    return null;
  }

  @Override
  public MatchInterceptor updateResponseJsonField(String name, String value) {
    return null;
  }

  @Override
  public MatchInterceptor removeResponseJsonField(String name, String value) {
    return null;
  }

  @Override
  public MatchInterceptor matchHttpMethods(List<HttpMethod> methods, String alias) {
    return null;
  }

  @Override
  public MatchInterceptor transformHttpMethods(BiFunction<ProxyContext, HttpMethod, HttpMethod> transformer) {
    return null;
  }

  @Override
  public MatchInterceptor updateHttpMethods(HttpMethod method) {
    return null;
  }
}
