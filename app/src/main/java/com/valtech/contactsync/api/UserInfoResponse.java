package com.valtech.contactsync.api;

import com.google.gson.annotations.SerializedName;

public class UserInfoResponse {
  public String email;
  public String name;

  @SerializedName("country_code")
  public String countryCode;

  @SerializedName("phone_number")
  public String phoneNumber;

  @SerializedName("fixed_phone_number")
  public String fixedPhoneNumber;
}
