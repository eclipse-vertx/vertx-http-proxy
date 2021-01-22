package examples;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServer;
import io.vertx.httpproxy.HttpProxy;

/**
 * @author <a href="mailto:emad.albloushi@gmail.com">Emad Alblueshi</a>
 */

public class HttpProxyExamples {

  public void origin(Vertx vertx) {
    HttpServer backendServer = vertx.createHttpServer();

    backendServer.requestHandler(req -> {
      req.response()
        .putHeader("content-type", "text/html")
        .end("<html><body><h1>I'm the target resource!</h1></body></html>");
    }).listen(7070);
  }

  public void proxy(Vertx vertx) {
    HttpClient proxyClient = vertx.createHttpClient();

    HttpProxy backend = HttpProxy.reverseProxy2(proxyClient);
    backend.target(7070, "localhost");

    HttpServer proxyServer = vertx.createHttpServer();

    proxyServer.requestHandler(backend).listen(8080);
  }

}
