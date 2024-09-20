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
    private String onHour;
    private String onMinute;
    private String offHour;
    private String offMinute;
    private String onOffFlag;
    private Map<String, Map<String, String>> timeSchedule;
    private String functionId;
    private String uuId;

}
