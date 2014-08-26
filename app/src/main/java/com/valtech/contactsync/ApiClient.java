package com.valtech.contactsync;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ApiClient {
  private static final Gson GSON = new Gson();

  private static final String AUTHORIZE_URL = "https://stage-id.valtech.com/oauth2/authorize";
  private static final String TOKEN_URL = "https://stage-id.valtech.com/oauth2/token";
  private static final String USER_INFO_URL = "https://stage-id.valtech.com/api/users/me";
  private static final String ALL_USER_INFOS_URL = "https://stage-id.valtech.com/api/users";
  private static final String CLIENT_ID = "valtech.contactsync.android";
  private static final String CLIENT_SECRET = "MGIxZmIwMDYtZTVlOS00ZDdiLTgxYjMtMjg2MzdhMTQwNGVh";
  private static final String INITIAL_SCOPE = "email vidp:get_users vidp:offline";
  private static final String FOLLOW_UP_SCOPE = "vidp:get_users";


  public String getAuthorizeUrl() {
    return AUTHORIZE_URL + "?response_type=code&client_id=" + CLIENT_ID + "&scope=" + INITIAL_SCOPE.replace(" ", "%20");
  }

  public TokenResponse getAccessTokenAndRefreshToken(String code) {
    TokenResponse tokenResponse = tokenRequest(
      new BasicNameValuePair("grant_type", "authorization_code"),
      new BasicNameValuePair("code", code));
    return tokenResponse;
  }

  public String getAccessToken(String refreshToken) {
    TokenResponse tokenResponse = tokenRequest(
      new BasicNameValuePair("grant_type", "refresh_token"),
      new BasicNameValuePair("refresh_token", refreshToken),
      new BasicNameValuePair("scope", FOLLOW_UP_SCOPE));
    return tokenResponse.accessToken;
  }

  private TokenResponse tokenRequest(NameValuePair... fields) {
    HttpClient httpClient = new DefaultHttpClient();
    HttpPost httpPost = new HttpPost(TOKEN_URL);

    try {
      List<NameValuePair> nameValuePairs = new ArrayList<>();
      nameValuePairs.add(new BasicNameValuePair("client_id", CLIENT_ID));
      nameValuePairs.add(new BasicNameValuePair("client_secret", CLIENT_SECRET));
      nameValuePairs.addAll(Arrays.asList(fields));

      httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

      HttpResponse response = httpClient.execute(httpPost);

      int statusCode = response.getStatusLine().getStatusCode();
      if (statusCode >= 400 && statusCode < 500) {
        String json = EntityUtils.toString(response.getEntity());
        TokenErrorResponse tokenErrorResponse = GSON.fromJson(json, TokenErrorResponse.class);
        throw OAuthException.build(tokenErrorResponse);
      } else if (statusCode >= 500) {
        throw new RuntimeException("Internal server error occurred.");
      }

      String json = EntityUtils.toString(response.getEntity());
      TokenResponse tokenResponse = GSON.fromJson(json, TokenResponse.class);

      return tokenResponse;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public UserInfoResponse getUserInfoMeResource(String accessToken) {
    return getProtectedResource(USER_INFO_URL, accessToken, UserInfoResponse.class);
  }

  public List<UserInfoResponse> getUserInfoResources(String accessToken) {
    return getProtectedResource(ALL_USER_INFOS_URL, accessToken, new TypeToken<List<UserInfoResponse>>(){}.getType());
  }

  private <T> T getProtectedResource(String url, String accessToken, Type type) {
    HttpClient httpClient = new DefaultHttpClient();
    HttpGet httpGet = new HttpGet(url);

    try {
      httpGet.setHeader("Authorization", "Bearer " + accessToken);
      HttpResponse response = httpClient.execute(httpGet);

      int statusCode = response.getStatusLine().getStatusCode();
      if (statusCode >= 400 && statusCode < 500) {
        Header wwwAuthenticateHeader = response.getFirstHeader("WWW-Authenticate");
        throw new OAuthException(wwwAuthenticateHeader);
      } else if (statusCode >= 500) {
        throw new RuntimeException("Internal server error occurred.");
      }

      String json = EntityUtils.toString(response.getEntity());
      T typedResponse = GSON.fromJson(json, type);

      return typedResponse;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static class TokenResponse {
    @SerializedName("access_token")
    public String accessToken;

    @SerializedName("refresh_token")
    public String refreshToken;
  }

  public static class TokenErrorResponse {
    public String error;

    @SerializedName("error_description")
    public String error_description;
  }

  public static class UserInfoResponse {
    public String email;
    public String name;

    @SerializedName("country_code")
    public String countryCode;

    @SerializedName("phone_number")
    public String phoneNumber;

    @SerializedName("fixed_phone_number")
    public String fixedPhoneNumber;
  }

  public static class OAuthException extends RuntimeException {
    protected OAuthException(TokenErrorResponse response) {
      super("OAuth error occurred on token request ('" + response.error + "', '" + response.error_description + "').");
    }

    protected OAuthException(Header wwwAuthenticateHeader) {
      super("OAuth error occurred when accessing protected resource (" + wwwAuthenticateHeader.getValue() + ")");
    }

    public static OAuthException build(TokenErrorResponse response) {
      if ("invalid_grant".equals(response.error)) return new InvalidGrantException(response);
      return new OAuthException(response);
    }
  }

  public static class InvalidGrantException extends OAuthException {
    protected InvalidGrantException(TokenErrorResponse response) {
      super(response);
    }
  }
}
