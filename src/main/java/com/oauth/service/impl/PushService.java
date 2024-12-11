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
import java.util.Objects;

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

    public void sendPushMessage(String jsonBody, String pushToken, String fPushYn, String userId, String modelCode, String title) throws Exception {
        log.info("sendPushMessage jsonBody: " + jsonBody);

        HashMap<String, String> pushMap = new HashMap<>();
        try {
            pushMap.put("targetToken", pushToken);
            pushMap.put("pushYn", fPushYn);
            pushMap.put("modelCode", modelCode);
            pushMap.put("title", title);
            pushMap.put("body", common.putQuotes(common.returnConValue(jsonBody)));
            pushMap.put("id", userId);

            mobiusService.createCin("ToPushServer", "ToPushServerCnt", JSON.toJson(pushMap));
        } catch (Exception e){
            log.error("", e);
        }
    }

    public void sendPushMessage(String jsonBody, String errroCode, String errorMesssage, String modelCode, String errorVersion) throws Exception {
        log.info("sendPushMessage jsonBody: " + jsonBody);

        String deviceId = common.readCon(jsonBody, "deviceId");

        AuthServerDTO info = deviceMapper.getGroupNameAndDeviceNickByDeviceId(deviceId);

        HashMap<String, String> pushMap = new HashMap<>();
        List<AuthServerDTO> pushInfo = deviceMapper.getPushinfoByDeviceId(deviceId);
        log.info("pushInfo: " + pushInfo);

        deviceMapper.updateDeviceErrorStatus(deviceId);

        try {
            for (AuthServerDTO authServerDTO : pushInfo) {
                log.info("authServerDTO.getPushToken(): " + authServerDTO.getPushToken());
                log.info("authServerDTO.getUserId(): " + authServerDTO.getUserId());
                log.info("authServerDTO.getSPushYn(): " + authServerDTO.getSPushYn());

                AuthServerDTO params = new AuthServerDTO();
                params.setUserId(authServerDTO.getUserId());
                params.setPushTitle(errroCode);
                params.setPushType("02");
                params.setPushContent(Objects.requireNonNullElse(errorVersion, ""));
                params.setDeviceId(deviceId);
                params.setDeviceNickname(info.getDeviceNickname());
                params.setGroupName(info.getGroupName());
                params.setDeviceType(common.getModelCode(modelCode));
                if(memberMapper.insertPushHistory(params) <= 0) log.info("PUSH ERROR HISTORY INSERT ERROR");

                pushMap.put("targetToken", authServerDTO.getPushToken());
                pushMap.put("title","ERROR");
                pushMap.put("body", common.putQuotes(common.returnConValue(common.readCon(jsonBody, "con"))));
                pushMap.put("id", authServerDTO.getUserId());
                pushMap.put("pushYn", authServerDTO.getSPushYn());
                pushMap.put("modelCode", modelCode);

                mobiusService.createCin("ToPushServer", "ToPushServerCnt", JSON.toJson(pushMap));
            }

        } catch (Exception e){
            log.error("", e);
        }
    }

}
