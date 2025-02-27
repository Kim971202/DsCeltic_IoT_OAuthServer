package com.oauth.dto.gw;


import java.util.Map;

import com.oauth.utils.DataSettable;

import lombok.*;

@Getter
@Setter
public class PowerOnOff implements DataSettable {

    private String userId;
    private String deviceId;
    private String controlAuthKey;
    private String deviceType;
    private String modelCode;
    private String powerStatus;
    private String functionId;
    private String uuId;

    @Override
    public void setData(Map<String, String> data) {
        if (data == null) {
            return;
        }
        
        // 각 필드에 대해 Map에서 값을 추출하여 할당합니다.
        if (data.containsKey("userId")) {
            this.userId = data.get("userId");
        }
        if (data.containsKey("deviceId")) {
            this.deviceId = data.get("deviceId");
        }
        if (data.containsKey("controlAuthKey")) {
            this.controlAuthKey = data.get("controlAuthKey");
        }
        if (data.containsKey("deviceType")) {
            this.deviceType = data.get("deviceType");
        }
        if (data.containsKey("modelCode")) {
            this.modelCode = data.get("modelCode");
        }
        if (data.containsKey("powerStatus")) {
            this.powerStatus = data.get("powerStatus");
        }
        if (data.containsKey("functionId")) {
            this.functionId = data.get("functionId");
        }
        if (data.containsKey("uuId")) {
            this.uuId = data.get("uuId");
        }
    }

}
