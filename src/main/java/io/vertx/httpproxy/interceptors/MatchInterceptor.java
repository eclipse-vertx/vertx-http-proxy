package io.vertx.httpproxy.interceptors;

import io.vertx.codegen.annotations.Unstable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.interceptors.impl.MatchInterceptorImpl;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

@VertxGen
@Unstable
public interface MatchInterceptor extends ProxyInterceptor {
  static MatchInterceptor builder() {
    return new MatchInterceptorImpl();
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

  MatchInterceptor updateParams(String name, String value);
  MatchInterceptor removeParams(String name);


  // Headers:
  MatchInterceptor matchRequestHeaders(String headerName, String alias);
  MatchInterceptor matchRequestHeaders(String headerName);
  MatchInterceptor matchRequestHeaders(BiFunction<ProxyContext, MultiMap, Boolean> matcher);
  MatchInterceptor matchResponseHeaders(String headerName, String alias);
  MatchInterceptor matchResponseHeaders(String headerName);
  MatchInterceptor matchResponseHeaders(BiFunction<ProxyContext, MultiMap, Boolean> matcher);

  MatchInterceptor transformRequestHeaders(BiFunction<ProxyContext, MultiMap, MultiMap> transformer);
  MatchInterceptor transformResponseHeaders(BiFunction<ProxyContext, MultiMap, MultiMap> transformer);

  MatchInterceptor updateRequestHeaders(String name, String value);
  MatchInterceptor removeRequestHeaders(String name);
  MatchInterceptor updateResponseHeaders(String name, String value);
  MatchInterceptor removeResponseHeaders(String name);


  // Body:
  <T> MatchInterceptor matchRequestBody(BiFunction<ProxyContext, T, Boolean> matcher, Class<T> inputRequestType);
  <T> MatchInterceptor matchResponseBody(BiFunction<ProxyContext, T, Boolean> matcher, Class<T> inputResponseType);

  <T> MatchInterceptor transformRequestBody(BiFunction<ProxyContext, T, Object> transformer, Class<T> inputRequestType);
  <T> MatchInterceptor transformResponseBody(BiFunction<ProxyContext, T, Object> transformer, Class<T> inputResponseType);

  MatchInterceptor updateRequestJsonField(String name, String value);
  MatchInterceptor removeRequestJsonField(String name, String value);
  MatchInterceptor updateResponseJsonField(String name, String value);
  MatchInterceptor removeResponseJsonField(String name, String value);

  // HTTP method:
  MatchInterceptor matchHttpMethods(List<HttpMethod> methods, String alias);

  MatchInterceptor transformHttpMethods(BiFunction<ProxyContext, HttpMethod, HttpMethod> transformer);

  MatchInterceptor updateHttpMethods(HttpMethod method);

}
