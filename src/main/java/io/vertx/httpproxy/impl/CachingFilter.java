package io.vertx.httpproxy.impl;

import io.vertx.core.Future;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;
import io.vertx.httpproxy.spi.cache.Cache;

import java.util.Date;
import java.util.function.BiFunction;

class CachingFilter implements ProxyInterceptor {

  private static final BiFunction<String, Resource, Resource> CACHE_GET_AND_VALIDATE = (key, resource) -> {
    long now = System.currentTimeMillis();
    long val = resource.timestamp + resource.maxAge;
    return val < now ? null : resource;
  };

  private final Cache<String, Resource> cache;

  public CachingFilter(Cache<String, Resource> cache) {
    this.cache = cache;
  }

  @Override
  public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
    Future<ProxyResponse> future = tryHandleProxyRequestFromCache(context);
    if (future != null) {
      return future;
    }
    return context.sendRequest();
  }

  @Override
  public Future<Void> handleProxyResponse(ProxyContext context) {
    return sendAndTryCacheProxyResponse(context);
  }

  private Future<Void> sendAndTryCacheProxyResponse(ProxyContext context) {

    ProxyResponse response = context.response();
    Resource cached = context.get("cached_resource", Resource.class);

    if (cached != null && response.getStatusCode() == 304) {
      // Warning: this relies on the fact that HttpServerRequest will not send a body for HEAD
      response.release();
      cached.init(response);
      return context.sendResponse();
    }

    ProxyRequest request = response.request();
    if (response.publicCacheControl() && response.maxAge() > 0) {
      if (request.getMethod() == HttpMethod.GET) {
        String absoluteUri = request.absoluteURI();
        Resource res = new Resource(
          absoluteUri,
          response.getStatusCode(),
          response.getStatusMessage(),
          response.headers(),
          System.currentTimeMillis(),
          response.maxAge());
        Body body = response.getBody();
        response.setBody(Body.body(new BufferingReadStream(body.stream(), res.content), body.length()));
        Future<Void> fut = context.sendResponse();
        fut.onSuccess(v -> {
          cache.put(absoluteUri, res);
        });
        return fut;
      } else {
        if (request.getMethod() == HttpMethod.HEAD) {
          Resource resource = cache.get(request.absoluteURI());
          if (resource != null) {
            if (!revalidateResource(response, resource)) {
              // Invalidate cache
              cache.remove(request.absoluteURI());
            }
          }
        }
        return context.sendResponse();
      }
    } else {
      return context.sendResponse();
    }
  }

  private static boolean revalidateResource(ProxyResponse response, Resource resource) {
    if (resource.etag != null && response.etag() != null) {
      return resource.etag.equals(response.etag());
    }
    return true;
  }

  private Future<ProxyResponse> tryHandleProxyRequestFromCache(ProxyContext context) {

    ProxyRequest proxyRequest = context.request();

    HttpServerRequest response = proxyRequest.proxiedRequest();

    Resource resource;
    HttpMethod method = response.method();
    if (method == HttpMethod.GET || method == HttpMethod.HEAD) {
      String cacheKey = proxyRequest.absoluteURI();
      resource = cache.computeIfPresent(cacheKey, CACHE_GET_AND_VALIDATE);
      if (resource == null) {
        return null;
      }
    } else {
      return null;
    }

    String cacheControlHeader = response.getHeader(HttpHeaders.CACHE_CONTROL);
    if (cacheControlHeader != null) {
      CacheControl cacheControl = new CacheControl().parse(cacheControlHeader);
      if (cacheControl.maxAge() >= 0) {
        long now = System.currentTimeMillis();
        long currentAge = now - resource.timestamp;
        if (currentAge > cacheControl.maxAge() * 1000) {
          String etag = resource.headers.get(HttpHeaders.ETAG);
          if (etag != null) {
            proxyRequest.headers().set(HttpHeaders.IF_NONE_MATCH, resource.etag);
            context.set("cached_resource", resource);
            return context.sendRequest();
          } else {
            return null;
          }
        }
      }
    }

    //
    String ifModifiedSinceHeader = response.getHeader(HttpHeaders.IF_MODIFIED_SINCE);
    if ((response.method() == HttpMethod.GET || response.method() == HttpMethod.HEAD) && ifModifiedSinceHeader != null && resource.lastModified != null) {
      Date ifModifiedSince = ParseUtils.parseHeaderDate(ifModifiedSinceHeader);
      if (resource.lastModified.getTime() <= ifModifiedSince.getTime()) {
        response.response().setStatusCode(304).end();
        return Future.succeededFuture();
      }
    }
    proxyRequest.release();
    ProxyResponse proxyResponse = proxyRequest.response();
    resource.init(proxyResponse);
    return Future.succeededFuture(proxyResponse);
  }
}
