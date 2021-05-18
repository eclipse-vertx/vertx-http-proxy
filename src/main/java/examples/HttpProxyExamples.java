package examples;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServer;
import io.vertx.core.net.SocketAddress;
import io.vertx.httpproxy.HttpProxy;
import io.vertx.httpproxy.ProxyRequest;

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

  public void more(Vertx vertx, HttpClient proxyClient) {
    HttpProxy proxy = HttpProxy.reverseProxy(proxyClient).originSelector(
      address -> Future.succeededFuture(SocketAddress.inetSocketAddress(7070, "origin"))
    );
  }

  public void lowLevel(Vertx vertx, HttpServer proxyServer, HttpClient proxyClient) {

    proxyServer.requestHandler(outboundRequest -> {
      ProxyRequest proxyRequest = ProxyRequest.reverseProxy(outboundRequest);

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
        outboundRequest.response().setStatusCode(500)
          .send();
      });
    });
  }
}
