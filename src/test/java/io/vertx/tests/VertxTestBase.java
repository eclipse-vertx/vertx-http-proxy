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
import io.vertx.httpproxy.ProxyOptions;
import io.vertx.junit5.VertxTest;
import org.junit.jupiter.api.BeforeEach;

/**
 * JUnit 5 + vertx-junit5 base class. The {@link Vertx} instance is created, instrumented and closed by the extension:
 * an assertion error thrown in a handler of that instance fails the test instead of being swallowed.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxTest
public abstract class VertxTestBase extends TestSupport {

  public VertxTestBase() {
    super();
  }

  public VertxTestBase(ProxyOptions proxyOptions) {
    super(proxyOptions);
  }

  @BeforeEach
  public void setUp(Vertx vertx) {
    this.vertx = vertx;
  }
}
