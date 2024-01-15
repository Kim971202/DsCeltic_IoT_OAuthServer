package com.oauth.utils;

public class Publics {

    /**
     * 서버 간 API 통신 할때 사용하는 액세스 토큰
     */
    public static final String KEY_API_ACCESS_TOKEN = "_TOKEN";

    /**
     * 인증토큰 내에 값을 설정할때 값을 특정 할 수 없을 경우 기본값으로 사용.
     */
//	public static final String TOKEN_SOME_DEFAULT_VALUE = "NULL";
    /**
     * 홈IoT 월패드의 경우 인증토큰 내에 hid 값을 알수 없기에 기본값으로 사용.
     */
    public static final String HID_DEFAULT_VALUE = "0000.0000.0000";
    public static final String IOT_DEFAULT_VALUE = "00000000-0000-0000-0000-000000000000";
    public static final String FID_INTERNAL_API_VALUE = "INTERNAL_API";

    /**
     * 서버 ID
     */
    public static final String APP_ADMIN_SERVER = "app-admin-server";
    public static final String APP_FRONT_DAESUNG_SERVER = "app-front-daesung-server";
    public static final String APP_DGGW_IOT_SERVER = "app-dggw-iot-server";
    public static final String APP_DGGW_SERVER = "app-dggw-server";
    public static final String APP_FRONT_GOOGLE_SERVER = "app-front-google-server";
    public static final String APP_PUSH_SERVER = "app-push-server";
    public static final String APP_PLATFORM_SERVER = "app-platform-server";

    /**
     * 단지GW서버 -> 단지서버 websocket 연결요청 성공시 deviceId 값.
     */
    public static final String DG_SOCKET_CONNECTED = "DG_SOCKET_CONNECTED";

}
