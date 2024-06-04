package com.oauth.dto.gw;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;

@Getter
@Setter
public class SetWeek {

    private String userId;
    private String deviceId;
    private String controlAuthKey;
    private List<HashMap<String, Object>> weekList;
    private String onOffFlag;

    private String functionId;
    private String uuId;

}
