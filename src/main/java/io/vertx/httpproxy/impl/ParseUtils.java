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
package io.vertx.httpproxy.impl;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ParseUtils {

  public static final DateTimeFormatter RFC_850_DATE_TIME = new DateTimeFormatterBuilder()
    .appendPattern("EEEE, dd-MMM-yy HH:mm:ss")
    .parseLenient()
    .appendLiteral(" GMT")
    .toFormatter(Locale.US)
    .withZone(ZoneId.of("UTC"));

  public static final DateTimeFormatter ASC_TIME = new DateTimeFormatterBuilder()
    .appendPattern("EEE MMM d HH:mm:ss yyyy")
    .parseLenient()
    .toFormatter(Locale.US)
    .withZone(ZoneId.of("UTC"));

  public static Instant parseHeaderDate(String value) {
    try {
      return parseHttpDate(value);
    } catch (Exception e) {
      return null;
    }
  }

  public static Instant parseWarningHeaderDate(String value) {
    // warn-code
    int index = value.indexOf(' ');
    if (index > 0) {
      // warn-agent
      index = value.indexOf(' ', index + 1);
      if (index > 0) {
        // warn-text
        index = value.indexOf(' ', index + 1);
        if (index > 0) {
          // warn-date
          int len = value.length();
          if (index + 2 < len && value.charAt(index + 1) == '"' && value.charAt(len - 1) == '"') {
            // Space for 2 double quotes
            String date = value.substring(index + 2, len - 1);
            try {
              return parseHttpDate(date);
            } catch (Exception ignore) {
            }
          }
        }
      }
    }
    return null;
  }

  public static String formatHttpDate(Instant date) {
    return DateTimeFormatter.RFC_1123_DATE_TIME.format(OffsetDateTime.ofInstant(date, ZoneOffset.UTC));
  }

  // https://www.rfc-editor.org/rfc/rfc9110#http.date
  public static Instant parseHttpDate(String value) throws Exception {
    int pos = value.indexOf(',');
    if (pos == 3) { // e.g. Sun, 06 Nov 1994 08:49:37 GMT
      return DateTimeFormatter.RFC_1123_DATE_TIME.parse(value, Instant::from);
    }
    if (pos == -1) { // e.g. Sun Nov  6 08:49:37 1994
      return ASC_TIME.parse(value, Instant::from);
    }
    return RFC_850_DATE_TIME.parse(value, Instant::from); // e.g. Sunday, 06-Nov-94 08:49:37 GMT
  }
}
