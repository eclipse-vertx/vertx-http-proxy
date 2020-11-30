package io.vertx.httpproxy.impl;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;

import java.util.Date;
import java.util.List;

class HttpUtils {

  static Boolean isChunked(MultiMap headers) {
    List<String> te = headers.getAll("transfer-encoding");
    if (te != null) {
      boolean chunked = false;
      for (String val : te) {
        if (val.equals("chunked")) {
          chunked = true;
        } else {
          return null;
        }
      }
      return chunked;
    } else {
      return false;
    }
  }

  static Date dateHeader(MultiMap headers) {
    String dateHeader = headers.get(HttpHeaders.DATE);
    if (dateHeader == null) {
      List<String> warningHeaders = headers.getAll("warning");
      if (warningHeaders.size() > 0) {
        for (String warningHeader : warningHeaders) {
          Date date = ParseUtils.parseWarningHeaderDate(warningHeader);
          if (date != null) {
            return date;
          }
        }
      }
      return null;
    } else {
      return ParseUtils.parseHeaderDate(dateHeader);
    }
  }
}
