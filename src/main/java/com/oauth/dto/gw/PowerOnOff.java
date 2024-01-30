package com.oauth.dto.gw;


import lombok.*;
import org.codehaus.jackson.annotate.JsonIgnore;


@Getter
@Setter
public class PowerOnOff {

    private String userId;
    private String deviceId;
    private String controlAuthKey;
    private String deviceType;
    private String modelCode;
    private String powerStatus;
    private String functionId;
    private String uuId;

}
