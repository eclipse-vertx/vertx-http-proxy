package io.vertx.httpproxy.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.HttpProxy;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

public class HttpProxyImpl implements HttpProxy {

  private static final BiFunction<String, Resource, Resource> CACHE_GET_AND_VALIDATE = (key, resource) -> {
    long now = System.currentTimeMillis();
    long val = resource.timestamp + resource.maxAge;
    return val < now ? null : resource;
  };

  private final HttpClient client;
  private Function<HttpServerRequest, Future<SocketAddress>> selector = req -> Future.failedFuture("No target available");
  private final Map<String, Resource> cache = new HashMap<>();

  public HttpProxyImpl(HttpClient client) {
    this.client = client;
  }

  @Override
  public HttpProxy selector(Function<HttpServerRequest, Future<SocketAddress>> selector) {
    this.selector = selector;
    return this;
  }

  @Override
  public void handle(HttpServerRequest frontRequest) {
    handleProxyRequest(frontRequest);
  }

  private Future<HttpClientRequest> resolveTarget(HttpServerRequest frontRequest) {
    return selector.apply(frontRequest).flatMap(server -> {
      RequestOptions requestOptions = new RequestOptions();
      requestOptions.setServer(server);
      return client.request(requestOptions);
    });
  }

  boolean revalidateResource(ProxyResponse response, Resource resource) {
    if (resource.etag != null && response.etag() != null) {
      return resource.etag.equals(response.etag());
    }
    return true;
  }

  private void end(ProxyRequest proxyRequest, int sc) {
    proxyRequest
      .release()
      .response()
      .setStatusCode(sc)
      .putHeader(HttpHeaders.CONTENT_LENGTH, "0")
      .setBody(null)
      .send(ar -> {});

  }

  private void handleProxyRequest(HttpServerRequest frontRequest) {
    ProxyRequest proxyRequest = ProxyRequest.reverseProxy(frontRequest);

    // Encoding check
    Boolean chunked = HttpUtils.isChunked(frontRequest.headers());
    if (chunked == null) {
      end(proxyRequest, 400);
      return;
    }

    // Handle from cache
    HttpMethod method = frontRequest.method();
    if (method == HttpMethod.GET || method == HttpMethod.HEAD) {
      String cacheKey = proxyRequest.absoluteURI();
      Resource resource = cache.computeIfPresent(cacheKey, CACHE_GET_AND_VALIDATE);
      if (resource != null) {
        if (tryHandleProxyRequestFromCache(proxyRequest, frontRequest, resource)) {
          return;
        }
      }
    }
    handleProxyRequestAndProxyResponse(proxyRequest, frontRequest);
  }

  private void handleProxyRequestAndProxyResponse(ProxyRequest proxyRequest, HttpServerRequest frontRequest) {
    handleProxyRequest(proxyRequest, frontRequest, ar -> {
      if (ar.succeeded()) {
        handleProxyResponse(ar.result(), ar2 -> {});
      } else {
        // TODO ???
      }
    });
  }

  private void handleProxyRequest(ProxyRequest proxyRequest, HttpServerRequest frontRequest, Handler<AsyncResult<ProxyResponse>> handler) {
    Future<HttpClientRequest> f = resolveTarget(frontRequest);
    f.onComplete(ar -> {
      if (ar.succeeded()) {
        handleProxyRequest(proxyRequest, frontRequest, ar.result(), handler);
      } else {
        frontRequest.resume();
        Promise<Void> promise = Promise.promise();
        frontRequest.exceptionHandler(promise::tryFail);
        frontRequest.endHandler(promise::tryComplete);
        promise.future().onComplete(ar2 -> {
          end(proxyRequest, 404);
        });
        handler.handle(Future.failedFuture(ar.cause()));
      }
    });
  }

  private void handleProxyRequest(ProxyRequest proxyRequest, HttpServerRequest frontRequest, HttpClientRequest backRequest, Handler<AsyncResult<ProxyResponse>> handler) {
    proxyRequest.send(backRequest, ar2 -> {
      if (ar2.succeeded()) {
        handler.handle(ar2);
      } else {
        frontRequest.response().setStatusCode(502).end();
        handler.handle(Future.failedFuture(ar2.cause()));
      }
    });
  }

