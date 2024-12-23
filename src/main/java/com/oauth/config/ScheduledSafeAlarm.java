package com.oauth.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oauth.dto.AuthServerDTO;
import com.oauth.mapper.MemberMapper;
import com.oauth.service.impl.MobiusService;
import com.oauth.utils.Common;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
@Slf4j
@Component
public class ScheduledSafeAlarm {

    @Autowired
    Common common;
    @Autowired
    MobiusService mobiusService;
    @Autowired
    private MemberMapper memberMapper;

    public void checkUserSafeAlarm() throws Exception {
        List<AuthServerDTO> userInfo;
        List<AuthServerDTO> pushInfo;

        userInfo = memberMapper.getSafeAlarmSet();

        if(userInfo != null && !userInfo.isEmpty()){
            pushInfo = memberMapper.getPushTokenByUserIds(userInfo);

            // pushInfo를 Map<String, String>으로 변환 (userId -> pushToken)
            Map<String, String> pushTokenMap = new HashMap<>();
            for (AuthServerDTO push : pushInfo) {
                pushTokenMap.put(push.getUserId(), push.getPushToken());
            }

            // pushInfo를 Map<String, String>으로 변환 (userId -> userNickname)
            Map<String, String> userNicknameMap = new HashMap<>();
            for (AuthServerDTO push : pushInfo) {
                userNicknameMap.put(push.getUserId(), push.getUserNickname());
            }

            // userInfo에 pushToken 값을 추가
            for (AuthServerDTO user : userInfo) {
                String userId = user.getUserId();
                if (pushTokenMap.containsKey(userId)) {
                    user.setPushToken(pushTokenMap.get(userId));    // pushToken 설정
                }
            }

            // userInfo에 pushToken 값을 추가
            for (AuthServerDTO user : userInfo) {
                String userId = user.getUserId();
                if (userNicknameMap.containsKey(userId)) {
                    user.setUserNickname(userNicknameMap.get(userId));  // userNickname 설정
                }
            }
            System.out.println(userInfo);

            // 이후 userInfo에 pushToken이 포함된 데이터를 기반으로 작업 수행
            for (AuthServerDTO user : userInfo) {
                Map<String, String> conMap = new HashMap<>();
                ObjectMapper objectMapper = new ObjectMapper();
                conMap.put("body", "SAFE ALARM PUSH");
                conMap.put("targetToken", user.getPushToken());
                conMap.put("title", "saFe");
                conMap.put("deviceNick", common.returnDeviceNickname(user.getDeviceId()));
                conMap.put("userNickname", common.stringToHex(user.getUserNickname()));
                String jsonString = objectMapper.writeValueAsString(conMap);

                if (!mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString).getResponseCode().equals("201")) {
                    log.info("PUSH 메세지 전송 오류");
                }
            }
        } else {
            log.info("userInfo is NULL");
        }
    }

}
