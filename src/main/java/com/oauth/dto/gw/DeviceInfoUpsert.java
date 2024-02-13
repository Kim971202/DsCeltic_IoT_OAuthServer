package com.oauth.dto.gw;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviceInfoUpsert {

    private String accessToken;
    private String userId;
    private String hp;
    private String regisYn;
    private String deviceId;
    private String controlAuthKey;
    private String tmpRegistryKey;
    private String deviceType;
    private String modelCode;
    private String serialNumber;
    private String zipCode;
    private String oldAddr;
    private String newAddr;
    private String addrDetail;
    private String deviceNickname;
    private String addrNickname;
    private String latitude;
    private String longitude;

    private String functionId;
    private String uuId;

}
