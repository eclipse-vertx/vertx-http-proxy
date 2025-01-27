/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.httpproxy.cache;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;

import java.util.Objects;

/**
 * Cache options.
 */
@DataObject
@JsonGen(publicConverter = false)
public class CacheOptions {

  /**
   * Default max size of the cache = 1000
   */
  public static final int DEFAULT_MAX_SIZE = 1000;

  /**
   * Actual name of anonymous shared cache = {@code __vertx.DEFAULT}
   */
  public static final String DEFAULT_NAME = "__vertx.DEFAULT";

  /**
   * Default shared cache = {@code false}
   */
  public static final boolean DEFAULT_SHARED = false;

  private int maxSize = DEFAULT_MAX_SIZE;
  private String name = DEFAULT_NAME;
  private boolean shared = DEFAULT_SHARED;

  /**
   * Default constructor.
   */
  public CacheOptions() {
  }

  /**
   * Copy constructor.
   *
   * @param other the options to copy
   */
  public CacheOptions(CacheOptions other) {
    this.maxSize = other.getMaxSize();
    this.name = other.getName();
    this.shared = other.isShared();
  }

  /**
   * Constructor to create an options from JSON.
   *
   * @param json  the JSON
   */
  public CacheOptions(JsonObject json) {
    CacheOptionsConverter.fromJson(json, this);
  }

  /**
   * @return the max number of entries the cache can hold.
   */
  public int getMaxSize() {
    return maxSize;
  }

  /**
   * Set the max number of entries the cache can hold.
   *
   * @param maxSize the max size
   * @return a reference to this, so the API can be used fluently
   */
  public CacheOptions setMaxSize(int maxSize) {
    if (maxSize <= 0) {
      throw new IllegalArgumentException("Max size must be > 0");
    }
    this.maxSize = maxSize;
    return this;
  }

  /**
   * @return the cache name used for sharing
   */
  public String getName() {
    return this.name;
  }

  /**
   * Set the cache name, used when the cache is shared, otherwise ignored.
   * @param name the new name
   * @return a reference to this, so the API can be used fluently
   */
  public CacheOptions setName(String name) {
    Objects.requireNonNull(name, "Client name cannot be null");
    this.name = name;
    return this;
  }

  /**
   * @return whether the cache is shared
   */
  public boolean isShared() {
    return shared;
  }

  /**
   * Set to {@code true} to share the cache.
   *
   * <p> There can be multiple shared caches distinguished by {@link #getName()}, when no specific
   * name is set, the {@link #DEFAULT_NAME} is used.
   *
   * @param shared {@code true} to use a shared client
   * @return a reference to this, so the API can be used fluently
   */
  public CacheOptions setShared(boolean shared) {
    this.shared = shared;
    return this;
  }

  @Override
  public String toString() {
    return toJson().toString();
  }

  /**
   * Convert to JSON.
   *
   * @return the JSON
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    CacheOptionsConverter.toJson(this, json);
    return json;
  }
}
