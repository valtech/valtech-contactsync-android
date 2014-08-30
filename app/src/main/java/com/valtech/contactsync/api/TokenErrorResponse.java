package com.valtech.contactsync.api;

import com.google.gson.annotations.SerializedName;

public class TokenErrorResponse {
  public String error;

  @SerializedName("error_description")
  public String error_description;
}