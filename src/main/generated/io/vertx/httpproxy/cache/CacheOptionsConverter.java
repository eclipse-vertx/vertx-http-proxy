package io.vertx.httpproxy.cache;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

/**
 * Converter and mapper for {@link io.vertx.httpproxy.cache.CacheOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.httpproxy.cache.CacheOptions} original class using Vert.x codegen.
 */
public class CacheOptionsConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, CacheOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "maxSize":
          if (member.getValue() instanceof Number) {
            obj.setMaxSize(((Number)member.getValue()).intValue());
          }
          break;
        case "name":
          if (member.getValue() instanceof String) {
            obj.setName((String)member.getValue());
          }
          break;
        case "shared":
          if (member.getValue() instanceof Boolean) {
            obj.setShared((Boolean)member.getValue());
          }
          break;
      }
    }
  }

   static void toJson(CacheOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(CacheOptions obj, java.util.Map<String, Object> json) {
    json.put("maxSize", obj.getMaxSize());
    if (obj.getName() != null) {
      json.put("name", obj.getName());
    }
    json.put("shared", obj.isShared());
  }
}
