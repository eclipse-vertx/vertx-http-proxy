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

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Base class for cache tests which use WireMock (port 8081) as the backend.
 */
public abstract class CacheWireMockTestBase extends CacheTestBase {

  protected HttpClient client;

  /**
   * Registered on an instance field, so the server is started and stopped around every test method, like the
   * {@code WireMockRule} it replaces. {@code configureStaticDsl} keeps the static {@code stubFor(...)} DSL working,
   * which the subclasses use; unlike the rule, the extension does not configure it by default.
   */
  @RegisterExtension
  WireMockExtension wireMock = WireMockExtension.newInstance()
    .options(wireMockConfig().port(8081))
    .configureStaticDsl(true)
    .build();

  @BeforeEach
  @Override
  public void setUp(Vertx vertx) {
    super.setUp(vertx);
    client = vertx.createHttpClient();
  }
}
