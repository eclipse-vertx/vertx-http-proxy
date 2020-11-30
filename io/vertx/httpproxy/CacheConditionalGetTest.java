package io.vertx.httpproxy;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.impl.SocketAddressImpl;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.httpproxy.impl.ParseUtils;
import org.junit.Rule;
import org.junit.Test;

import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CacheConditionalGetTest extends ProxyTestBase {

  private AtomicInteger hits = new AtomicInteger();
  private HttpClient client;

  @Rule
  public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().port(8081));

  @Override
  public void setUp() {
    super.setUp();
    hits.set(0);
    client = vertx.createHttpClient();
  }

  @Test
  public void testIfModifiedSinceRespondsNotModified(TestContext ctx) throws Exception {
    long now = System.currentTimeMillis();
    stubFor(get(urlEqualTo("/img.jpg")).inScenario("s").whenScenarioStateIs(STARTED)
        .willReturn(
            aResponse()
                .withStatus(200)
                .withHeader("Cache-Control", "public")
                .withHeader("ETag", "tag0")
                .withHeader("Date", ParseUtils.formatHttpDate(new Date(now)))
                .withHeader("Last-Modified", ParseUtils.formatHttpDate(new Date(now - 5000)))
                .withHeader("Expires", ParseUtils.formatHttpDate(new Date(now + 5000)))
                .withBody("content")));
    startProxy(new SocketAddressImpl(8081, "localhost"));
    Async latch = ctx.async();
    client.request(HttpMethod.GET, 8080, "localhost", "/img.jpg").compose(req1 ->
      req1.send().compose(resp1 -> {
        ctx.assertEquals(200, resp1.statusCode());
        return resp1.body();
      })
    ).onComplete(ctx.asyncAssertSuccess(body1 -> {
      ctx.assertEquals("content", body1.toString());
      vertx.setTimer(3000, id -> {
        client.request(HttpMethod.GET, 8080, "localhost", "/img.jpg")
            .compose(req2 -> req2
                .putHeader(HttpHeaders.IF_MODIFIED_SINCE, ParseUtils.formatHttpDate(new Date(now - 5000)))
                .send()
                .compose(resp2 -> {
              ctx.assertEquals(304, resp2.statusCode());
              return resp2.body();
            })).onComplete(ctx.asyncAssertSuccess(body2 -> {
          ctx.assertEquals("", body2.toString());
          latch.complete();
        }));
      });
    }));
    latch.awaitSuccess(10000);
/*
    ServeEvent event1 = getAllServeEvents().get(1);
    assertNull(event1.getRequest().getHeader("If-None-Match"));
    assertEquals(200, event1.getResponse().getStatus());
    ServeEvent event0 = getAllServeEvents().get(0);
    assertEquals("tag0", event0.getRequest().getHeader("If-None-Match"));
    assertEquals(304, event0.getResponse().getStatus());
*/
  }
}
