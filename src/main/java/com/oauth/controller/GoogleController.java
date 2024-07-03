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

        String functionCode = null;

        String value = common.readCon(jsonBody, "value");
        String userId = common.readCon(jsonBody, "userId");
        String functionId = common.readCon(jsonBody, "functionId");
        String deviceId = common.readCon(jsonBody, "deviceId");
        String[] deviceArray = deviceId.split("\\.");
       // [0, 2, 481, 1, 1, 2045534365636f313353, 20202020303833413844433645333841] - deviceArray

        log.info("userId:{}, functionId:{}, deviceId:{}, ", userId, functionId, deviceId);

        conMap.put("userId", userId);
        conMap.put("deviceId", deviceId);
        conMap.put("controlAuthKey", "0000");
        conMap.put("deviceType", "01");
        conMap.put("modelCode", deviceArray[5]);
        conMap.put("functionId", functionId);
        conMap.put("uuId", common.getTransactionId());

        if(functionId.equals("powr")) functionCode = "powerStatus";
        if(functionId.equals("mode")) functionCode = "opMd";

        conMap.put(functionCode, value);

        System.out.println("JSON.toJson(conMap): " + JSON.toJson(conMap));

        String redisValue = userId + "," + "functionCode";
        redisCommand.setValues(conMap.get("uuId"), redisValue);
        mobiusService.createCin(deviceArray[6], userId, JSON.toJson(conMap));

        return "OK";
    }

}
