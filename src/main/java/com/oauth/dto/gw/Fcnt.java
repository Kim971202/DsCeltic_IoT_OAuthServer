package com.oauth.dto.gw;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Fcnt {

    private String accessToken;
    private String userId;
    private String deviceId;
    private String controlAuthKey;

    private String functionId;
    private String uuId;

}
