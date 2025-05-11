package io.vertx.httpproxy;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.httpproxy.impl.MediaTypeImpl;

import java.util.List;

/**
 * Represent a Media type (rfc6838).
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public interface MediaType {

  MediaType ANY = MediaType.parse("*/*");
  MediaType APPLICATION = MediaType.parse("application/*");
  MediaType APPLICATION_OCTET_STREAM = MediaType.parse("application/octet-stream");
  MediaType APPLICATION_JSON = MediaType.parse("application/json");
  MediaType TEXT = MediaType.parse("text/*");
  MediaType TEXT_PLAIN = MediaType.parse("text/plain");

  /**
   * Parse an accept header which is a list of media types
   *
   * @param header the header
   * @return the list of media types
   * @throws IllegalArgumentException when the list is not valid
   */
  static List<MediaType> parseAcceptHeader(String header) throws IllegalArgumentException {
    return MediaTypeImpl.parseCommaSeparatedList(header, 0);
  }

  /**
   * Parse a media type.
   *
   * @param s the string representation
   * @return the parsed media type or {@code null} when the string to parse is not valid
   */
  static MediaType parse(String s) {
    return MediaTypeImpl.parseMediaType(s, 0);
  }

  /**
   * @return the type or {@code null} when everything is matched (*)
   */
  String type();

  /**
   * @return the sub type or {@code null} when everything is matched (*)
   */
  String subType();

  /**
   * @return whether the {@code other} mime type is accepted
   */
  boolean accepts(MediaType other);

  /**
   * Return a media type parameter
   *
   * @param name the parameter name
   * @return the value or {@code null}
   */
  String parameter(String name);

}
