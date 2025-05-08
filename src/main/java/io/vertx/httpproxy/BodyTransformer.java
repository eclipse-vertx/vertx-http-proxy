package io.vertx.httpproxy;

import io.vertx.codegen.annotations.Unstable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.httpproxy.impl.BodyTransformerImpl;

import java.util.function.Function;

/**
 * A synchronous function that transforms an HTTP body entity.
 */
@VertxGen
@Unstable
public interface BodyTransformer extends Function<Buffer, Buffer> {

  /**
   * Create a callback for transform JsonObject.
   *
   * @param transformer the operation to transform data
   * @return the built callback
   */
  static BodyTransformer transformJsonObject(Function<JsonObject, JsonObject> transformer) {
    return BodyTransformerImpl.transformJsonObject(transformer);
  }

  /**
   * Create a callback for transform JsonArray.
   *
   * @param transformer the operation to transform data
   * @return the built callback
   */
  static BodyTransformer transformJsonArray(Function<JsonArray, JsonArray> transformer) {
    return BodyTransformerImpl.transformJsonArray(transformer);
  }

  /**
   * Create a callback for transform json with unknown shape.
   *
   * @param transformer the operation to transform data
   * @return the built callback
   */
  static BodyTransformer transformJson(Function<Object, Object> transformer) {
    return BodyTransformerImpl.transformJson(transformer);
  }

  /**
   * Create a callback for transform texts.
   *
   * @param transformer the operation to transform data
   * @return the built callback
   */
  static BodyTransformer transformText(Function<String, String> transformer, String encoding) {
    return BodyTransformerImpl.transformText(transformer, encoding);
  }

  /**
   * Create a callback to discard the body.
   *
   * @return the built callback
   */
  static BodyTransformer discard() {
    return BodyTransformerImpl.discard();
  }
}
