package io.vertx.httpproxy.impl;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
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
import java.util.List;
import java.util.Map;

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
    ProxyRequest request = response.request();
    Resource cached = context.get("cached_resource", Resource.class);
    String absoluteUri = request.absoluteURI();

    if (cached != null && response.getStatusCode() == 304) {
      return updateStoredCache(cache, absoluteUri, cached, response.headers()).compose(newCached -> {
        response.release();
        newCached.init(response, request.getMethod() == HttpMethod.GET);
        return context.sendResponse();
      });
    }

    String reqCacheControlStr = request.headers().get(HttpHeaders.CACHE_CONTROL);
    CacheControl requestCacheControl = reqCacheControlStr == null ? null : new CacheControl().parse(reqCacheControlStr);
    String respCacheControlStr = response.headers().get(HttpHeaders.CACHE_CONTROL);
    CacheControl responseCacheControl = respCacheControlStr == null ? null : new CacheControl().parse(respCacheControlStr);

    if (request.getMethod() == HttpMethod.GET || request.getMethod() == HttpMethod.HEAD) {
      boolean canCache = response.maxAge() >= 0 || (responseCacheControl != null && responseCacheControl.isPublic());
      if (responseCacheControl != null) {
        if (responseCacheControl.isPrivate()) canCache = false;
        if (responseCacheControl.isMustUnderstand()) {
          if (!statusCodeUnderstandable(response.getStatusCode())) canCache = false;
        } else if (responseCacheControl.isNoStore()) {
          canCache = false;
        }
      }
      if (response.headers().get(HttpHeaders.AUTHORIZATION) != null) {
        if (
          responseCacheControl == null || (
            !responseCacheControl.isMustRevalidate()
            && !responseCacheControl.isPublic()
            && responseCacheControl.sMaxage() == -1)
        ) {
          canCache = false;
        }
      }
      if (requestCacheControl != null && requestCacheControl.isNoStore()) {
        canCache = false;
      }
      if (canCache) {
        if (request.getMethod() == HttpMethod.GET) {
          Resource res = new Resource(
            absoluteUri,
            varyHeaders(request.headers(), response.headers()),
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
        } else { // is HEAD
          return cache.remove(absoluteUri).compose(v -> {
            return context.sendResponse();
          });
        }
      }

    } else if (request.getMethod() != HttpMethod.OPTIONS && request.getMethod() != HttpMethod.TRACE) {
      return cache.remove(absoluteUri).compose(v -> {
        return context.sendResponse();
      });
    }
    return context.sendResponse();
  }

  private static MultiMap varyHeaders(MultiMap requestHeaders, MultiMap responseHeaders) {
    MultiMap result = MultiMap.caseInsensitiveMultiMap();
    String vary = responseHeaders.get(HttpHeaders.VARY);
    if (vary != null) {
      if (vary.trim().equals("*")) {
        return result.addAll(requestHeaders);
      }
      for (String toVary : vary.split(",")) {
        toVary = toVary.trim();
        String toVaryValue = requestHeaders.get(toVary);
        if (toVaryValue != null) {
          result.add(toVary, toVaryValue);
        }
      }
    }
    return result;
  }

  private static boolean statusCodeUnderstandable(int statusCode) {
    return statusCode >= 100 && statusCode < 600; // TODO: should investigate
  }

  private Future<ProxyResponse> tryHandleProxyRequestFromCache(ProxyContext context) {

    ProxyRequest proxyRequest = context.request();

    HttpServerRequest inboundRequest = proxyRequest.proxiedRequest();
    String cacheControlHeader = inboundRequest.getHeader(HttpHeaders.CACHE_CONTROL);
    CacheControl requestCacheControl = cacheControlHeader == null ? null : new CacheControl().parse(cacheControlHeader);

    HttpMethod method = inboundRequest.method();
    if (method != HttpMethod.GET && method != HttpMethod.HEAD) {
      return context.sendRequest();
    }

    String cacheKey = proxyRequest.absoluteURI();
    return cache.get(cacheKey).compose(resource -> {
      if (resource == null || !checkVaryHeaders(proxyRequest.headers(), resource.getRequestVaryHeader())) {
        if (requestCacheControl != null && requestCacheControl.isOnlyIfCached()) {
          return Future.succeededFuture(proxyRequest.release().response().setStatusCode(504));
        }
        return context.sendRequest();
      }

      boolean validInboundCache = false;
      String inboundIfModifiedSince = inboundRequest.getHeader(HttpHeaders.IF_MODIFIED_SINCE);
      String inboundIfNoneMatch = inboundRequest.getHeader(HttpHeaders.IF_NONE_MATCH);
      Instant resourceLastModified = resource.getLastModified();
      String resourceETag = resource.getEtag();
      if (resource.getStatusCode() == 200) { // TODO: status code 206
        if (inboundIfNoneMatch != null && resourceETag != null) {
          String[] inboundETags = inboundIfNoneMatch.split(",");
          for (String inboundETag : inboundETags) {
            inboundETag = inboundETag.trim();
            if (inboundETag.equals(resourceETag)) {
              validInboundCache = true;
              break;
            }
            return context.sendRequest();
          }
        } else if (inboundIfModifiedSince != null && resourceLastModified != null) {
          if (ParseUtils.parseHeaderDate(inboundIfModifiedSince).isAfter(resourceLastModified)) { // TODO: is it wrong???
            validInboundCache = true;
          }
        }
      }
      if (validInboundCache) {
        MultiMap infoHeaders = MultiMap.caseInsensitiveMultiMap();
        List<CharSequence> headersNeeded = List.of(
          HttpHeaders.CACHE_CONTROL,
          HttpHeaders.CONTENT_LOCATION,
          HttpHeaders.DATE,
          HttpHeaders.ETAG,
          HttpHeaders.EXPIRES,
          HttpHeaders.VARY
        );
        for (CharSequence header : headersNeeded) {
          String value = resource.getHeaders().get(header);
          if (value != null) infoHeaders.add(header, value);
        }
        ProxyResponse resp = proxyRequest.release().response();
        resp.headers().setAll(infoHeaders);
        resp.setStatusCode(304);
        return Future.succeededFuture(resp);
      }

      boolean needValidate = false;
      String resourceCacheControlHeader = resource.getHeaders().get(HttpHeaders.CACHE_CONTROL);
      CacheControl resourceCacheControl = resourceCacheControlHeader == null ? null : new CacheControl().parse(resourceCacheControlHeader);
      if (resourceCacheControl != null && resourceCacheControl.isNoCache()) needValidate = true;
      if (requestCacheControl != null && requestCacheControl.isNoCache()) needValidate = true;
      long age = Math.subtractExact(System.currentTimeMillis(), resource.getTimestamp()); // in ms
      long maxAge = Math.max(0, resource.getMaxAge());
      if (resourceCacheControl != null && (resourceCacheControl.isMustRevalidate() || resourceCacheControl.isProxyRevalidate())) {
        if (age > maxAge) needValidate = true;
      } else if (requestCacheControl != null) {
        if (requestCacheControl.maxAge() != -1) {
          maxAge = Math.min(maxAge, SafeMathUtils.safeMultiply(requestCacheControl.maxAge(), 1000));
        }
        if (requestCacheControl.minFresh() != -1) {
          maxAge -= SafeMathUtils.safeMultiply(requestCacheControl.minFresh(), 1000);
        } else if (requestCacheControl.maxStale() != -1) {
          maxAge += SafeMathUtils.safeMultiply(requestCacheControl.maxStale(), 1000);
        }
        if (age > maxAge) needValidate = true;
      }
      String etag = resource.getHeaders().get(HttpHeaders.ETAG);
      String lastModified = resource.getHeaders().get(HttpHeaders.LAST_MODIFIED);
      if (needValidate) {
        if (etag != null) {
          proxyRequest.headers().set(HttpHeaders.IF_NONE_MATCH, etag);
        }
        if (lastModified != null) {
          proxyRequest.headers().set(HttpHeaders.IF_MODIFIED_SINCE, lastModified);
        }
        context.set("cached_resource", resource);
        return context.sendRequest();
      } else {
        proxyRequest.release();
        ProxyResponse proxyResponse = proxyRequest.response();
        resource.init(proxyResponse, inboundRequest.method() == HttpMethod.GET);
        return Future.succeededFuture(proxyResponse);
      }

    });

  }


  private static boolean checkVaryHeaders(MultiMap requestHeaders, MultiMap varyHeaders) {
    for (Map.Entry<String, String> e: varyHeaders) {
      String fromVary = e.getValue().toLowerCase();
      String fromRequest = requestHeaders.get(e.getKey());
      if (fromRequest == null) return false;
      fromRequest = fromVary.toLowerCase();
      if (!fromRequest.equals(fromVary)) return false;
    }
    return true;
  }

  private static Future<Resource> updateStoredCache(Cache cache, String key, Resource oldCached, MultiMap newHeaders) {
    MultiMap newHeadersInternal = MultiMap.caseInsensitiveMultiMap().addAll(newHeaders).remove(HttpHeaders.CONTENT_LENGTH);
    oldCached.getHeaders().setAll(newHeadersInternal);
    return cache.put(key, oldCached).compose(v -> Future.succeededFuture(oldCached));
  }
}
