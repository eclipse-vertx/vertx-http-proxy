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
package io.vertx.tests;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.httpproxy.ProxyOptions;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;

/**
 * JUnit 4 + vertx-unit base class, for the tests not migrated to JUnit 5 yet. New tests should extend
 * {@link VertxTestBase} instead.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@RunWith(VertxUnitRunner.class)
public abstract class TestBase extends TestSupport {

  public TestBase() {
    super();
  }

  public TestBase(ProxyOptions proxyOptions) {
    super(proxyOptions);
  }

  @Before
  public void setUp() {
    vertx = Vertx.vertx();
  }

  @After
  public void tearDown(TestContext context) {
    vertx.close().onComplete(context.asyncAssertSuccess());
  }
}
