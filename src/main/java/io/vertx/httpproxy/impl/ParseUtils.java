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
    .appendLiteral(" UTC")
    .toFormatter(Locale.US).withZone(ZoneId.of("UTC"));

  public static final DateTimeFormatter ASC_TIME = new DateTimeFormatterBuilder()
    .appendPattern("EEE MMM d HH:mm:ss yyyy")
    .parseLenient()
    .toFormatter(Locale.US).withZone(ZoneId.of("UTC"));


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

  // http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3.1
  public static Instant parseHttpDate(String value) throws Exception {
    int sep = 0;
    while (true) {
      if (sep < value.length()) {
        char c = value.charAt(sep);
        if (c == ',') {
          String s = value.substring(0, sep);
          if (parseWkday(s) != null) {
            // rfc1123-date
            return Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(value));
          } else if (parseWeekday(s) != null) {
            // rfc850-date
            return Instant.from(RFC_850_DATE_TIME.parse(value));
          }
          return null;
        }  else if (c == ' ') {
          String s = value.substring(0, sep);
          if (parseWkday(s) != null) {
            // asctime-date
            return Instant.from(ASC_TIME.parse(value));
          }
          return null;
        }
        sep++;
      } else {
        return null;
      }
    }
  }

  private static DayOfWeek parseWkday(String value) {
    switch (value) {
      case "Mon":
        return DayOfWeek.MONDAY;
      case "Tue":
        return DayOfWeek.TUESDAY;
      case "Wed":
        return DayOfWeek.WEDNESDAY;
      case "Thu":
        return DayOfWeek.THURSDAY;
      case "Fri":
        return DayOfWeek.FRIDAY;
      case "Sat":
        return DayOfWeek.SATURDAY;
      case "Sun":
        return DayOfWeek.SUNDAY;
      default:
        return null;
    }
  }

  private static DayOfWeek parseWeekday(String value) {
    switch (value) {
      case "Monday":
        return DayOfWeek.MONDAY;
      case "Tuesday":
        return DayOfWeek.TUESDAY;
      case "Wednesday":
        return DayOfWeek.WEDNESDAY;
      case "Thursday":
        return DayOfWeek.THURSDAY;
      case "Friday":
        return DayOfWeek.FRIDAY;
      case "Saturday":
        return DayOfWeek.SATURDAY;
      case "Sunday":
        return DayOfWeek.SUNDAY;
      default:
        return null;
    }
  }
}
