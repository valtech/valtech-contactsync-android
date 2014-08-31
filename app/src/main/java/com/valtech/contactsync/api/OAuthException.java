package com.valtech.contactsync.api;

import org.apache.http.Header;

public class OAuthException extends RuntimeException {
  private OAuthException(String message) {
    super(message);
  }

  protected OAuthException(TokenErrorResponse response) {
    super("OAuth error occurred on token request ('" + response.error + "', '" + response.error_description + "').");
  }

  private OAuthException(Header wwwAuthenticateHeader) {
    super("OAuth error occurred when accessing protected resource (" + wwwAuthenticateHeader.getValue() + ")");
  }

  public static OAuthException build(TokenErrorResponse response) {
    if ("invalid_grant".equals(response.error)) return new InvalidGrantException(response);
    return new OAuthException(response);
  }

  public static OAuthException build(Header wwwAuthenticateHeader) {
    if (wwwAuthenticateHeader == null) return new OAuthException("No WWW-Authenticate header present.");
    return new OAuthException(wwwAuthenticateHeader);
  }
}