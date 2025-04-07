package com.oauth.controller;

import com.oauth.dto.gw.PowerOnOff;
import com.oauth.mapper.DeviceMapper;
import com.oauth.service.impl.MobiusService;
import com.oauth.utils.Common;
import com.oauth.utils.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class NaverController {

    @Autowired
    DeviceMapper deviceMapper;
    @Autowired
    MobiusService mobiusService;
    @Autowired
    Common common;

    @PostMapping(value = "/NaverToAppServer")
    @ResponseBody
    public void receiveCin(@RequestBody String jsonBody) throws Exception{
        log.info("Naver Received JSON: " + jsonBody);

        HashMap<String, String> conMap = new HashMap<>();
        String functionId = common.readCon(jsonBody, "functionId");
        String value = common.readCon(jsonBody, "value");
        String deviceId = common.readCon(jsonBody, "deviceId");
        String userId = common.readCon(jsonBody, "userId");

        switch(functionId){
            case "TurnOn":
            case "TurnOff":
                functionId = "powr";
                conMap.put("powerStatus", value);
                break;
            case "IncrementTargetTemperature":
            case "DecrementTargetTemperature":
            case "SetTargetTemperature":
                functionId = "htTp";
                conMap.put("temperature", value);
                break;
            case "SetMode":
                functionId = "opMd";
                switch (value) {
                    case "away":
                        value = "03"; // 외출 모드 코드
                        break;
                    case "hotwater":
                        value = "07"; // 온수 모드 코드
                        break;
                    case "indoor":
                        value = "01"; // 실내 모드 코드
                        break;
                    case "sleep":
                        value = "06"; // 취침 모드 코드
                        conMap.put("sleepCode", "01");
                        break;
                    default:
                        value = "99"; // 알 수 없는 모드
                        break;
                }
                conMap.put("modeCode", value);
                break;
            default:
                break;
        }

        conMap.put("deviceId", deviceId);
        conMap.put("controlAuthKey", "0000");
        conMap.put("deviceType", "01");
        conMap.put("modelCode", common.getModelCodeFromDeviceId(deviceId).trim());
        conMap.put("functionId", functionId);
        conMap.put("uuId", common.getTransactionId());

        mobiusService.createCin(common.getHexSerialNumberFromDeviceId(deviceId), userId, JSON.toJson(conMap));
    }

}
