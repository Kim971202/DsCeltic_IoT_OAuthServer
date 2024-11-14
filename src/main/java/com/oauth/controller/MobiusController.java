package com.oauth.controller;

import com.oauth.dto.AuthServerDTO;
import com.oauth.dto.gw.DeviceStatusInfo;
import com.oauth.mapper.DeviceMapper;
import com.oauth.mapper.MemberMapper;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.function.Consumer;

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
    MemberMapper memberMapper;
    @Autowired
    GwMessagingSystem gwMessagingSystem;
    @Autowired
    PushService pushService;
    @Value("#{${device.model.code}}")
    Map<String, String> modelCodeMap;

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
        String redisValue = "false";

        if(functionId == null) return "FUNCTION ID NULL";

        if(!functionId.equals("rtSt") && !functionId.equals("mfSt") && !functionId.equals("opIf")) redisValue = redisCommand.getValues(uuId);
        log.info("uuId: " + uuId);

        log.info("functionId: " + functionId);
        log.info("redisValue: " + redisValue);
        if(redisValue == null) {
            log.info("NULL RECEIVED");
            return "NULL RECEIVED";
        }

        // DeviceId로 ModelCode 확인
        String deviceId = common.readCon(jsonBody, "deviceId");

        System.out.println("deviceId: " + deviceId);

        String[] modelCode = deviceId.split("\\.");

        System.out.println("modelCode: " + Arrays.toString(modelCode));
        System.out.println("modelCode[5]: " + modelCode[5]);
        System.out.println("common.hexToString(modelCode[5]): " + common.hexToString(modelCode[5]));

        List<String> redisValueList;

        if(replyErrorCode != null) {
            if(replyErrorCode.equals("2")){
                gwMessagingSystem.sendMessage(functionId + uuId, replyErrorCode);
                return "";
            }
        }

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
            pushService.sendPushMessage(jsonBody, errorCode, errorMessage, common.hexToString(modelCode[5]));
            if(deviceMapper.insertErrorInfo(errorInfo) <= 0) {
                result.setResult(ApiResponse.ResponseType.HTTP_200, "DB_ERROR 잠시 후 다시 시도 해주십시오.");
                new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }
        } else if (functionId.equals("mfSt")) {

            DeviceStatusInfo.Device deviceInfo = new DeviceStatusInfo.Device();
            deviceInfo.setDeviceId(common.readCon(jsonBody, "deviceId"));

            deviceInfo.setFtMd(common.readCon(jsonBody, "ftMd"));
            deviceInfo.setFcLc(common.readCon(jsonBody, "fcLc"));

            deviceInfo.setPast(common.readCon(jsonBody, "past"));
            deviceInfo.setInDr(common.readCon(jsonBody, "inDr"));
            deviceInfo.setInCl(common.readCon(jsonBody, "inCl"));
            deviceInfo.setEcSt(common.readCon(jsonBody, "ecSt"));

            if(common.readCon(jsonBody, "mfCd").equals("acTv")){
                deviceInfo.setSerialNumber(common.readCon(jsonBody, "srNo"));
                mobiusService.actvHandler(deviceInfo);
                return "OK";
            }

            deviceInfo.setMfcd(common.readCon(jsonBody, "mfcd"));
            deviceInfo.setPowr(common.readCon(jsonBody, "powr"));
            deviceInfo.setOpMd(common.readCon(jsonBody, "opMd"));
            deviceInfo.setSlCd(common.readCon(jsonBody, "slCd"));
            deviceInfo.setHtTp(common.readCon(jsonBody, "htTp"));
            deviceInfo.setWtTp(common.readCon(jsonBody, "wtTp"));
            deviceInfo.setHwTp(common.readCon(jsonBody, "hwTp"));
            deviceInfo.setBCdt(common.readCon(jsonBody, "bCdt"));
            deviceInfo.setChTp(common.readCon(jsonBody, "chTp"));
            deviceInfo.setCwTp(common.readCon(jsonBody, "cwTp"));
            deviceInfo.setHwSt(common.readCon(jsonBody, "hwSt"));
            deviceInfo.setEcOp(common.readCon(jsonBody, "ecOp"));
            deviceInfo.setBlCf(common.readCon(jsonBody, "blCf"));
            deviceInfo.setVtSp(common.readCon(jsonBody, "vtSp"));

//            if(deviceInfo.getBCdt() != null) deviceMapper

            if(common.readCon(jsonBody, "rsPw") != null) deviceInfo.setRsPw(common.convertToJsonFormat(common.readCon(jsonBody, "rsPw")));

            if(common.readCon(jsonBody, "7wk") != null) deviceInfo.setWk7(common.convertToJsonFormat(common.readCon(jsonBody, "7wk")));

            if(common.readCon(jsonBody, "12h") != null) deviceInfo.setH12(common.convertToJsonFormat(common.readCon(jsonBody, "12h")));

            if(common.readCon(jsonBody, "24h") != null) deviceInfo.setH24(common.convertToJsonFormat(common.readCon(jsonBody, "24h")));

            deviceInfo.setFwh(common.readCon(jsonBody, "fwh"));

            int rcUpdateResult = deviceMapper.updateDeviceStatusFromApplication(deviceInfo);

            // DeviceId로 해당 기기의 userId를 찾아서 PushMessage 전송
            List<AuthServerDTO> userIds = memberMapper.getDeviceIdByUserIds(common.readCon(jsonBody, "deviceId"));

            AuthServerDTO info = new AuthServerDTO();

            for (AuthServerDTO id : userIds) {
                log.info("쿼리한 UserId: " + id.getUserId());

                info.setUserId(id.getUserId());
                info.setDeviceId(common.readCon(jsonBody, "deviceId"));

                String fPushYn = memberMapper.getPushYnStatusByDeviceIdAndUserId(info).getFPushYn();
                String pushToken = memberMapper.getPushTokenByUserId(id.getUserId()).getPushToken();

                pushService.sendPushMessage(jsonBody, pushToken, fPushYn, id.getUserId(), common.hexToString(modelCode[5]));
            }

            AuthServerDTO params = new AuthServerDTO();

            Map<String, Object> nonNullFields = common.getNonNullFields(deviceInfo);
            System.out.println("Non-null fields: " + nonNullFields);

            common.setCommandParams(nonNullFields, params);

            // 결과 출력
            System.out.println("CommandId: " + params.getCommandId());
            System.out.println("ControlCode: " + params.getControlCode());
            System.out.println("ControlCodeName: " + params.getControlCodeName());

            params.setCommandId(params.getCommandId());
            params.setControlCode(params.getControlCode());
            params.setControlCodeName(params.getControlCodeName());

            params.setCodeType("1");
            params.setCommandFlow("1");
            params.setDeviceId(deviceInfo.getDeviceId());
            params.setUserId("RC");

            int insertCommandHistoryResult = memberMapper.insertCommandHistory(params);
            log.info("insertCommandHistoryResult: " + insertCommandHistoryResult);

            params.setPushTitle("기기 제어");
            params.setPushContent(params.getControlCodeName());
            params.setDeviceId(deviceId);
            params.setDeviceType(common.getModelCode(common.getModelCodeFromDeviceId(deviceId).replace(" ", "")));
            if(memberMapper.insertPushHistory(params) <= 0) log.info("PUSH HISTORY INSERT ERROR");

        } else if (functionId.equals("rtSt")) {

            // 주기상태보고
            DeviceStatusInfo.Device dr910WDevice = new DeviceStatusInfo.Device();

            // 공통 필드 설정
            dr910WDevice.setDeviceId(common.readCon(jsonBody, "deviceId"));
            dr910WDevice.setRKey(common.readCon(jsonBody, "rKey"));
            dr910WDevice.setSerialNumber(common.readCon(jsonBody, "srNo"));
            dr910WDevice.setPowr(common.readCon(jsonBody, "powr")); // 전원 ON/OF
            dr910WDevice.setOpMd(common.readCon(jsonBody, "opMd")); // 홈 IoT 모드
            dr910WDevice.setMfDt(common.readCon(jsonBody, "mfDt")); // 변경 시간

            // 모델별 필드 설정
            String modelType = common.hexToString(modelCode[5]);

            if (modelType.equals(modelCodeMap.get("newModel"))) {
                dr910WDevice.setH12(common.convertToJsonString(common.readCon(jsonBody, "12h")));
                dr910WDevice.setWk7(common.convertToJsonString(common.readCon(jsonBody, "7wk")));
                dr910WDevice.setHtTp(common.readCon(jsonBody, "htTp"));
                dr910WDevice.setWtTp(common.readCon(jsonBody, "wtTp"));
                dr910WDevice.setHwTp(common.readCon(jsonBody, "hwTp"));
                dr910WDevice.setFtMd(common.readCon(jsonBody, "ftMd"));
                dr910WDevice.setBCdt(common.readCon(jsonBody, "bCdt"));
                dr910WDevice.setChTp(common.readCon(jsonBody, "chTp"));
                dr910WDevice.setCwTp(common.readCon(jsonBody, "cwTp"));
                dr910WDevice.setHwSt(common.readCon(jsonBody, "hwSt"));
                dr910WDevice.setFcLc(common.readCon(jsonBody, "fcLc"));
                dr910WDevice.setBlCf(common.readCon(jsonBody, "blCf"));

            } else if (modelType.equals(modelCodeMap.get("oldModel"))) {
                dr910WDevice.setH24(common.convertToJsonString(common.readCon(jsonBody, "24h")));
                dr910WDevice.setH12(common.convertToJsonString(common.readCon(jsonBody, "12h")));
                dr910WDevice.setWk7(common.convertToJsonString(common.readCon(jsonBody, "7wk")));
                dr910WDevice.setHtTp(common.readCon(jsonBody, "htTp"));
                dr910WDevice.setWtTp(common.readCon(jsonBody, "wtTp"));
                dr910WDevice.setHwTp(common.readCon(jsonBody, "hwTp"));
                dr910WDevice.setFtMd(common.readCon(jsonBody, "ftMd"));
                dr910WDevice.setBCdt(common.readCon(jsonBody, "bCdt"));
                dr910WDevice.setChTp(common.readCon(jsonBody, "chTp"));
                dr910WDevice.setCwTp(common.readCon(jsonBody, "cwTp"));
                dr910WDevice.setHwSt(common.readCon(jsonBody, "hwSt"));
                dr910WDevice.setSlCd(common.readCon(jsonBody, "slCd"));
                dr910WDevice.setBlCf(common.readCon(jsonBody, "blCf"));

            } else if (modelType.equals(modelCodeMap.get("ventilation"))) {
                dr910WDevice.setRsSl(common.convertToJsonString(common.readCon(jsonBody, "rsSl")));
                dr910WDevice.setRsPw(common.convertToJsonString(common.readCon(jsonBody, "rsPw")));
                dr910WDevice.setVtSp(common.readCon(jsonBody, "vtSp"));
                dr910WDevice.setInAq(common.readCon(jsonBody, "inAq"));
            }

            mobiusService.rtstHandler(dr910WDevice);
        } else if(functionId.equals("opIf")){
            List<AuthServerDTO> opTmInfo;
            opTmInfo = memberMapper.getUserIdFromDeviceGroup(deviceId);

            List<AuthServerDTO> inputList = new ArrayList<>();
            for(AuthServerDTO authServerDTO : opTmInfo){
                AuthServerDTO newWkTmInfo = new AuthServerDTO();
                newWkTmInfo.setWorkTime(common.readCon(jsonBody, "wkTm"));
                newWkTmInfo.setMsDt(common.readCon(jsonBody, "msDt"));
                newWkTmInfo.setDeviceId(deviceId);
                newWkTmInfo.setUserId(authServerDTO.getUserId());
                inputList.add(newWkTmInfo);
            }
            memberMapper.insertWorkTime(inputList);
        }else {
            return "0x0106-Devices 상태 보고 요청";
        }
        return null;
    }
}