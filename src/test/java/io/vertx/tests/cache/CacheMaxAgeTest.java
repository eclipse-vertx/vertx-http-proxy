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
package io.vertx.tests.cache;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.unit.TestContext;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CacheMaxAgeTest extends CacheExpiresTest {

  @Override
  protected void setCacheControl(MultiMap headers, long now, long delaySeconds) {
    headers.set(HttpHeaders.CACHE_CONTROL, "public, max-age=" + delaySeconds);
  }

  @Override
  public void testPublicGet(TestContext ctx) throws Exception {
    super.testPublicGet(ctx);
  }

  @Override
  public void testPublicHead(TestContext ctx) throws Exception {
    super.testPublicHead(ctx);
  }

  @Override
  public void testPublicExpiration(TestContext ctx) throws Exception {
    super.testPublicExpiration(ctx);
  }
}
