package com.oauth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oauth.dto.AuthServerDTO;
import com.oauth.dto.gw.DeviceStatusInfo;
import com.oauth.dto.gw.Set12;
import com.oauth.dto.gw.Set24;
import com.oauth.mapper.DeviceMapper;
import com.oauth.service.impl.MobiusService;
import com.oauth.utils.Common;
import com.oauth.utils.JSON;
import com.oauth.utils.RedisCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class GoogleController {

    @Autowired
    DeviceMapper deviceMapper;
    @Autowired
    MobiusService mobiusService;
    @Autowired
    Common common;
    @Autowired
    RedisCommand redisCommand;

    @PostMapping(value = "/GoogleToAppServer")
    @ResponseBody
    public String receiveCin(@RequestBody String jsonBody) throws Exception {

        HashMap<String, String> conMap = new HashMap<>();

        log.info("GOOGLE Received JSON: " + jsonBody);

        // 12시간 예약: 시간
        String workPeriod = "";

        // 12시간 예약: 분
        String workTime = "";

        // 24시간 타입
        String md = "";

        // 24시간 시간 List
        String hs = "";

        String value = common.readCon(jsonBody, "value");
        String userId = common.readCon(jsonBody, "userId");
        String functionId = common.readCon(jsonBody, "functionId");
        String deviceId = common.readCon(jsonBody, "deviceId");
        String[] deviceArray = deviceId.split("\\.");
       // [0, 2, 481, 1, 1, 2045534365636f313353, 20202020303833413844433645333841] - deviceArray

        log.info("userId:{}, functionId:{}, deviceId:{}, ", userId, functionId, deviceId);

        if(functionId.equals("powr")) {
            conMap.put("powerStatus", value);
        }

        /*
        * Sleep 코드 정의
        * 061 : 취침1 - 01 = Comfort
        * 062 : 취침2 - 02 = Normal
        * 063 : 취침3 - 03 = Warm
        * */
        if(functionId.equals("opMd")) {
            conMap.put("modeCode", value);

            // Sleep Code 지정 변수
            String slCd = "";
            if(value.equals("061")) slCd = "01";
            else if(value.equals("062")) slCd = "02";
            else if(value.equals("063")) slCd = "03";

            // Google Smarthome 서버에서 받은 061 코드를 GW서버에서 받을수 있게 06으로 Replace
            if(!slCd.isEmpty()) {
                conMap.replace("modeCode", "06");
                conMap.put("sleepCode", slCd);
            }

        }

        conMap.put("userId", userId);
        conMap.put("deviceId", deviceId);
        conMap.put("controlAuthKey", "0000");
        conMap.put("deviceType", "01");
        conMap.put("modelCode", deviceArray[5]);
        conMap.put("functionId", functionId);
        conMap.put("uuId", common.getTransactionId());


        System.out.println("JSON.toJson(conMap): " + JSON.toJson(conMap, true));

        String redisValue = userId + "," + "functionCode";
        // redisCommand.setValues(conMap.get("uuId"), redisValue);
        mobiusService.createCin(deviceArray[6], userId, JSON.toJson(conMap));


        // TODO: 예약의 경우 opMd 후 예약 SET 까지 처리 해야 RC 화면 변경 가능 (구형 기준)
        // 10 - 24시간
        // 11 - 반복예약
        // 12 - 주간예약
        DeviceStatusInfo.Device deviceInfo = deviceMapper.getSingleDeviceStauts(deviceId);
        // ObjectMapper 인스턴스 생성
        ObjectMapper objectMapper = new ObjectMapper();
        ConcurrentHashMap<String, Object> map = new ConcurrentHashMap<String, Object>();

        if(value.equals("11")){
            Set12 set12 = new Set12();

            try {
                JsonNode jsonNode = objectMapper.readTree(deviceInfo.getH12());
                workPeriod = jsonNode.get("hr").asText();
                workTime = jsonNode.get("mn").asText();
            } catch (Exception e){
                log.error("", e);
            }
            set12.setAccessToken(common.getTransactionId());
            set12.setUserId(userId);
            set12.setDeviceId(deviceId);
            set12.setControlAuthKey("0000");
            set12.setWorkPeriod(workPeriod);
            set12.setWorkTime(workTime);
            set12.setOnOffFlag("of");
            set12.setFunctionId("12h");
            set12.setUuId(common.getTransactionId());

            mobiusService.createCin(deviceArray[6], userId, JSON.toJson(set12));
        } else if(value.equals("10")){
            Set24 set24 = new Set24();

            try {
                JsonNode jsonNode = objectMapper.readTree(deviceInfo.getH24());
                md = jsonNode.get("md").asText();
                hs = jsonNode.get("md").asText();

            } catch (Exception e){
                log.error("", e);
            }
            set24.setAccessToken(common.getTransactionId());
            set24.setUserId(userId);
            set24.setDeviceId(deviceId);
            set24.setControlAuthKey("0000");
            set24.setHours(hs);
            set24.setType24h(md);
            set24.setOnOffFlag("on");
            set24.setFunctionId("24h");
            set24.setUuId(common.getTransactionId());

            mobiusService.createCin(deviceArray[6], userId, JSON.toJson(set24));
        }

        return "OK";
    }
}
