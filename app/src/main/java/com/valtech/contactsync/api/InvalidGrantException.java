package com.valtech.contactsync.api;

public class InvalidGrantException extends OAuthException {
  public InvalidGrantException(TokenErrorResponse response) {
    super(response);
  }
}