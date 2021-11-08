package io.vertx.httpproxy;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link io.vertx.httpproxy.ProxyOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.httpproxy.ProxyOptions} original class using Vert.x codegen.
 */
public class ProxyOptionsConverter {


  private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;
  private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

  public static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, ProxyOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "cacheOptions":
          if (member.getValue() instanceof JsonObject) {
            obj.setCacheOptions(new io.vertx.httpproxy.cache.CacheOptions((io.vertx.core.json.JsonObject)member.getValue()));
          }
          break;
        case "supportWebSocket":
          if (member.getValue() instanceof Boolean) {
            obj.setSupportWebSocket((Boolean)member.getValue());
          }
          break;
      }
    }
  }

  public static void toJson(ProxyOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

  public static void toJson(ProxyOptions obj, java.util.Map<String, Object> json) {
    if (obj.getCacheOptions() != null) {
      json.put("cacheOptions", obj.getCacheOptions().toJson());
    }
    json.put("supportWebSocket", obj.getSupportWebSocket());
  }
}