  private void handleProxyResponse(ProxyResponse response, Handler<AsyncResult<Void>> completionHandler) {

    // Check validity
    Boolean chunked = HttpUtils.isChunked(response.headers());
    if (chunked == null) {
      // response.request().release(); // Is it needed ???
      end(response.request(), 501);
      completionHandler.handle(Future.failedFuture("TODO"));
      return;
    }

    if (chunked && response.request().version() == HttpVersion.HTTP_1_0) {
      String contentLength = response.headers().get(HttpHeaders.CONTENT_LENGTH);
      if (contentLength == null) {
        // Special handling for HTTP 1.0 clients that cannot handle chunked encoding
        Body body = response.getBody();
        response.release();
        BufferingWriteStream buffer = new BufferingWriteStream();
        body.stream().pipeTo(buffer, ar -> {
          if (ar.succeeded()) {
            Buffer content = buffer.content();
            response.setBody(Body.body(content));
            continueHandleResponse(response, completionHandler);
          } else {
            System.out.println("Not implemented");
          }
        });
        return;
      }
    }
    continueHandleResponse(response, completionHandler);
  }

  private void continueHandleResponse(ProxyResponse response, Handler<AsyncResult<Void>> completionHandler) {
    ProxyRequest request = response.request();
    Handler<AsyncResult<Void>> handler;
    if (response.publicCacheControl() && response.maxAge() > 0) {
      if (request.getMethod() == HttpMethod.GET) {
        String absoluteUri = request.absoluteURI();
        Resource res = new Resource(
          absoluteUri,
          response.getStatusCode(),
          response.headers(),
          System.currentTimeMillis(),
          response.maxAge());
        response.bodyFilter(s -> new BufferingReadStream(s, res.content));
        handler = ar3 -> {
          completionHandler.handle(ar3);
          if (ar3.succeeded()) {
            cache.put(absoluteUri, res);
          }
        };
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
        handler = completionHandler;
      }
    } else {
      handler = completionHandler;
    }

    response.send(handler);
  }

  private boolean tryHandleProxyRequestFromCache(ProxyRequest proxyRequest, HttpServerRequest frontRequest, Resource resource) {
    String cacheControlHeader = frontRequest.getHeader(HttpHeaders.CACHE_CONTROL);
    if (cacheControlHeader != null) {
      CacheControl cacheControl = new CacheControl().parse(cacheControlHeader);
      if (cacheControl.maxAge() >= 0) {
        long now = System.currentTimeMillis();
        long currentAge = now - resource.timestamp;
        if (currentAge > cacheControl.maxAge() * 1000) {
          String etag = resource.headers.get(HttpHeaders.ETAG);
          if (etag != null) {
            proxyRequest.headers().set(HttpHeaders.IF_NONE_MATCH, resource.etag);
            handleProxyRequest(proxyRequest, frontRequest, ar -> {
              if (ar.succeeded()) {
                ProxyResponse proxyResp = ar.result();
                int sc = proxyResp.getStatusCode();
                switch (sc) {
                  case 200:
                    handleProxyResponse(proxyResp, ar2 -> {});
                    break;
                  case 304:
                    // Warning: this relies on the fact that HttpServerRequest will not send a body for HEAD
                    proxyResp.release();
                    resource.sendTo(proxyRequest.response());
                    break;
                  default:
                    System.out.println("Not implemented");
                    break;
                }
              } else {
                System.out.println("Not implemented");
              }
            });
            return true;
          } else {
            return false;
          }
        }
      }
    }

    //
    String ifModifiedSinceHeader = frontRequest.getHeader(HttpHeaders.IF_MODIFIED_SINCE);
    if ((frontRequest.method() == HttpMethod.GET || frontRequest.method() == HttpMethod.HEAD) && ifModifiedSinceHeader != null && resource.lastModified != null) {
      Date ifModifiedSince = ParseUtils.parseHeaderDate(ifModifiedSinceHeader);
      if (resource.lastModified.getTime() <= ifModifiedSince.getTime()) {
        frontRequest.response().setStatusCode(304).end();
        return true;
      }
    }

    resource.sendTo(proxyRequest.response());
    return true;
  }
}
