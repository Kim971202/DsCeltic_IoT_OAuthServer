package com.oauth.dto.gw;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ModeChange {

    private String accessToken;
    private String userId;
    private String deviceId;
    private String controlAuthKey;
    private String modelCode;
    private String modeCode;
    private String sleepCode;

    private String functionId;
    private String uuid;

}
