module io.vertx.httpproxy {
  requires transitive io.vertx.core;
  requires io.vertx.core.logging;
  requires static io.vertx.codegen.api;
  requires static io.vertx.codegen.json;
  requires static vertx.docgen;
  exports io.vertx.httpproxy;
  exports io.vertx.httpproxy.cache;
  exports io.vertx.httpproxy.spi.cache;
  exports io.vertx.httpproxy.impl to io.vertx.tests;
}
