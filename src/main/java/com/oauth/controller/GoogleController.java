package com.oauth.controller;

import com.oauth.mapper.DeviceMapper;
import com.oauth.service.impl.MobiusService;
import com.oauth.utils.Common;
import com.oauth.utils.JSON;
import com.oauth.utils.RedisCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
            
            if(!slCd.isEmpty()) conMap.put("sleepCode", slCd);
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
        redisCommand.setValues(conMap.get("uuId"), redisValue);
        mobiusService.createCin(deviceArray[6], userId, JSON.toJson(conMap));

        return "OK";
    }

}
