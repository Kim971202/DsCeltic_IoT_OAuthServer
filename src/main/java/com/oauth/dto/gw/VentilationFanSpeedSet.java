package com.oauth.dto.gw;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VentilationFanSpeedSet {

    private String userId;
    private String deviceId;
    private String controlAuthKey;
    private String modelCode;
    private String fanSpeed;
    private String functionId;
    private String uuId;

}
