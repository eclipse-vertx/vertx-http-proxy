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

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.vertx.core.http.HttpClient;
import org.junit.Rule;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Base class for cache tests which use WireMock (port 8081) as the backend.
 */
public abstract class CacheWireMockTestBase extends CacheTestBase {

  protected HttpClient client;

  @Rule
  public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().port(8081));

  @Override
  public void setUp() {
    super.setUp();
    client = vertx.createHttpClient();
  }
}
