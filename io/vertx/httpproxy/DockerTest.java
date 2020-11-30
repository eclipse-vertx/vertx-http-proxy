package io.vertx.httpproxy;

import io.vertx.ext.unit.TestContext;
import org.junit.Test;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class DockerTest extends ProxyTestBase {

  @Test
  public void testDocker(TestContext ctx) throws Exception {

/*
    Async async = ctx.async();
//    async.awaitSuccess();

    DockerBackend backend = new DockerBackend(vertx);
    backend.start(ctx.asyncAssertSuccess(v -> async.complete()));

    Async async2 = ctx.async();

    HttpProxy proxy = HttpProxy.createProxy(vertx, options);
    proxy.addBackend(backend);
    proxy.listen(ctx.asyncAssertSuccess(v -> async2.complete()));


    Async async3 = ctx.async();
*/
  }
}
