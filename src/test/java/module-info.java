open module io.vertx.tests {
  requires io.netty.codec.http;
  requires io.vertx.httpproxy;
  requires io.vertx.testing.unit;
  requires io.vertx.testing.junit5;
  requires junit;
  requires org.junit.jupiter.api;
  requires org.junit.jupiter.params;
  requires wiremock.standalone;
  requires testcontainers;
  requires com.github.dockerjava.api;
  requires org.apache.commons.lang3;
  requires io.netty.common;
}
