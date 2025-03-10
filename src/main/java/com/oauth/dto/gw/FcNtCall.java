package com.oauth.dto.gw;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FcNtCall {
    
    private String deviceId;
    private String uuId;
    private String functionId;
    private String modelCode;
    private String TempAndSystemNotice;

}
