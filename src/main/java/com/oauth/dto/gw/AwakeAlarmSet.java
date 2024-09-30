package com.oauth.dto.gw;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Getter
@Setter
public class AwakeAlarmSet {

    private String accessToken;
    private String userId;
    private String deviceId;
    private String controlAuthKey;

    private List<HashMap<String, Object>> awakeList;

    private String functionId;
    private String uuId;
}
