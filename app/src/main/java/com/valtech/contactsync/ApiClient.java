package com.valtech.contactsync;

import android.content.Context;
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

  private final String authorizeUrl;
  private final String tokenUrl;
  private final String userInfoUrl;
  private final String allUserInfosUrl;
  private final String clientId;
  private final String clientSecret;
  private final String initialScope;
  private final String followUpScope;

  public ApiClient(Context context) {
    authorizeUrl = context.getString(R.string.idp_authorize_url);
    tokenUrl = context.getString(R.string.idp_token_url);
    userInfoUrl = context.getString(R.string.idp_user_info_url);
    allUserInfosUrl = context.getString(R.string.idp_all_user_infos_url);
    clientId = context.getString(R.string.idp_client_id);
    clientSecret = context.getString(R.string.idp_client_secret);
    initialScope = context.getString(R.string.idp_initial_scope);
    followUpScope = context.getString(R.string.idp_follow_up_scope);
  }

  public String getAuthorizeUrl() {
    return authorizeUrl + "?response_type=code&client_id=" + clientId + "&scope=" + initialScope.replace(" ", "%20");
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
      new BasicNameValuePair("scope", followUpScope));
    return tokenResponse.accessToken;
  }

  private TokenResponse tokenRequest(NameValuePair... fields) {
    HttpClient httpClient = new DefaultHttpClient();
    HttpPost httpPost = new HttpPost(tokenUrl);

    try {
      List<NameValuePair> nameValuePairs = new ArrayList<>();
      nameValuePairs.add(new BasicNameValuePair("client_id", clientId));
      nameValuePairs.add(new BasicNameValuePair("client_secret", clientSecret));
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
    return getProtectedResource(userInfoUrl, accessToken, UserInfoResponse.class);
  }

  public List<UserInfoResponse> getUserInfoResources(String accessToken) {
    return getProtectedResource(allUserInfosUrl, accessToken, new TypeToken<List<UserInfoResponse>>(){}.getType());
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
