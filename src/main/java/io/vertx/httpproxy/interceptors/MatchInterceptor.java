package io.vertx.httpproxy.interceptors;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.httpproxy.ProxyContext;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public interface MatchInterceptor {
  static MatchInterceptor builder() {
    return null;
  }

  // Path:
  MatchInterceptor matchPath(String path);
  MatchInterceptor matchPath(BiFunction<ProxyContext, String, Boolean> matcher);

  MatchInterceptor transformPath(BiFunction<ProxyContext, String, String> transformer);

  MatchInterceptor updatePath(String transformer);
  MatchInterceptor addPathPrefix(String prefix);
  MatchInterceptor removePathPrefix(String prefix);

  // Params:
  MatchInterceptor matchParams(String paramName, String alias);
  MatchInterceptor matchParams(String paramName);

  MatchInterceptor transformParams(BiFunction<ProxyContext, MultiMap, MultiMap> transformer);

  MatchInterceptor updateParam(String name, String value);
  MatchInterceptor removeParam(String name);


  // Headers:
  MatchInterceptor matchRequestHeaders(String paramName, String alias);
  MatchInterceptor matchRequestHeaders(String paramName);
  MatchInterceptor matchRequestHeaders(BiFunction<ProxyContext, MultiMap, Boolean> matcher);
  MatchInterceptor matchResponseHeaders(String paramName, String alias);
  MatchInterceptor matchResponseHeaders(String paramName);
  MatchInterceptor matchResponseHeaders(BiFunction<ProxyContext, MultiMap, Boolean> matcher);

  MatchInterceptor transformRequestHeaders(BiFunction<ProxyContext, MultiMap, MultiMap> transformer);
  MatchInterceptor transformResponseHeaders(BiFunction<ProxyContext, MultiMap, MultiMap> transformer);

  MatchInterceptor updateRequestHeaders(String name, String value);
  MatchInterceptor removeRequestHeaders(String name);
  MatchInterceptor updateResponseHeaders(String name, String value);
  MatchInterceptor removeResponseHeaders(String name);


  // Body:
  <T> MatchInterceptor matchRequestBody(BiFunction<ProxyContext, T, Boolean> matcher);
  <T> MatchInterceptor matchResponseBody(BiFunction<ProxyContext, T, Boolean> matcher);

  <T, R> MatchInterceptor transformRequestBody(BiFunction<ProxyContext, T, R> transformer);
  <T, R> MatchInterceptor transformResponseBody(BiFunction<ProxyContext, T, R> transformer);

  MatchInterceptor updateRequestJsonField(String name, String value);
  MatchInterceptor removeRequestJsonField(String name, String value);
  MatchInterceptor updateResponseJsonField(String name, String value);
  MatchInterceptor removeResponseJsonField(String name, String value);

  // HTTP method:
  MatchInterceptor matchHttpMethods(List<HttpMethod> methods, String alias);

  MatchInterceptor transformHttpMethods(BiFunction<ProxyContext, HttpMethod, HttpMethod> transformer);

  MatchInterceptor updateHttpMethods(HttpMethod method);

}
