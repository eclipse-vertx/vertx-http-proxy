/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.grpc;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.WaitContainerCmd;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import io.netty.util.internal.PlatformDependent;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.httpproxy.HttpProxy;
import io.vertx.httpproxy.ProxyOptions;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.InternetProtocol;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

public class GrpcProxyIntegrationTest {

  private static final int GRPC_SERVER_PORT = 50051;
  private static final int PROXY_PORT = 8080;

  private Vertx vertx;
  private HttpServer proxyServer;
  private HttpClient httpClient;
  private ServerContainer<?> grpcServerContainer;
  private GenericContainer<?> grpcClientContainer;

  @BeforeClass
  public static void beforeClass() throws Exception {
    assumeFalse("Cannot run Linux containers on Windows", PlatformDependent.isWindows());
  }

  @Before
  public void setUp() throws Exception {
    vertx = Vertx.vertx();
  }

  @After
  public void tearDownContainers() {
    if (grpcClientContainer != null) {
      grpcClientContainer.stop();
    }
    if (grpcServerContainer != null) {
      grpcServerContainer.stop();
    }
    if (proxyServer != null) {
      proxyServer.close().await();
    }
    if (httpClient != null) {
      httpClient.close().await();
    }
    if (vertx != null) {
      vertx.close().await();
    }
  }

  @Test
  public void testGrpcThroughProxy() throws Exception {
    startGrpcServer();
    startProxy();
    int exitCode = runGrpcClient();
    assertEquals("gRPC client tests should pass", 0, exitCode);
  }

  private void startProxy() {
    httpClient = vertx.createHttpClient(new HttpClientOptions()
      .setProtocolVersion(io.vertx.core.http.HttpVersion.HTTP_2)
      .setHttp2ClearTextUpgrade(false));

    SocketAddress backend = SocketAddress.inetSocketAddress(grpcServerContainer.getMappedPort(GRPC_SERVER_PORT), grpcServerContainer.getHost());
    HttpProxy proxy = HttpProxy.reverseProxy(new ProxyOptions(), httpClient).origin(backend);

    proxyServer = vertx.createHttpServer(new HttpServerOptions()
        .setPort(PROXY_PORT)
        .setHost("0.0.0.0")
        .setHttp2ClearTextEnabled(true))
      .requestHandler(proxy)
      .listen()
      .await();
  }

  private void startGrpcServer() throws Exception {
    grpcServerContainer = new ServerContainer<>(new ImageFromDockerfile("vertx-http-proxy-grpc-server", false)
      .withFileFromClasspath("Dockerfile", "grpc/server/Dockerfile")
      .withFileFromClasspath("server.js", "grpc/server/server.js")
      .withFileFromClasspath("package.json", "grpc/package.json")
      .withFileFromClasspath("test.proto", "grpc/test.proto"));
    if (System.getProperties().containsKey("containerFixedPort")) {
      grpcServerContainer.withFixedExposedPort(GRPC_SERVER_PORT, GRPC_SERVER_PORT);
    } else {
      grpcServerContainer.withExposedPorts(GRPC_SERVER_PORT);
    }
    grpcServerContainer
      .withEnv("GRPC_PORT", String.valueOf(GRPC_SERVER_PORT))
      .withEnv("GRPC_HOST", "0.0.0.0")
      .waitingFor(Wait.forLogMessage(".*gRPC server listening.*", 1));

    grpcServerContainer.start();
  }

  private int runGrpcClient() throws Exception {
    grpcClientContainer = new GenericContainer<>(
      new ImageFromDockerfile("vertx-http-proxy-grpc-client", false)
        .withFileFromClasspath("Dockerfile", "grpc/client/Dockerfile")
        .withFileFromClasspath("client.js", "grpc/client/client.js")
        .withFileFromClasspath("package.json", "grpc/package.json")
        .withFileFromClasspath("test.proto", "grpc/test.proto"))
      .withNetworkMode("host")
      .withEnv("GRPC_SERVER", String.format("localhost:%d", PROXY_PORT))
      .withStartupCheckStrategy(new OneShotStartupCheckStrategy())
      .withStartupTimeout(Duration.ofMinutes(3));

    grpcClientContainer.start();

    DockerClient dockerClient = grpcClientContainer.getDockerClient();
    try (WaitContainerCmd cmd = dockerClient.waitContainerCmd(grpcClientContainer.getContainerId())) {
      return cmd.exec(new WaitContainerResultCallback())
        .awaitStatusCode();
    }
  }

  private static class ServerContainer<SELF extends ServerContainer<SELF>> extends GenericContainer<SELF> {

    public ServerContainer(java.util.concurrent.Future<String> dockerImageName) {
      super(dockerImageName);
    }

    public SELF withFixedExposedPort(int hostPort, int containerPort) {
      super.addFixedExposedPort(hostPort, containerPort, InternetProtocol.TCP);
      return self();
    }
  }
}
