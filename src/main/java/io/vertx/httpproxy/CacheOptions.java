package io.vertx.httpproxy;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class CacheOptions {

  public CacheOptions() {
  }

  public CacheOptions(JsonObject json) {
  }

  @Override
  public String toString() {
    return "CacheOptions[]";
  }
}
