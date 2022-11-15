package examples;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.HttpProxy;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyOptions;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;
import io.vertx.httpproxy.cache.CacheOptions;

/**
 * @author <a href="mailto:emad.albloushi@gmail.com">Emad Alblueshi</a>
 */

public class HttpProxyExamples {

  public void origin(Vertx vertx) {
    HttpServer originServer = vertx.createHttpServer();

    originServer.requestHandler(req -> {
      req.response()
        .putHeader("content-type", "text/html")
        .end("<html><body><h1>I'm the target resource!</h1></body></html>");
    }).listen(7070);
  }

  public void proxy(Vertx vertx) {
    HttpClient proxyClient = vertx.createHttpClient();

    HttpProxy proxy = HttpProxy.reverseProxy(proxyClient);
    proxy.origin(7070, "origin");

    HttpServer proxyServer = vertx.createHttpServer();

    proxyServer.requestHandler(proxy).listen(8080);
  }

  private SocketAddress resolveOriginAddress(HttpServerRequest request) {
    return null;
  }

  public void originSelector(HttpProxy proxy) {
    proxy.originSelector(request -> Future.succeededFuture(resolveOriginAddress(request)));
  }

  private RequestOptions resolveOriginOptions(HttpServerRequest request) {
    return null;
  }

  public void originRequestProvider(HttpProxy proxy) {
    proxy.originRequestProvider((request, client) -> client.request(resolveOriginOptions(request)));
  }

  public void inboundInterceptor(HttpProxy proxy) {
    proxy.addInterceptor(new ProxyInterceptor() {
      @Override
      public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
        ProxyRequest proxyRequest = context.request();

        filter(proxyRequest.headers());

        // Continue the interception chain
        return context.sendRequest();
      }
    });
  }

  public void outboundInterceptor(HttpProxy proxy) {
    proxy.addInterceptor(new ProxyInterceptor() {
      @Override
      public Future<Void> handleProxyResponse(ProxyContext context) {
        ProxyResponse proxyResponse = context.response();

        filter(proxyResponse.headers());

        // Continue the interception chain
        return context.sendResponse();
      }
    });
  }

  public void bodyFilter(HttpProxy proxy) {
    proxy.addInterceptor(new ProxyInterceptor() {
      @Override
      public Future<Void> handleProxyResponse(ProxyContext context) {
        ProxyResponse proxyResponse = context.response();

        // Create a filtered body
        Body filteredBody = filter(proxyResponse.getBody());

        // And then let the response use it
        proxyResponse.setBody(filteredBody);

        // Continue the interception chain
        return context.sendResponse();
      }
    });
  }

  public void immediateResponse(HttpProxy proxy) {
    proxy.addInterceptor(new ProxyInterceptor() {
      @Override
      public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {

        ProxyRequest proxyRequest = context.request();

        // Release the underlying resources
        proxyRequest.release();

        // Create a response and populate it
        ProxyResponse proxyResponse = proxyRequest.response()
          .setStatusCode(200)
          .putHeader("content-type", "text/plain")
          .setBody(Body.body(Buffer.buffer("Hello World")));

        return Future.succeededFuture(proxyResponse);
      }
    });
  }

  private void filter(MultiMap headers) {
    //
  }

  private Body filter(Body body) {
    return body;
  }

  public void more(Vertx vertx, HttpClient proxyClient) {
    HttpProxy proxy = HttpProxy.reverseProxy(proxyClient).originSelector(
      address -> Future.succeededFuture(SocketAddress.inetSocketAddress(7070, "origin"))
    );
  }

  public void lowLevel(Vertx vertx, HttpServer proxyServer, HttpClient proxyClient) {

    proxyServer.requestHandler(request -> {
      ProxyRequest proxyRequest = ProxyRequest.reverseProxy(request);

      proxyClient.request(proxyRequest.getMethod(), 8080, "origin", proxyRequest.getURI())
        .compose(proxyRequest::send)
        .onSuccess(proxyResponse -> {
          // Send the proxy response
          proxyResponse.send();
        })
        .onFailure(err -> {
        // Release the request
        proxyRequest.release();

        // Send error
        request.response().setStatusCode(500)
          .send();
      });
    });
  }

  public void overrideAuthority(HttpProxy proxy) {
    proxy.addInterceptor(new ProxyInterceptor() {
      @Override
      public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
        ProxyRequest proxyRequest = context.request();
        proxyRequest.setAuthority("example.com:80");
        return ProxyInterceptor.super.handleProxyRequest(context);
      }
    });
  }

  public void cacheConfig(Vertx vertx, HttpClient proxyClient) {
    HttpProxy proxy = HttpProxy.reverseProxy(new ProxyOptions().setCacheOptions(new CacheOptions()), proxyClient);
  }
}
