package com.valtech.contactsync.api;

import android.content.Context;
import android.net.Uri;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.valtech.contactsync.R;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.util.EntityUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

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
    this.authorizeUrl = context.getString(R.string.idp_authorize_url);
    this.tokenUrl = context.getString(R.string.idp_token_url);
    this.userInfoUrl = context.getString(R.string.idp_user_info_url);
    this.allUserInfosUrl = context.getString(R.string.idp_all_user_infos_url);
    this.clientId = context.getString(R.string.idp_client_id);
    this.clientSecret = context.getString(R.string.idp_client_secret);
    this.initialScope = context.getString(R.string.idp_initial_scope);
    this.followUpScope = context.getString(R.string.idp_follow_up_scope);
  }

  public Uri getAuthorizeUrl() {
    return Uri.parse(authorizeUrl + "?response_type=code&client_id=" + clientId + "&scope=" + initialScope.replace(" ", "%20"));
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

  public BinaryResponse downloadGravatarImage(String url, int maxSize, String lastModified) throws IOException {
    String gravatarUrl = url + "?s=" + maxSize + "&d=404";
    return download(gravatarUrl, lastModified);
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
      switch (statusCode) {
        case 200:
          String json = EntityUtils.toString(response.getEntity());
          return GSON.fromJson(json, TokenResponse.class);
        case 400:
          json = EntityUtils.toString(response.getEntity());
          TokenErrorResponse tokenErrorResponse = GSON.fromJson(json, TokenErrorResponse.class);
          throw OAuthException.build(tokenErrorResponse);
        default:
          finish(response);
          throw new RuntimeException("Unhandled response code " + statusCode + ".");
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private String getLastModifiedHeader(HttpResponse response) {
    Header header = response.getFirstHeader("Last-Modified");
    if (header == null) return getCurrentHttpDate();
    if (header.getValue() == null) return getCurrentHttpDate();
    if (header.getValue().isEmpty()) return getCurrentHttpDate();
    return header.getValue();
  }

  private String getCurrentHttpDate() {
    Calendar cal = Calendar.getInstance();
    DateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
    df.setTimeZone(TimeZone.getTimeZone("UTC"));
    return df.format(cal.getTime());
  }

  public UserInfoResponse getUserInfoMeResource(String accessToken) {
    return getProtectedResource(userInfoUrl, accessToken, UserInfoResponse.class);
  }

  public List<UserInfoResponse> getUserInfoResources(String accessToken) {
    return getProtectedResource(allUserInfosUrl, accessToken, new TypeToken<List<UserInfoResponse>>(){}.getType());
  }

  private BinaryResponse download(String url, String lastModified) throws IOException {
    HttpClient httpClient = new DefaultHttpClient();
    HttpGet request = new HttpGet(url);
    if (lastModified != null) request.setHeader("If-Modified-Since", lastModified);

    // Gravatar requires a User-Agent otherwise we get 403, anything will do
    httpClient.getParams().setParameter(CoreProtocolPNames.USER_AGENT, System.getProperty("http.agent"));

    HttpResponse response = httpClient.execute(request);
    BinaryResponse binaryResponse = new BinaryResponse();

    int statusCode = response.getStatusLine().getStatusCode();
    switch (statusCode) {
      case 200:
        binaryResponse.data = new ByteArrayOutputStream();
        binaryResponse.lastModified = getLastModifiedHeader(response);
        response.getEntity().writeTo(binaryResponse.data);
        return binaryResponse;
      case 304:
        finish(response);
        binaryResponse.lastModified = lastModified;
        return binaryResponse;
      case 404:
        finish(response);
        throw new NoSuchElementException();
      default:
        finish(response);
        throw new RuntimeException("Unhandled response code " + statusCode + ".");
    }
  }

  private <T> T getProtectedResource(String url, String accessToken, Type type) {
    HttpClient httpClient = new DefaultHttpClient();
    HttpGet httpGet = new HttpGet(url);

    try {
      httpGet.setHeader("Authorization", "Bearer " + accessToken);
      HttpResponse response = httpClient.execute(httpGet);

      int statusCode = response.getStatusLine().getStatusCode();
      switch (statusCode) {
        case 200:
          String json = EntityUtils.toString(response.getEntity());
          return GSON.fromJson(json, type);
        case 401:
          Header wwwAuthenticateHeader = response.getFirstHeader("WWW-Authenticate");
          finish(response);
          throw OAuthException.build(wwwAuthenticateHeader);
        default:
          finish(response);
          throw new RuntimeException("Unhandled response code " + statusCode + ".");
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void finish(HttpResponse response) throws IOException {
    if (response.getEntity() == null) return;
    // consumeContent() will be renamed to finish() in next major, as it actually releases all resources
    // and allows the underlying http connection to be reused.
    // http://developer.android.com/reference/org/apache/http/HttpEntity.html#consumeContent()
    response.getEntity().consumeContent();
  }

  public static class TokenResponse {
    @SerializedName("access_token")
    public String accessToken;

    @SerializedName("refresh_token")
    public String refreshToken;
  }
}
