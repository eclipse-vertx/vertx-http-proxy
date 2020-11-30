/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
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
