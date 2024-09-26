package com.oauth.dto.gw;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FastHotWaterSet {

    private String accessToken;
    private String userId;
    private String deviceId;
    private String controlAuthKey;
    private String ftMdSet;

    private String functionId;
    private String uuId;


}
