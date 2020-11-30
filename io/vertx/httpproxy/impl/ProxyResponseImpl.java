package io.vertx.httpproxy.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.streams.Pipe;
import io.vertx.core.streams.ReadStream;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

class ProxyResponseImpl implements ProxyResponse {

  private final ProxyRequestImpl request;
  private final HttpServerResponse edgeResponse;
  private int statusCode;
  private Body body;
  private MultiMap headers;
  private HttpClientResponse originResponse;
  private long maxAge;
  private String etag;
  private boolean publicCacheControl;
  private Function<ReadStream<Buffer>, ReadStream<Buffer>> bodyFilter = Function.identity();

  ProxyResponseImpl(ProxyRequestImpl request, HttpServerResponse edgeResponse) {
    this.originResponse = null;
    this.statusCode = 200;
    this.headers = MultiMap.caseInsensitiveMultiMap();
    this.request = request;
    this.edgeResponse = edgeResponse;
  }

  ProxyResponseImpl(ProxyRequestImpl request, HttpServerResponse edgeResponse, HttpClientResponse originResponse) {

    // Determine content length
    long contentLength = -1L;
    String contentLengthHeader = originResponse.getHeader(HttpHeaders.CONTENT_LENGTH);
    if (contentLengthHeader != null) {
      try {
        contentLength = Long.parseLong(contentLengthHeader);
      } catch (NumberFormatException e) {
        // Ignore ???
      }
    }

    this.request = request;
    this.originResponse = originResponse;
    this.edgeResponse = edgeResponse;
    this.statusCode = originResponse.statusCode();
    this.body = Body.body(originResponse, contentLength);

    long maxAge = -1;
    boolean publicCacheControl = false;
    String cacheControlHeader = originResponse.getHeader(HttpHeaders.CACHE_CONTROL);
    if (cacheControlHeader != null) {
      CacheControl cacheControl = new CacheControl().parse(cacheControlHeader);
      if (cacheControl.isPublic()) {
        publicCacheControl = true;
        if (cacheControl.maxAge() > 0) {
          maxAge = (long)cacheControl.maxAge() * 1000;
        } else {
          String dateHeader = originResponse.getHeader(HttpHeaders.DATE);
          String expiresHeader = originResponse.getHeader(HttpHeaders.EXPIRES);
          if (dateHeader != null && expiresHeader != null) {
            maxAge = ParseUtils.parseHeaderDate(expiresHeader).getTime() - ParseUtils.parseHeaderDate(dateHeader).getTime();
          }
        }
      }
    }
    this.maxAge = maxAge;
    this.publicCacheControl = publicCacheControl;
    this.etag = originResponse.getHeader(HttpHeaders.ETAG);
    this.headers = MultiMap.caseInsensitiveMultiMap().addAll(originResponse.headers());
  }

  @Override
  public ProxyRequest request() {
    return request;
  }

  @Override
  public int getStatusCode() {
    return statusCode;
  }

  @Override
  public ProxyResponseImpl setStatusCode(int sc) {
    statusCode = sc;
    return this;
  }

  @Override
  public Body getBody() {
    return body;
  }

  @Override
  public ProxyResponseImpl setBody(Body body) {
    this.body = body;
    return this;
  }

  @Override
  public boolean publicCacheControl() {
    return publicCacheControl;
  }

  @Override
  public long maxAge() {
    return maxAge;
  }

  @Override
  public String etag() {
    return etag;
  }

  @Override
  public MultiMap headers() {
    return headers;
  }

  @Override
  public ProxyResponse putHeader(CharSequence name, CharSequence value) {
    headers.set(name, value);
    return this;
  }

  @Override
  public ProxyResponse bodyFilter(Function<ReadStream<Buffer>, ReadStream<Buffer>> filter) {
    bodyFilter = filter;
    return this;
  }

  @Override
  public void send(Handler<AsyncResult<Void>> completionHandler) {
    // Set stuff
    edgeResponse.setStatusCode(statusCode);

    // Date header
    Date date = HttpUtils.dateHeader(headers);
    if (date == null) {
      date = new Date();
    }
    try {
      edgeResponse.putHeader("date", ParseUtils.formatHttpDate(date));
    } catch (Exception e) {
      e.printStackTrace();
    }

    // Warning header
    List<String> warningHeaders = headers.getAll("warning");
    if (warningHeaders.size() > 0) {
      warningHeaders = new ArrayList<>(warningHeaders);
      String dateHeader = headers.get("date");
      Date dateInstant = dateHeader != null ? ParseUtils.parseHeaderDate(dateHeader) : null;
      Iterator<String> i = warningHeaders.iterator();
      // Suppress incorrect warning header
      while (i.hasNext()) {
        String warningHeader = i.next();
        Date warningInstant = ParseUtils.parseWarningHeaderDate(warningHeader);
        if (warningInstant != null && dateInstant != null && !warningInstant.equals(dateInstant)) {
          i.remove();
        }
      }
    }
    edgeResponse.putHeader("warning", warningHeaders);

    // Handle other headers
    headers.forEach(header -> {
      String name = header.getKey();
      String value = header.getValue();
      if (name.equalsIgnoreCase("date") || name.equalsIgnoreCase("warning") || name.equalsIgnoreCase("transfer-encoding")) {
        // Skip
      } else {
        edgeResponse.headers().add(name, value);
      }
    });

    //
    if (body == null) {
      edgeResponse.end();
      return;
    }

    long len = body.length();
    if (len >= 0) {
      edgeResponse.putHeader(HttpHeaders.CONTENT_LENGTH, Long.toString(len));
    } else {
      edgeResponse.setChunked(true);
    }
    ReadStream<Buffer> bodyStream = bodyFilter.apply(body.stream());
    sendResponse(bodyStream, completionHandler);
  }

  @Override
  public ProxyResponseImpl release() {
    if (originResponse != null) {
      originResponse.resume();
      originResponse = null;
      body = null;
      headers.clear();
    }
    return this;
  }

  private void sendResponse(ReadStream<Buffer> body, Handler<AsyncResult<Void>> completionHandler) {
    Pipe<Buffer> pipe = body.pipe();
    pipe.endOnSuccess(true);
    pipe.endOnFailure(false);
    pipe.to(edgeResponse, ar -> {
      if (ar.failed()) {
        request.edgeRequest.reset();
        edgeResponse.reset();
      }
      completionHandler.handle(ar);
    });
  }
}
