package com.oauth.dto.gw;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WaterTempertureSet {

    private String accessToken;
    private String userId;
    private String deviceId;
    private String controlAuthKey;
    private String temperature;

    private String functionId;
    private String uuId;

}
