package com.oauth.dto.gw;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class SetOnOffPower {

    private String accessToken;
    private String userId;
    private String deviceId;
    private String controlAuthkey;
    private String onOffFlag;
    private Map<String, String> onOffTimerList;
    private String functionId;
    private String uuId;
}
