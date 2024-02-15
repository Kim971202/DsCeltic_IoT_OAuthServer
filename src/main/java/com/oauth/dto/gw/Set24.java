package com.oauth.dto.gw;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Set24 {

    private String accessToken;
    private String userId;
    private String deviceId;
    private String controlAuthKey;
    private List<String> hours;
    private String type24h;
    private String onOffFlag;

    private String functionId;
    private String uuId;

}
