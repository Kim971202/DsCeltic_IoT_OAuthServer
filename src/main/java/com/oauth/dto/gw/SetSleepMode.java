package com.oauth.dto.gw;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class SetSleepMode {

    private String accessToken;
    private String userId;
    private String deviceId;
    private String controlAuthKey;
    private String onOffFlag;
    private Map<String, Map<String, String>> sleepTimerList;
    private String functionId;
    private String uuId;

}
