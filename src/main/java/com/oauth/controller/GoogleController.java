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

        ConcurrentHashMap<String, String> conMap = new ConcurrentHashMap<>();

        log.info("GOOGLE Received JSON: " + jsonBody);

        String[] serialNumber = common.readCon(jsonBody, "sur").split("/");
        String userId = common.readCon(jsonBody, "userId");
        String deviceId = common.readCon(jsonBody, "deviceId");

        String powerStatus = common.readCon(jsonBody, "value");
        if(!powerStatus.equals("of")) powerStatus = "on";

        System.out.println("common.stringToHex(\"    \" + serialNumber[2]): " + common.stringToHex("    " + serialNumber[2]));
        System.out.println("userId: " + userId);

        conMap.put("userId", userId);
        conMap.put("deviceId", deviceId);
        conMap.put("controlAuthKey", "0000");
        conMap.put("deviceType", "01");
        conMap.put("modelCode", serialNumber[1]);
        conMap.put("powerStatus", powerStatus);
        conMap.put("functionId", "powr");
        conMap.put("uuId", common.getTransactionId());

        String redisValue = userId + "," + "powr";
        redisCommand.setValues(conMap.get("uuId"), redisValue);

        System.out.println(JSON.toJson(conMap));
        mobiusService.createCin(common.stringToHex("    " + serialNumber[2]), userId, JSON.toJson(conMap));

        return "OK";
    }

}
