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
        ApiResponse.Data result = new ApiResponse.Data();
        deviceMapper.insertJson(jsonBody);
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

        String userId;
        String functionId = common.readCon(jsonBody, "functionId");
        String redisValue = redisCommand.getValues(uuId);

        log.info("functionId: " + functionId);
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
        } else if (functionId.equals("mfSt")) {
            // 변경실시간상태
            pushService.sendPushMessage(jsonBody);

            DeviceStatusInfo.Device deviceInfo = new DeviceStatusInfo.Device();
            deviceInfo.setMfcd(common.readCon(jsonBody, "mfcd"));
            deviceInfo.setPowr(common.readCon(jsonBody, "powr"));
            deviceInfo.setOpMd(common.readCon(jsonBody, "opMd"));
            deviceInfo.setHtTp(common.readCon(jsonBody, "htTp"));
            deviceInfo.setWtTp(common.readCon(jsonBody, "wtTp"));
            deviceInfo.setHwTp(common.readCon(jsonBody, "hwTp"));
            deviceInfo.setFtMd(common.readCon(jsonBody, "ftMd"));
            deviceInfo.setBCdt(common.readCon(jsonBody, "bCdt"));
            deviceInfo.setChTp(common.readCon(jsonBody, "chTp"));
            deviceInfo.setCwTp(common.readCon(jsonBody, "cwTp"));
            deviceInfo.setHwSt(common.readCon(jsonBody, "hwSt"));
            deviceInfo.setWk7(common.readCon(jsonBody, "7wk"));
            deviceInfo.setH12(common.readCon(jsonBody, "12h"));
            deviceInfo.setH24(common.readCon(jsonBody, "24h"));
            deviceInfo.setFwh(common.readCon(jsonBody, "fwh"));
            deviceInfo.setDeviceId(common.readCon(jsonBody, "deviceId"));
            int rcUpdateResult = deviceMapper.updateDeviceStatusFromApplication(deviceInfo);
            log.info("rcUpdateResult: " + rcUpdateResult);

        } else if (functionId.equals("rtSt")) {
            // 주기상태보고

            DeviceStatusInfo.Device dr910WDevice = new DeviceStatusInfo.Device();

            // DeviceId로 ModelCode 확인
            String deviceId = common.readCon(jsonBody, "deviceId");

            System.out.println("deviceId: " + deviceId);

            String[] modelCode = deviceId.split("\\.");

            System.out.println("modelCode: " + Arrays.toString(modelCode));

            // 구형 보일러 RC
            if(common.hexToString(modelCode[5]).equals(" ESCeco13S")){

                System.out.println("THIS IS ESCeco13S");

            }else if(common.hexToString(modelCode[5]).equals(" DCR-91/WT")){
            // 신형 보일러 RC
                System.out.println("THIS IS DCR-91/WT");

            }

            dr910WDevice.setH24(common.convertToJsonString(common.readCon(jsonBody, "24h")));
            dr910WDevice.setH12(common.convertToJsonString(common.readCon(jsonBody, "12h")));
            dr910WDevice.setWk7(common.convertToJsonString(common.readCon(jsonBody, "7wk")));
//            dr910WDevice.setFwh(common.convertToJsonString(common.readCon(jsonBody, "fwh")));
            dr910WDevice.setFwh("null");
            dr910WDevice.setDeviceId(common.readCon(jsonBody, "deviceId"));
            dr910WDevice.setRKey(common.readCon(jsonBody, "rKey"));
            dr910WDevice.setSerialNumber(common.readCon(jsonBody, "srNo"));
            dr910WDevice.setPowr(common.readCon(jsonBody, "powr"));
            dr910WDevice.setOpMd(common.readCon(jsonBody, "opMd"));
            dr910WDevice.setHtTp(common.readCon(jsonBody, "htTp"));
            dr910WDevice.setWtTp(common.readCon(jsonBody, "wtTp"));
            dr910WDevice.setHwTp(common.readCon(jsonBody, "hwTp"));
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