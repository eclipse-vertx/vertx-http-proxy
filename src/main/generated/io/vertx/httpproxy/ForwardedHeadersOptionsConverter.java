package io.vertx.httpproxy;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

/**
 * Converter and mapper for {@link io.vertx.httpproxy.ForwardedHeadersOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.httpproxy.ForwardedHeadersOptions} original class using Vert.x codegen.
 */
public class ForwardedHeadersOptionsConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, ForwardedHeadersOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "enabled":
          if (member.getValue() instanceof Boolean) {
            obj.setEnabled((Boolean)member.getValue());
          }
          break;
        case "forwardFor":
          if (member.getValue() instanceof Boolean) {
            obj.setForwardFor((Boolean)member.getValue());
          }
          break;
        case "forwardProto":
          if (member.getValue() instanceof Boolean) {
            obj.setForwardProto((Boolean)member.getValue());
          }
          break;
        case "forwardHost":
          if (member.getValue() instanceof Boolean) {
            obj.setForwardHost((Boolean)member.getValue());
          }
          break;
        case "forwardPort":
          if (member.getValue() instanceof Boolean) {
            obj.setForwardPort((Boolean)member.getValue());
          }
          break;
        case "useRfc7239":
          if (member.getValue() instanceof Boolean) {
            obj.setUseRfc7239((Boolean)member.getValue());
          }
          break;
      }
    }
  }

   static void toJson(ForwardedHeadersOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(ForwardedHeadersOptions obj, java.util.Map<String, Object> json) {
    json.put("enabled", obj.isEnabled());
    json.put("forwardFor", obj.isForwardFor());
    json.put("forwardProto", obj.isForwardProto());
    json.put("forwardHost", obj.isForwardHost());
    json.put("forwardPort", obj.isForwardPort());
    json.put("useRfc7239", obj.isUseRfc7239());
  }
}
