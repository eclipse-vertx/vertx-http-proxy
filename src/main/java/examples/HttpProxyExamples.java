package examples;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.SocketAddress;
import io.vertx.httpproxy.*;
import io.vertx.httpproxy.cache.CacheOptions;
import io.vertx.httpproxy.interceptors.BodyInterceptor;
import io.vertx.httpproxy.interceptors.BodyTransformer;
import io.vertx.httpproxy.interceptors.HeadInterceptor;

import java.util.Set;

/**
 * @author <a href="mailto:emad.albloushi@gmail.com">Emad Alblueshi</a>
 */

@SuppressWarnings("unused")
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

  private Future<SocketAddress> resolveOriginAddress(ProxyContext proxyContext) {
    return null;
  }

  public void originSelector(HttpProxy proxy) {
    proxy.origin(OriginRequestProvider.selector(proxyContext -> resolveOriginAddress(proxyContext)));
  }

  private RequestOptions resolveOriginOptions(ProxyContext request) {
    return null;
  }

  public void originRequestProvider(HttpProxy proxy) {
    proxy.origin((proxyContext) -> proxyContext.client().request(resolveOriginOptions(proxyContext)));
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

  public void headerInterceptorFilter(HttpProxy proxy, Set<CharSequence> shouldRemove) {
    // remove a set of headers
    proxy.addInterceptor(
      HeadInterceptor.builder().filteringResponseHeaders(shouldRemove).build());
  }

  public void queryInterceptorAdd(HttpProxy proxy, String key, String value) {
    proxy.addInterceptor(
      HeadInterceptor.builder().settingQueryParam(key, value).build());
  }

  public void bodyInterceptorJson(HttpProxy proxy) {
    proxy.addInterceptor(
      BodyInterceptor.modifyResponseBody(
        BodyTransformer.transformJsonObject(
          jsonObject -> removeSomeFields(jsonObject)
        )
      ));
  }

  public void webSocketInterceptorPath(HttpProxy proxy) {
    HeadInterceptor interceptor = HeadInterceptor.builder()
      .addingPathPrefix("/api")
      .build();
    proxy.addInterceptor(interceptor, true);
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

  private JsonObject removeSomeFields(JsonObject o) {
    return o;
  }

  public void overrideAuthority(HttpProxy proxy) {
    proxy.addInterceptor(new ProxyInterceptor() {
      @Override
      public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
        ProxyRequest proxyRequest = context.request();
        proxyRequest.setAuthority(HostAndPort.create("example.com", 80));
        return ProxyInterceptor.super.handleProxyRequest(context);
      }
    });
  }

  public void cacheConfig(Vertx vertx, HttpClient proxyClient) {
    HttpProxy proxy = HttpProxy.reverseProxy(new ProxyOptions().setCacheOptions(new CacheOptions()), proxyClient);
  }
}
