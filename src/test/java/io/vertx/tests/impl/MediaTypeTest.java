package io.vertx.tests.impl;

import io.vertx.httpproxy.MediaType;
import io.vertx.httpproxy.impl.MediaTypeImpl;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class MediaTypeTest {

  @Test
  public void testParseQuotedString() {
    assertEquals(0, MediaTypeImpl.parseQuotedString("", 0));
    assertEquals(0, MediaTypeImpl.parseQuotedString("\"", 0));
    assertEquals(0, MediaTypeImpl.parseQuotedString("\"A", 0));
    assertEquals(3, MediaTypeImpl.parseQuotedString("\"A\"", 0));
    assertEquals(0, MediaTypeImpl.parseQuotedString("\"\n\"", 0));
  }

  @Test
  public void testParseParameter() {
    assertEquals(0, MediaTypeImpl.parseParameter("foo", 0));
    assertEquals(0, MediaTypeImpl.parseParameter("foo=", 0));
    assertEquals(7, MediaTypeImpl.parseParameter("foo=bar", 0));
    assertEquals(0, MediaTypeImpl.parseParameter("foo=\"", 0));
    assertEquals(0, MediaTypeImpl.parseParameter("foo=\"bar", 0));
    assertEquals(9, MediaTypeImpl.parseParameter("foo=\"bar\"", 0));
  }

  @Test
  public void testParse() {
    assertParse("text/plain", "text", "plain");
    assertParse("text/*", "text", null);
    assertParse("*/*", null, null);
    assertParse("text/plain;", "text", "plain");
    assertParse("text/plain;a=b", "text", "plain", "a", "b");
    assertParse("text/plain;a=\"b\"", "text", "plain", "a", "b");
    assertParse("text/plain; a=b", "text", "plain", "a", "b");
    assertParse("text/plain; a=b;", "text", "plain", "a", "b");
    assertParse("text/plain; a=b ;", "text", "plain", "a", "b");
    assertParse("text/plain; a=b ; c=\"d\"", "text", "plain", "a", "b", "c", "d");
  }

  private void assertParse(String s, String expectedType, String expectedSubType, String... expectedParameters) {
    MediaType mimeType = MediaType.parse(s);
    assertEquals(expectedType, mimeType.type());
    assertEquals(expectedSubType, mimeType.subType());
    mimeType = MediaTypeImpl.parseMediaType(s, 0);
    assertEquals(expectedType, mimeType.type());
    assertEquals(expectedSubType, mimeType.subType());
    for (int i = 0;i < expectedParameters.length;i += 2) {
      String name = expectedParameters[i];
      String value = expectedParameters[i + 1];
      assertEquals(value, mimeType.parameter(name));
    }
  }

  @Test
  public void testAccepts() {
    MediaType applicationGrpc = MediaType.parse("application/grpc");
    MediaType textPlain = MediaType.parse("text/plain");
    MediaType textHtml = MediaType.parse("text/html");
    MediaType text = MediaType.parse("text/*");
    MediaType any = MediaType.parse("*/*");
    assertTrue(textPlain.accepts(textPlain));
    assertFalse(textPlain.accepts(textHtml));
    assertFalse(textPlain.accepts(applicationGrpc));
    assertTrue(text.accepts(textPlain));
    assertTrue(text.accepts(textHtml));
    assertFalse(text.accepts(applicationGrpc));
    assertTrue(any.accepts(textPlain));
    assertTrue(any.accepts(textHtml));
    assertTrue(any.accepts(applicationGrpc));
    assertFalse(textPlain.accepts(null));
    assertFalse(text.accepts(null));
    assertFalse(any.accepts(null));
  }

  @Test
  public void testParseHeader() {
    assertParseHeader("text/plain", MediaType.TEXT_PLAIN);
    assertParseHeader("text/plain,application/json", MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON);
    assertParseHeader("text/plain ,application/json", MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON);
    assertParseHeader("text/plain, application/json", MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON);
    assertParseHeader("text/plain , application/json", MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON);
    assertParseHeader("text/plain;a=b", MediaType.parse("text/plain;a=b"));
    assertParseHeader("text/plain;a=b,application/json", MediaType.parse("text/plain;a=b"), MediaType.APPLICATION_JSON);
  }

  private void assertParseHeader(String header, MediaType... expected) {
    List<MediaType> list = MediaType.parseAcceptHeader(header);
    assertEquals(expected.length, list.size());
    for (int i = 0;i < expected.length;i++) {
      assertEquals(expected[i], list.get(i));
    }
  }

  @Test
  public void testToString() {
    assertEquals("text/plain", MediaType.parse("text/plain").toString());
    assertEquals("text/plain;a=b", MediaType.parse("text/plain;a=b").toString());
    assertEquals("text/plain;a=b", MediaType.parse("text/plain; a=b").toString());
    assertEquals("text/plain;a=\"b\"", MediaType.parse("text/plain; a=\"b\"").toString());
  }
}
