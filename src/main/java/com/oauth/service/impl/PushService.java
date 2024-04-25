package com.oauth.service.impl;

import com.oauth.dto.AuthServerDTO;
import com.oauth.mapper.DeviceMapper;
import com.oauth.utils.Common;
import com.oauth.utils.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;

@Slf4j
@Service
public class PushService {

    @Autowired
    MobiusService mobiusService;
    @Autowired
    Common common;
    @Autowired
    DeviceMapper deviceMapper;

    public void sendPushMessage(String jsonBody) throws Exception {

        HashMap<String, String> pushMap = new HashMap<>();
        AuthServerDTO pushInfo = deviceMapper.getPushinfoByDeviceId(common.readCon(jsonBody, "deviceId"));
        System.out.println(pushInfo);
        try {
            pushMap.put("targetToken", pushInfo.getPushToken());
            pushMap.put("title", common.readCon(jsonBody, "mfCd"));
            pushMap.put("body", common.readCon(jsonBody, "con"));
            pushMap.put("id", pushInfo.getUserId());
            pushMap.put("isEnd", "False");

            mobiusService.createCin("ToPushServer", "ToPushServerCnt", JSON.toJson(pushMap));
        } catch (Exception e){
            log.error("", e);
        }
    }

}
