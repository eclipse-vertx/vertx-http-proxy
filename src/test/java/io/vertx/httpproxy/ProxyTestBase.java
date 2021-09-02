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

import io.vertx.ext.unit.junit.VertxUnitRunnerWithParametersFactory;
import io.vertx.httpproxy.cache.CacheOptions;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(VertxUnitRunnerWithParametersFactory.class)
public abstract class ProxyTestBase extends TestBase {

  @Parameterized.Parameters(name = "Options: {0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
      { new ProxyOptions() },
      { new ProxyOptions().setCacheOptions(new CacheOptions()) }
    });
  }

  public ProxyTestBase(ProxyOptions options) {
    super(options);
  }

}
