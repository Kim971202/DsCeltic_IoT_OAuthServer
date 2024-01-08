package com.oauth.constants;

public class Constants {

    // 로그인 URI
    public static final String LOGIN_URI = "/v1/ui/member/formLogin.do";
    public static final String CLIENT_ID = "clientId";
    public static final String SCOPE_READ = "read";
    public static final String RESOURCE_SERVER = "http://localhost:8080";

    public static final String DEFAULT_AUTHORIZE_URI = "/oauth/authorize?client_id=" + Constants.CLIENT_ID
            + "&response_type=code&scope=" + Constants.SCOPE_READ + "&redirect_uri=" + Constants.RESOURCE_SERVER
            + "/authorization_code";
}
