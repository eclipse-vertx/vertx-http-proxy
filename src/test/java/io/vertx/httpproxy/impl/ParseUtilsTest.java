package io.vertx.httpproxy.impl;

import org.junit.Test;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

import static junit.framework.TestCase.assertEquals;

public class ParseUtilsTest {

  private final Instant RESULT_DATE = Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse("Tue, 2 Jan 2024 12:34:56 GMT"));

  /**
   * Test parse RFC_1123_DATE_TIME : EEE, dd MMM yyyy HH:mm:ss z
   *
   * @throws Exception
   */
  @Test
  public void testParseHttpDateRFC_1123_DATE_TIME() throws Exception {
    assertEquals(RESULT_DATE, ParseUtils.parseHttpDate("Tue, 2 Jan 2024 12:34:56 GMT"));
    assertEquals(RESULT_DATE, ParseUtils.parseHttpDate("Tue, 02 Jan 2024 13:34:56 +0100"));
  }

  @Test
  public void testFormatHttpDateRFC_1123_DATE_TIME() {
    assertEquals("Tue, 2 Jan 2024 12:34:56 GMT", ParseUtils.formatHttpDate(RESULT_DATE));
  }

  /**
   * Test parse RFC_850_DATE_TIME : EEEEEEEEE, dd-MMM-yy HH:mm:ss zzz
   *
   * @throws Exception
   */
  @Test
  public void testParseHttpDateRFC_850_DATE_TIME() throws Exception {
    assertEquals(RESULT_DATE, ParseUtils.parseHttpDate("Tuesday, 02-Jan-24 12:34:56 GMT"));
  }

  /**
   * Test parse ASC_TIME : EEE MMM d HH:mm:ss yyyy
   *
   * @throws Exception
   */
  @Test
  public void testParseHttpDateASC_TIME() throws Exception {
    assertEquals(RESULT_DATE, ParseUtils.parseHttpDate("Tue Jan 2 12:34:56 2024"));
  }

}
