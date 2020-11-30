package io.vertx.httpproxy;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import io.vertx.httpproxy.impl.BufferedReadStream;

@VertxGen
public interface Body {

  static Body body(ReadStream<Buffer> stream, long len) {
    return new Body() {
      @Override
      public long length() {
        return len;
      }
      @Override
      public ReadStream<Buffer> stream() {
        return stream;
      }
    };
  }

  static Body body(ReadStream<Buffer> stream) {
    return body(stream, -1L);
  }

  static Body body(Buffer buffer) {
    return new Body() {
      @Override
      public long length() {
        return buffer.length();
      }
      @Override
      public ReadStream<Buffer> stream() {
        return new BufferedReadStream(buffer);
      }
    };
  }

  /**
   * @return the body length or {@code -1} if that can't be determined
   */
  long length();

  /**
   * @return the body stream
   */
  ReadStream<Buffer> stream();

}
