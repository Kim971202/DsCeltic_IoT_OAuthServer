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

        if(functionId.equals("powr")) {
            conMap.put("powerStatus", value);
        }

        if(functionId.equals("htTp")){
            conMap.put("temperature", value);
        }
        
	if(functionId.equals("wtTp")){

        if(deviceId.contains("204443522d39312f5746")){
            value = String.valueOf(Integer.parseInt(value));
        }
        conMap.put("temperature", value);

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

        mobiusService.createCin(deviceArray[6], userId, JSON.toJson(conMap));

        return "OK";
    }
}
