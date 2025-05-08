package io.vertx.httpproxy.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.httpproxy.BodyTransformer;
import io.vertx.httpproxy.MediaType;

import java.util.function.Function;

public class BodyTransformerImpl implements BodyTransformer {

  private final Function<Buffer, Buffer> transformer;
  private final MediaType consumes;
  private final MediaType produces;

  public BodyTransformerImpl(Function<Buffer, Buffer> transformer, MediaType consumedMediaType, MediaType produces) {
    this.transformer = transformer;
    this.consumes = consumedMediaType;
    this.produces = produces;
  }

  @Override
  public Function<Buffer, Buffer> transformer(MediaType mediaType) {
    return transformer;
  }

  @Override
  public boolean consumes(MediaType mediaType) {
    return consumes == null || (mediaType != null && consumes.accepts(mediaType));
  }

  @Override
  public MediaType produces(MediaType mediaType) {
    return produces;
  }

  public static BodyTransformerImpl transformJsonObject(Function<JsonObject, JsonObject> transformer) {
    return new BodyTransformerImpl(buffer -> transformer.apply(buffer.toJsonObject()).toBuffer(), MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON);
  }

  public static BodyTransformerImpl transformJsonArray(Function<JsonArray, JsonArray> transformer) {
    return new BodyTransformerImpl(buffer -> transformer.apply(buffer.toJsonArray()).toBuffer(), MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON);
  }

  public static BodyTransformerImpl transformJson(Function<Object, Object> transformer) {
    return new BodyTransformerImpl(buffer -> Json.encodeToBuffer(transformer.apply(Json.decodeValue(buffer))), MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON);
  }

  public static BodyTransformerImpl transformText(Function<String, String> transformer, String encoding) {
    return new BodyTransformerImpl(buffer -> Buffer.buffer(transformer.apply(buffer.toString(encoding))), MediaType.TEXT_PLAIN, MediaType.TEXT_PLAIN);
  }

  public static BodyTransformerImpl discard() {
    return new BodyTransformerImpl(buffer -> Buffer.buffer(), null, null);
  }
}
