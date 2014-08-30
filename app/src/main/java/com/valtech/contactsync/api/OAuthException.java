package com.valtech.contactsync.api;

import org.apache.http.Header;

public class OAuthException extends RuntimeException {
  public OAuthException(TokenErrorResponse response) {
    super("OAuth error occurred on token request ('" + response.error + "', '" + response.error_description + "').");
  }

  public OAuthException(Header wwwAuthenticateHeader) {
    super("OAuth error occurred when accessing protected resource (" + wwwAuthenticateHeader.getValue() + ")");
  }

  public static OAuthException build(TokenErrorResponse response) {
    if ("invalid_grant".equals(response.error)) return new InvalidGrantException(response);
    return new OAuthException(response);
  }
}