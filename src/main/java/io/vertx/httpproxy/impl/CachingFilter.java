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
import io.vertx.httpproxy.spi.cache.Resource;

import java.time.Instant;
import java.util.function.BiFunction;
import java.util.function.Predicate;

class CachingFilter implements ProxyInterceptor {

  private final Cache cache;

  public CachingFilter(Cache cache) {
    this.cache = cache;
  }

  @Override
  public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
    return tryHandleProxyRequestFromCache(context);
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
      fillResponseFromResource(response, cached);
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
        response.setBody(Body.body(new BufferingReadStream(body.stream(), res.getContent()), body.length()));
        Future<Void> fut = context.sendResponse();
        fut.onSuccess(v -> {
          cache.put(absoluteUri, res);
        });
        return fut;
      } else if (request.getMethod() != HttpMethod.HEAD) {
        return context.sendResponse();
      } else {
        return cache.get(request.absoluteURI()).compose(resource -> {
          if (resource != null) {
            if (!revalidateResource(response, resource)) {
              // Invalidate cache
              cache.remove(request.absoluteURI());
            }
          }
          return context.sendResponse();
        });
      }
    } else {
      return context.sendResponse();
    }
  }

  private static boolean revalidateResource(ProxyResponse response, Resource resource) {
    if (resource.getEtag() != null && response.etag() != null) {
      return resource.getEtag().equals(response.etag());
    }
    return true;
  }

  private Future<ProxyResponse> tryHandleProxyRequestFromCache(ProxyContext context) {

    ProxyRequest proxyRequest = context.request();

    HttpServerRequest response = proxyRequest.proxiedRequest();

    HttpMethod method = response.method();
    if (method != HttpMethod.GET && method != HttpMethod.HEAD) {
      return context.sendRequest();
    }

    String cacheKey = proxyRequest.absoluteURI();
    return cache.get(cacheKey).compose(resource -> {
      if (resource == null) {
        return context.sendRequest();
      }

      long now = System.currentTimeMillis();
      long val = resource.getTimestamp() + resource.getMaxAge();
      if (val < now) {
        return cache.remove(cacheKey).compose(v -> context.sendRequest());
      }

      String cacheControlHeader = response.getHeader(HttpHeaders.CACHE_CONTROL);
      if (cacheControlHeader != null) {
        CacheControl cacheControl = new CacheControl().parse(cacheControlHeader);
        if (cacheControl.maxAge() >= 0) {
          long currentAge = now - resource.getTimestamp();
          if (currentAge > cacheControl.maxAge() * 1000) {
            String etag = resource.getHeaders().get(HttpHeaders.ETAG);
            if (etag != null) {
              proxyRequest.headers().set(HttpHeaders.IF_NONE_MATCH, resource.getEtag());
              context.set("cached_resource", resource);
            }
            return context.sendRequest();
          }
        }
      }

      //
      String ifModifiedSinceHeader = response.getHeader(HttpHeaders.IF_MODIFIED_SINCE);
      if ((response.method() == HttpMethod.GET || response.method() == HttpMethod.HEAD) && ifModifiedSinceHeader != null && resource.getLastModified() != null) {
        Instant ifModifiedSince = ParseUtils.parseHeaderDate(ifModifiedSinceHeader);
        if (!ifModifiedSince.isAfter(resource.getLastModified())) {
          return Future.succeededFuture(proxyRequest.release().response().setStatusCode(304));
        }
      }
      proxyRequest.release();
      ProxyResponse proxyResponse = proxyRequest.response();
      fillResponseFromResource(proxyResponse, resource);
      return Future.succeededFuture(proxyResponse);
    });

  }

  public void fillResponseFromResource(ProxyResponse proxyResponse, Resource resource) {
    proxyResponse.setStatusCode(200);
    proxyResponse.setStatusMessage(resource.getStatusMessage());
    proxyResponse.headers().addAll(resource.getHeaders());
    proxyResponse.setBody(Body.body(resource.getContent()));
  }
}
