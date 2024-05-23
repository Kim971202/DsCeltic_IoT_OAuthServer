package com.oauth.controller;

import com.oauth.dto.AuthServerDTO;
import com.oauth.dto.gw.DeviceStatusInfo;
import com.oauth.mapper.DeviceMapper;
import com.oauth.message.GwMessagingSystem;
import com.oauth.response.ApiResponse;
import com.oauth.service.impl.MobiusService;
import com.oauth.service.impl.PushService;
import com.oauth.utils.Common;
import com.oauth.utils.JSON;
import com.oauth.utils.RedisCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class MobiusController {

    @Autowired
    Common common;
    @Autowired
    RedisCommand redisCommand;
    @Autowired
    MobiusService mobiusService;
    @Autowired
    DeviceMapper deviceMapper;
    @Autowired
    GwMessagingSystem gwMessagingSystem;
    @Autowired
    PushService pushService;
    @PostMapping(value = "/GatewayToAppServer")
    @ResponseBody
    public String receiveCin(@RequestBody String jsonBody) throws Exception {

        log.info("GW Received JSON: " + jsonBody);
        DeviceStatusInfo.Device dr910WDevice = new DeviceStatusInfo.Device();
        ApiResponse.Data result = new ApiResponse.Data();

        /* *
         * 1. Redis에서 받은 uuId로 Redis에 저장된 Value값을 검색한다.
         * 2. 해당 Value는 userId,functionId 형태로 저장됨
         * 3. common에 함수를 사용하여 userId와 functionId를 추출
         * 4. 추출한 functionId를 기반으로 해당 functionId에 맞는 if문으로 분기
         * */
        String uuId = common.readCon(jsonBody, "uuId");

        String errorCode = common.readCon(jsonBody, "erCd");
        String replyErrorCode = common.readCon(jsonBody, "errorCode");
        String errorMessage = common.readCon(jsonBody, "erMg");
        String errorDateTime = common.readCon(jsonBody, "erDt");

        // serialNumber: [Mobius, 20202020303833413844433645333841, 083A8DC6E38A, 3957]
        String[] serialNumber = common.readCon(jsonBody, "sur").split("/");

        String mfStFunctionId = common.readCon(jsonBody, "functionId");
        String rtStFunctionId = common.readCon(jsonBody, "functionId");

        String userId;
        String functionId = "null";
        String redisValue = redisCommand.getValues(uuId);
        log.info("redisValue: " + redisValue);
        List<String> redisValueList;
        if (!redisValue.equals("false")) {
            redisValueList = common.getUserIdAndFunctionId(redisCommand.getValues(uuId));
            userId = redisValueList.get(0);
            functionId = redisValueList.get(1);
            log.info("userId: " + userId);
            log.info("functionId: " + functionId);
            log.info("errorCode: " + errorCode);
            log.info("replyErrorCode: " + replyErrorCode);
            gwMessagingSystem.sendMessage(functionId + uuId, errorCode);
        } else if (errorCode != null && errorDateTime != null) {
            AuthServerDTO errorInfo = new AuthServerDTO();
            errorInfo.setErrorCode(errorCode);
            errorInfo.setErrorMessage(errorMessage);
            errorInfo.setErrorDateTime(errorDateTime);
            errorInfo.setSerialNumber(serialNumber[2]);

            pushService.sendPushMessage(jsonBody);
            if(deviceMapper.insertErrorInfo(errorInfo) <= 0) {
                result.setResult(ApiResponse.ResponseType.HTTP_200, "DB_ERROR 잠시 후 다시 시도 해주십시오.");
                new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }
        } else if (mfStFunctionId.equals("mfSt")) {
            // 변경실시간상태
            pushService.sendPushMessage(jsonBody);

            Map<String, String> rsCfMap = new HashMap<>();

            // 변경내용 DB에 UPDATE
            List<DeviceStatusInfo.Device> device = deviceMapper.getDeviceStauts(Collections.singletonList(common.readCon(jsonBody, "srNo")));
            System.out.println(device);
            if(common.readCon(jsonBody, "mfCd").equals("powr")) device.get(0).setPowr(common.readCon(jsonBody, "powr"));
            if(common.readCon(jsonBody, "mfCd").equals("opMd")) device.get(0).setOpMd(common.readCon(jsonBody, "opMd"));
            if(common.readCon(jsonBody, "mfCd").equals("htTp")) device.get(0).setHtTp(common.readCon(jsonBody, "htTp"));
            if(common.readCon(jsonBody, "mfCd").equals("wtTp")) device.get(0).setWtTp(common.readCon(jsonBody, "wtTp"));
            if(common.readCon(jsonBody, "mfCd").equals("hwTp")) device.get(0).setHwTp(common.readCon(jsonBody, "hwTp"));
            if(common.readCon(jsonBody, "mfCd").equals("ftMd")) device.get(0).setFtMd(common.readCon(jsonBody, "ftMd"));
            if(common.readCon(jsonBody, "mfCd").equals("chTp")) device.get(0).setChTp(common.readCon(jsonBody, "chTp"));
            if(common.readCon(jsonBody, "mfCd").equals("mfDt")) device.get(0).setMfDt(common.readCon(jsonBody, "mfDt"));
            if(common.readCon(jsonBody, "mfCd").equals("slCd")) device.get(0).setSlCd(common.readCon(jsonBody, "slCd"));
            if(common.readCon(jsonBody, "mfCd").equals("hwSt")) device.get(0).setHwSt(common.readCon(jsonBody, "hwSt"));
            if(common.readCon(jsonBody, "mfCd").equals("fcLc")) device.get(0).setFcLc(common.readCon(jsonBody, "fcLc"));

            log.info("mfStFunctionId.equals(\"mfSt\"): " + jsonBody);

            if(common.readCon(jsonBody, "mfCd").equals("24h")){
                rsCfMap.put("24h", common.readCon(jsonBody, "24h"));
                rsCfMap.put("12h", common.readCon(device.get(0).getStringRsCf(), "12h_old"));
                rsCfMap.put("7wk", common.readCon(device.get(0).getStringRsCf(), "7wk_old"));
                device.get(0).setStringRsCf(JSON.toJson(rsCfMap));
            }

            if(common.readCon(jsonBody, "mfCd").equals("12h")){
                rsCfMap.put("12h", common.readCon(jsonBody, "12h"));
                rsCfMap.put("24h", common.readCon(device.get(0).getStringRsCf(), "24h_old"));
                rsCfMap.put("7wk", common.readCon(device.get(0).getStringRsCf(), "7wk_old"));
                device.get(0).setStringRsCf(JSON.toJson(rsCfMap));
            }

            if(common.readCon(jsonBody, "mfCd").equals("7wk")){
                rsCfMap.put("7wk", common.readCon(jsonBody, "7wk"));
                rsCfMap.put("24h", common.readCon(device.get(0).getStringRsCf(), "24h_old"));
                rsCfMap.put("12h", common.readCon(device.get(0).getStringRsCf(), "12h_old"));
                device.get(0).setStringRsCf(JSON.toJson(rsCfMap));
            }

            deviceMapper.updateDeviceStatus(device.get(0));
        } else if (rtStFunctionId.equals("rtSt")) {
            // 주기상태보고

            dr910WDevice.setDeviceId(common.readCon(jsonBody, "deviceId"));
            dr910WDevice.setRKey(common.readCon(jsonBody, "rKey"));
            dr910WDevice.setSerialNumber(common.readCon(jsonBody, "srNo"));
            dr910WDevice.setPowr(common.readCon(jsonBody, "powr"));
            dr910WDevice.setOpMd(common.readCon(jsonBody, "opMd"));
            dr910WDevice.setHtTp(common.readCon(jsonBody, "htTp"));
            dr910WDevice.setWtTp(common.readCon(jsonBody, "wtTp"));
            dr910WDevice.setHwTp(common.readCon(jsonBody, "hwTp"));
            dr910WDevice.setStringRsCf(common.convertToJsonString(common.readCon(jsonBody, "rsCf")));
            dr910WDevice.setFtMd(common.readCon(jsonBody, "ftMd"));
            dr910WDevice.setBCdt(common.readCon(jsonBody, "bCdt"));
            dr910WDevice.setChTp(common.readCon(jsonBody, "chTp"));
            dr910WDevice.setCwTp(common.readCon(jsonBody, "cwTp"));
            dr910WDevice.setHwSt(common.readCon(jsonBody, "hwSt"));
            dr910WDevice.setSlCd(common.readCon(jsonBody, "slCd"));
            dr910WDevice.setMfDt(common.readCon(jsonBody, "mfDt"));
            mobiusService.rtstHandler(dr910WDevice);
        } else {
            return "0x0106-Devices 상태 보고 요청";
        }
        return null;
    }
}