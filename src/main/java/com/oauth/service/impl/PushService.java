package com.oauth.service.impl;

import com.oauth.dto.AuthServerDTO;
import com.oauth.mapper.DeviceMapper;
import com.oauth.mapper.MemberMapper;
import com.oauth.response.ApiResponse;
import com.oauth.utils.Common;
import com.oauth.utils.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

@Slf4j
@Service
public class PushService {

    @Autowired
    MobiusService mobiusService;
    @Autowired
    Common common;
    @Autowired
    DeviceMapper deviceMapper;
    @Autowired
    MemberMapper memberMapper;

    public void sendPushMessage(String jsonBody, String pushToken, String fPushYn, String userId, String modelCode) throws Exception {
        log.info("sendPushMessage jsonBody: " + jsonBody);

        HashMap<String, String> pushMap = new HashMap<>();
        try {
            pushMap.put("targetToken", pushToken);
            pushMap.put("pushYn", fPushYn);
            pushMap.put("modelCode", modelCode);
            pushMap.put("title", common.readCon(jsonBody, "mfCd"));
            pushMap.put("body", common.readCon(jsonBody, "con"));
            pushMap.put("id", userId);
            pushMap.put("isEnd", "False");

            mobiusService.createCin("ToPushServer", "ToPushServerCnt", JSON.toJson(pushMap));
        } catch (Exception e){
            log.error("", e);
        }
    }

    public void sendPushMessage(String jsonBody, String errroCode, String errorMesssage) throws Exception {
        log.info("sendPushMessage jsonBody: " + jsonBody);

        HashMap<String, String> pushMap = new HashMap<>();
        List<AuthServerDTO> pushInfo = deviceMapper.getPushinfoByDeviceId(common.readCon(jsonBody, "deviceId"));
        System.out.println(pushInfo);

        deviceMapper.updateDeviceErrorStatus(common.readCon(jsonBody, "deviceId"));

        try {
            for (AuthServerDTO authServerDTO : pushInfo) {
                log.info("authServerDTO.getPushToken(): " + authServerDTO.getPushToken());
                log.info("authServerDTO.getUserId(): " + authServerDTO.getUserId());
                log.info("authServerDTO.getSPushYn(): " + authServerDTO.getSPushYn());

                AuthServerDTO params = new AuthServerDTO();
                params.setPushTitle(errroCode);
                params.setPushContent(errorMesssage);
                params.setUserId(authServerDTO.getUserId());
                if(memberMapper.insertPushHistory(params) <= 0) log.info("PUSH ERROR HISTORY INSERT ERROR");

                pushMap.put("targetToken", authServerDTO.getPushToken());
                pushMap.put("title","ERROR");
                pushMap.put("body", common.readCon(jsonBody, "con"));
                pushMap.put("id", authServerDTO.getUserId());
                pushMap.put("pushYn", authServerDTO.getSPushYn());
                pushMap.put("isEnd", "False");

                mobiusService.createCin("ToPushServer", "ToPushServerCnt", JSON.toJson(pushMap));
            }

        } catch (Exception e){
            log.error("", e);
        }
    }

}
