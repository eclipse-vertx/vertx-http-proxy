/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.httpproxy.HttpProxy;
import io.vertx.httpproxy.ProxyOptions;
import io.vertx.junit5.VertxTest;
import org.junit.jupiter.api.BeforeEach;

import java.io.Closeable;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Base class of the proxy tests: the options, the {@link Vertx} instance and the helpers starting a proxy or a
 * backend. The {@code Vertx} instance is created, instrumented and closed by {@code VertxExtension}, so an assertion
 * error thrown in one of its handlers fails the test instead of being swallowed.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxTest
public abstract class TestBase {

  protected ProxyOptions proxyOptions;
  protected HttpServerOptions serverOptions;
  protected HttpClientOptions clientOptions;

  protected Vertx vertx;

  public TestBase() {
    this(new ProxyOptions());
  }

  public TestBase(ProxyOptions proxyOptions) {
    this.proxyOptions = proxyOptions;
    this.serverOptions = new HttpServerOptions().setPort(8080).setHost("localhost");
    this.clientOptions = new HttpClientOptions();
  }

  @BeforeEach
  public void setUp(Vertx vertx) {
    this.vertx = vertx;
  }

  protected Closeable startProxy(SocketAddress backend) {
    return startProxy(proxy -> proxy.origin(backend));
  }

  protected Closeable startProxy(Consumer<HttpProxy> config) {
    return startProxy(UnaryOperator.identity(), config);
  }

  protected Closeable startProxy(UnaryOperator<HttpServerOptions> proxyOptionsConfig, Consumer<HttpProxy> config) {
    CompletableFuture<Closeable> res = new CompletableFuture<>();
    vertx.deployVerticle(new AbstractVerticle() {
      HttpClient proxyClient;
      HttpServer proxyServer;
      HttpProxy proxy;
      @Override
      public void start(Promise<Void> startFuture) {
        proxyClient = vertx.createHttpClient(new HttpClientOptions(clientOptions));
        proxyServer = vertx.createHttpServer(proxyOptionsConfig.apply(new HttpServerOptions(serverOptions)));
        proxy = HttpProxy.reverseProxy(proxyOptions, proxyClient);
        config.accept(proxy);
        proxyServer.requestHandler(proxy);
        proxyServer.listen().onComplete(ar -> startFuture.handle(ar.mapEmpty()));
      }
    }).onComplete(ar -> {
      if (ar.succeeded()) {
        String id = ar.result();
        res.complete(() -> {
          CountDownLatch latch = new CountDownLatch(1);
          vertx.undeploy(id).onComplete(ar2 -> latch.countDown());
          try {
            latch.await(10, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
          }
        });
      } else {
        res.completeExceptionally(ar.cause());
      }
    });
    try {
      return res.get(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    } catch (ExecutionException e) {
      throw new AssertionError(e.getMessage());
    } catch (TimeoutException e) {
      throw new AssertionError(e);
    }
  }

  protected void startHttpServer(HttpServerOptions options, Handler<HttpServerRequest> handler) {
    HttpServer proxyServer = vertx.createHttpServer(options);
    proxyServer.requestHandler(handler);
    proxyServer.listen().await();
  }

  protected SocketAddress startHttpBackend(int port, Handler<HttpServerRequest> handler) {
    return startHttpBackend(new HttpServerOptions().setPort(port).setHost("localhost"), handler);
  }

  protected SocketAddress startHttpBackend(HttpServerOptions options, Handler<HttpServerRequest> handler) {
    HttpServer backendServer = vertx.createHttpServer(options);
    backendServer.requestHandler(handler);
    backendServer.listen().await();
    return SocketAddress.inetSocketAddress(options.getPort(), "localhost");
  }

  protected SocketAddress startNetBackend(int port, Handler<NetSocket> handler) {
    NetServer backendServer = vertx.createNetServer(new HttpServerOptions().setPort(port).setHost("localhost"));
    backendServer.connectHandler(handler);
    backendServer.listen().await();
    return SocketAddress.inetSocketAddress(port, "localhost");
  }

}
