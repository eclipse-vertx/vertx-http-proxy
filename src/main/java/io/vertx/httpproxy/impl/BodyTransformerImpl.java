package io.vertx.httpproxy.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.httpproxy.BodyTransformer;

import java.util.Objects;
import java.util.function.Function;

public class BodyTransformerImpl implements BodyTransformer {
  private final Function<Buffer, Buffer> transformer;

  @Override
  public Buffer apply(Buffer buffer) {
    return transformer.apply(buffer);
  }

  private BodyTransformerImpl(Function<Buffer, Buffer> transformer) {
    this.transformer = Objects.requireNonNull(transformer);
  }

  public static BodyTransformerImpl transformJsonObject(Function<JsonObject, JsonObject> transformer) {
    return new BodyTransformerImpl(buffer -> transformer.apply(buffer.toJsonObject()).toBuffer());
  }

  public static BodyTransformerImpl transformJsonArray(Function<JsonArray, JsonArray> transformer) {
    return new BodyTransformerImpl(buffer -> transformer.apply(buffer.toJsonArray()).toBuffer());
  }

  public static BodyTransformerImpl transformJson(Function<Object, Object> transformer) {
    return new BodyTransformerImpl(buffer -> Json.encodeToBuffer(transformer.apply(Json.decodeValue(buffer))));
  }

  public static BodyTransformerImpl transformText(Function<String, String> transformer, String encoding) {
    return new BodyTransformerImpl(buffer -> Buffer.buffer(transformer.apply(buffer.toString(encoding))));
  }

  public static BodyTransformerImpl discard() {
    return new BodyTransformerImpl(buffer -> Buffer.buffer());
  }


}
