package com.oauth.controller;

import com.oauth.dto.AuthServerDTO;
import com.oauth.dto.gw.DeviceStatusInfo;
import com.oauth.mapper.DeviceMapper;
import com.oauth.mapper.MemberMapper;
import com.oauth.message.GwMessagingSystem;
import com.oauth.response.ApiResponse;
import com.oauth.service.impl.InfluxService;
import com.oauth.service.impl.MobiusService;
import com.oauth.service.impl.PushService;
import com.oauth.utils.Common;
import com.oauth.utils.RedisCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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
    MemberMapper memberMapper;
    @Autowired
    GwMessagingSystem gwMessagingSystem;
    @Autowired
    PushService pushService;
    @Autowired
    InfluxService influxService;
    @Value("#{${device.model.code}}")
    Map<String, String> modelCodeMap;

    @PostMapping(value = "/GatewayToAppServer")
    @ResponseBody
    public String receiveCin(@RequestBody String jsonBody) throws Exception {

        log.info("GW Received JSON: {}", jsonBody);
        ApiResponse.Data result = new ApiResponse.Data();

        /*
         * *
         * 1. Redis에서 받은 uuId로 Redis에 저장된 Value값을 검색한다.
         * 2. 해당 Value는 userId,functionId 형태로 저장됨
         * 3. common에 함수를 사용하여 userId와 functionId를 추출
         * 4. 추출한 functionId를 기반으로 해당 functionId에 맞는 if문으로 분기
         */

        // prId 값이 존재할 경우 각방이므로 값을 저장 한다
        String hexSerial = common.getHexSerialNumberFromDeviceId(common.readCon(jsonBody, "deviceId"));
        if (common.readCon(jsonBody, "prId") != null) {

            DeviceStatusInfo.Device deviceInfo = new DeviceStatusInfo.Device();

            deviceInfo.setSerialNumber(common.readCon(jsonBody, "srNo"));
            deviceInfo.setModelCode(common.readCon(jsonBody, "biMd"));
            deviceInfo.setRKey(common.readCon(jsonBody, "rKey"));
            deviceInfo.setPsYn(common.readCon(jsonBody, "psYn"));
            deviceInfo.setPrId(common.readCon(jsonBody, "prId"));
            deviceInfo.setDvNm(common.readCon(jsonBody, "dvNm"));
            deviceInfo.setDeviceId(common.readCon(jsonBody, "deviceId"));

            // 이전 값이 남아 있을수 있으므로 삭제
            if (deviceInfo.getDvNm().equals("room0")) {
                deviceMapper.deleteExistPrId(common.getHexSerialNumberFromDeviceId(deviceInfo.getPrId()));
            }

            if (deviceInfo.getDvNm().equals("room0")) {
                deviceInfo.setDvNm("거실");
            } else {
                String roomId = deviceInfo.getDvNm();
                deviceInfo.setDvNm("방" + roomId.substring(roomId.length() - 1));
            }

            if (deviceMapper.getEachRoomStautsByDeviceId(hexSerial) == null) {
                // 신규 기기 INSERT
                deviceMapper.insertEachRoomStatus(deviceInfo);
            } else {
                // 기존 기기 UPDATE
                deviceInfo.setTargetDeviceId(hexSerial);
                deviceMapper.updateEachRoomStatus(deviceInfo);
            }
            return "DONE";
        }

        String uuId = common.readCon(jsonBody, "uuId");
        String errorCode = common.readCon(jsonBody, "erCd");
        String replyErrorCode = common.readCon(jsonBody, "errorCode");
        String errorMessage = common.readCon(jsonBody, "erMg");
        String errorVersion = common.readCon(jsonBody, "erVr");
        String errorDateTime = common.readCon(jsonBody, "erDt");

        // serialNumber: [Mobius, 20202020303833413844433645333841, 083A8DC6E38A, 3957]
        String[] serialNumber = common.readCon(jsonBody, "sur").split("/");

        String userId;
        String functionId = common.readCon(jsonBody, "functionId");
        String redisValue = "false";

        if (functionId == null || functionId.equals("mfAr"))
            return "FUNCTION NO CHECK";

        if (!functionId.equals("rtSt") && !functionId.equals("mfSt") && !functionId.equals("opIf") && !functionId.equals("fcNt")) {
            redisValue = redisCommand.getValues(uuId);
        }

        log.info("functionId: {}, redisValue: {} ", functionId, redisValue);

        if (redisValue == null) {
            log.info("NULL RECEIVED");
            return "NULL RECEIVED";
        }

        // DeviceId로 ModelCode 확인
        String deviceId = common.readCon(jsonBody, "deviceId");
        String subDeviceId = common.readCon(jsonBody, "deviceId");

        String[] modelCode = deviceId.split("\\.");

        List<String> redisValueList;

        if (replyErrorCode != null) {
            if (replyErrorCode.equals("2")) {
                gwMessagingSystem.sendMessage(functionId + uuId, replyErrorCode);
                return "";
            }
        }

        if (!redisValue.equals("false")) {
            redisValueList = common.getUserIdAndFunctionId(redisCommand.getValues(uuId));
            userId = redisValueList.get(0);
            functionId = redisValueList.get(1);
            log.info("userId: {}, functionId:{}, errorCode: {}, replyErrorCode: {}", userId, functionId, errorCode, replyErrorCode);
            gwMessagingSystem.sendMessage(functionId + uuId, errorCode);

        } else if (errorCode != null && errorDateTime != null) {
            AuthServerDTO errorInfo = new AuthServerDTO();
            errorInfo.setErrorCode(errorCode);
            errorInfo.setErrorMessage(errorMessage);
            errorInfo.setErrorVersion(errorVersion);
            errorInfo.setErrorDateTime(errorDateTime);
            errorInfo.setSerialNumber(serialNumber[2]);
            if (!errorCode.equals("00"))
                pushService.sendPushMessage(jsonBody, errorCode, errorMessage, common.hexToString(modelCode[5]),
                        errorVersion);
            if (deviceMapper.insertErrorInfo(errorInfo) <= 0) {
                result.setResult(ApiResponse.ResponseType.HTTP_200, "DB_ERROR 잠시 후 다시 시도 해주십시오.");
                new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }
        } else if (functionId.equals("vfLs")) {
            DeviceStatusInfo.Device deviceInfo = new DeviceStatusInfo.Device();
            deviceInfo.setDeviceId(deviceId);
            deviceInfo.setVfLs(common.readCon(jsonBody, "vfLs"));

            // DeviceId로 해당 기기의 userId를 찾아서 PushMessage 전송
            List<AuthServerDTO> userIds = memberMapper.getAllUserIdsByDeviceId(deviceId);

            AuthServerDTO info = deviceMapper.getDeviceNicknameByDeviceId(deviceId);
            for (AuthServerDTO id : userIds) {
                if (memberMapper.getUserLoginoutStatus(id.getUserId()).getLoginoutStatus().equals("Y")) {
                    info.setUserId(id.getUserId());
                    info.setDeviceId(deviceId);
                    info.setDeviceId(deviceId);
                    String fPushYn = memberMapper.getPushYnStatusByDeviceIdAndUserId(info).getFPushYn();
                    String pushToken = memberMapper.getPushTokenByUserId(id.getUserId()).getPushToken();
                    pushService.sendPushMessage(common.readCon(jsonBody, "con"), pushToken, fPushYn, id.getUserId(),
                            common.hexToString(modelCode[5]), common.readCon(jsonBody, "mfCd"),
                            info.getDeviceNickname());
                }
            }
            AuthServerDTO params = new AuthServerDTO();

            Map<String, Object> nonNullFields = common.getNonNullFields(deviceInfo);

            common.setCommandParams(nonNullFields, params);

            // 결과 출력
            log.info("CommandId: {}, ControlCode: {}, ControlCodeName: {} ",params.getCommandId(), params.getControlCode(), params.getControlCodeName());

            params.setCommandId(params.getCommandId());
            params.setControlCode(params.getControlCode());
            params.setControlCodeName(params.getControlCodeName());

            params.setCodeType("1");
            params.setCommandFlow("1");
            params.setDeviceId(deviceInfo.getDeviceId());
            params.setUserId("RC");

            memberMapper.insertCommandHistory(params);

            influxService.writeMeasurement(
                    "VentilationFanLifeStatus",
                    "RC",
                    deviceInfo.getVfLs(),
                    "환기 팬 잔여 수명",
                    "USER_ID",
                    deviceInfo.getDeviceId(),
                    "1",
                    "1"
            );

            params.setPushTitle("기기 제어");
            params.setPushContent(params.getControlCodeName());
            params.setDeviceId(deviceId);
            params.setDeviceType(common.getModelCode(common.getModelCodeFromDeviceId(deviceId).replace(" ", "")));
            memberMapper.insertPushHistory(params);

        } else if (functionId.equals("mfSt")) {

            DeviceStatusInfo.Device deviceInfo = new DeviceStatusInfo.Device();
            deviceInfo.setDeviceId(deviceId);

            deviceInfo.setFtMd(common.readCon(jsonBody, "ftMd"));
            deviceInfo.setFcLc(common.readCon(jsonBody, "fcLc"));
            deviceInfo.setEcOp(common.readCon(jsonBody, "ecOp"));

            deviceInfo.setPast(common.readCon(jsonBody, "past"));
            deviceInfo.setInDr(common.readCon(jsonBody, "inDr"));
            deviceInfo.setInCl(common.readCon(jsonBody, "inCl"));
            deviceInfo.setEcSt(common.readCon(jsonBody, "ecSt"));

            if (common.readCon(jsonBody, "mfCd").equals("acTv")) {
                deviceInfo.setFtMdActv(common.readCon(jsonBody, "ftMd"));
                deviceInfo.setFcLcActv(common.readCon(jsonBody, "fcLc"));
                deviceInfo.setEcOpActv(common.readCon(jsonBody, "ecOp"));
                deviceInfo.setSerialNumber(common.readCon(jsonBody, "srNo"));
                deviceInfo.setFcLc(null);
                deviceInfo.setFtMd(null);
                deviceInfo.setEcOp(null);
                mobiusService.actvHandler(deviceInfo);
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
            deviceInfo.setBlCf(common.readCon(jsonBody, "blCf"));
            deviceInfo.setVtSp(common.readCon(jsonBody, "vtSp"));

            if (deviceInfo.getHwSt() != null)
                memberMapper.updateSafeAlarmSet(deviceInfo);
            if (deviceInfo.getBCdt() != null)
                memberMapper.updateSafeAlarmSetByBcDt(deviceInfo);

            if (common.readCon(jsonBody, "rsPw") != null)
                deviceInfo.setRsPw(common.convertToJsonFormat(common.readCon(jsonBody, "rsPw")));

            if (common.readCon(jsonBody, "7wk") != null) {
                if (common.convertToJsonFormat(common.readCon(jsonBody, "7wk")).equals("[{wk:,\"hs\":[]}]"))
                    deviceInfo.setWk7("[{\"wk\":\"\",\"hs\":[]}]");
                else
                    deviceInfo.setWk7(common.convertToJsonFormat(common.readCon(jsonBody, "7wk")));
            }

            if (common.readCon(jsonBody, "12h") != null)
                deviceInfo.setH12(common.convertToJsonFormat(common.readCon(jsonBody, "12h")));

            if (common.readCon(jsonBody, "24h") != null)
                deviceInfo.setH24(common.convertToJsonFormat(common.readCon(jsonBody, "24h")));

            deviceInfo.setFwh(common.readCon(jsonBody, "fwh"));

            int rcUpdateResult;

            // 각방의 경우 저장하는 테이블 변경
            String subDeviceNickname = "";
            if (common.checkDeviceType(deviceId)) {
                if (common.readCon(jsonBody, "mfCd").equals("hwTp")) {
                    rcUpdateResult = deviceMapper.updateEachRoomControlStatusHwTp(deviceInfo);
                } else {
                    // SUB_ID를 ParentId로 수정 해야 함.
                    deviceId = deviceMapper.getParentIdBySubId(deviceId).getParentDevice();
                    rcUpdateResult = deviceMapper.updateEachRoomControlStatus(deviceInfo);
                    deviceInfo.setDeviceId(deviceId);
                    subDeviceNickname = deviceMapper.getDeviceNickNameBySubId(subDeviceId).getDeviceNickName();
                }
            } else {
                rcUpdateResult = deviceMapper.updateDeviceStatusFromApplication(deviceInfo);
            }

            log.info("rcUpdateResult: {}", rcUpdateResult);

            AuthServerDTO info = deviceMapper.getDeviceNicknameByDeviceId(deviceId);
            if (!subDeviceNickname.isEmpty()) {
                info.setDeviceNickname(subDeviceNickname);
            }

            // DeviceId로 해당 기기의 userId를 찾아서 PushMessage 전송
            List<AuthServerDTO> userIds = memberMapper.getAllUserIdsByDeviceId(deviceId);

            for (AuthServerDTO id : userIds) {
                if (memberMapper.getUserLoginoutStatus(id.getUserId()).getLoginoutStatus().equals("Y")) {
                    info.setUserId(id.getUserId());
                    info.setDeviceId(deviceId);

                    String fPushYn = memberMapper.getPushYnStatusByDeviceIdAndUserId(info).getFPushYn();
                    String pushToken = memberMapper.getPushTokenByUserId(id.getUserId()).getPushToken();

                    pushService.sendPushMessage(common.readCon(jsonBody, "con"), pushToken, fPushYn, id.getUserId(),
                            common.hexToString(modelCode[5]), common.readCon(jsonBody, "mfCd"),
                            info.getDeviceNickname());
                }
            }

            common.updateStatusGoogle(deviceInfo, deviceId);

            AuthServerDTO params = new AuthServerDTO();
            params.setUserId("RC");

            if (!common.readCon(jsonBody, "mfCd").equals("acTv")) {
                Map<String, Object> nonNullFields = common.getNonNullFields(deviceInfo);
                log.info("Non-null fields: {}", nonNullFields);

                common.setCommandParams(nonNullFields, params);

                // 결과 출력
                log.info("CommandId: {}, ControlCode: {}, ControlCodeName: {}", params.getCommandId(), params.getControlCode(), params.getControlCodeName());

                params.setCommandId(params.getCommandId());
                params.setControlCode(params.getControlCode());
                params.setControlCodeName(params.getControlCodeName());

                params.setCodeType("1");
                params.setCommandFlow("1");
                params.setDeviceId(deviceInfo.getDeviceId());

                int insertCommandHistoryResult = memberMapper.insertCommandHistory(params);
                log.info("insertCommandHistoryResult: {}", insertCommandHistoryResult);
            }

            params.setPushTitle("기기 제어");
            params.setPushContent(params.getControlCodeName());
            params.setDeviceId(deviceId);
            params.setDeviceType(common.getModelCode(common.getModelCodeFromDeviceId(deviceId).replace(" ", "")));
            memberMapper.insertPushHistory(params);
            influxService.writeMeasurement(
                    params.getCommandId(),
                    params.getControlCode(),
                    common.readCon(jsonBody, "con"),
                    params.getControlCodeName(),
                    "RC",
                    deviceId,
                    "1",
                    "1"
            );

        } else if (functionId.equals("rtSt")) {

            // 주기상태보고
            DeviceStatusInfo.Device dr910WDevice = new DeviceStatusInfo.Device();

            // 공통 필드 설정
            dr910WDevice.setDeviceId(deviceId);
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

                // TBR_OPR_EACH_ROOM_MODE_INFO 테이블에 해당 일에 해당 기기가 있는지 확인 (없으면 INSERT 후 로직 진행)
                dr910WDevice.setPrId(deviceId);
                dr910WDevice.setRegDatetime(common.getCurrentDateTimeForEachRoomMode());
                if(deviceMapper.checkEachRoomModeInfo(dr910WDevice).getDeviceCount().equals("0")){
                    deviceMapper.insertEachRoomStatInfo(dr910WDevice);
                }

                dr910WDevice.setColumn(common.getColumnForCurrentHour());
                deviceMapper.updateEachRoomStatInfo(dr910WDevice);

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

                dr910WDevice.setPrId(deviceId);
                dr910WDevice.setRegDatetime(common.getCurrentDateTimeForEachRoomMode());
                if(deviceMapper.checkEachRoomModeInfo(dr910WDevice).getDeviceCount().equals("0")){
                    deviceMapper.insertEachRoomStatInfo(dr910WDevice);
                }

                dr910WDevice.setColumn(common.getColumnForCurrentHour());
                deviceMapper.updateEachRoomStatInfo(dr910WDevice);

            } else if (modelType.equals(modelCodeMap.get("ventilation"))) {
                dr910WDevice.setRsSl(common.convertToJsonString(common.readCon(jsonBody, "rsSl")));
                dr910WDevice.setRsPw(common.convertToJsonString(common.readCon(jsonBody, "rsPw")));
                dr910WDevice.setVfLs(common.readCon(jsonBody, "vfLs"));
                dr910WDevice.setVtSp(common.readCon(jsonBody, "vtSp"));
                dr910WDevice.setOdHm(common.readCon(jsonBody, "odHm"));
                String inAq = common.readCon(jsonBody, "inAq");
                dr910WDevice.setInAq(inAq);

                inAq = inAq.replaceAll("[\\[\\]]", ""); // 대괄호 제거
                String[] values = inAq.split(",");

                // 환기 INAQ 값 저장 예: [34,2,0,3,1172] TBR_OPR_VENT_AIR_INFO
                AuthServerDTO authServerDTO = new AuthServerDTO();
                authServerDTO.setDeviceId(deviceId);
                authServerDTO.setIndoorTemp(values[0]);
                authServerDTO.setIndoorHumi(values[1]);
                authServerDTO.setPm10(values[2]);
                authServerDTO.setPm25(values[3]);
                authServerDTO.setCo2(values[4]);
                deviceMapper.insertVentAirInfo(authServerDTO);

            } else if (modelType.contains("MC2600")) {
                dr910WDevice.setHtTp(common.readCon(jsonBody, "htTp"));
                dr910WDevice.setHwTp(common.readCon(jsonBody, "hwTp"));
                dr910WDevice.setH12(common.convertToJsonString(common.readCon(jsonBody, "12h")));
                dr910WDevice.setChTp(common.readCon(jsonBody, "chTp"));
                dr910WDevice.setTargetDeviceId(hexSerial);
                deviceMapper.updateEachRoomStatus(dr910WDevice);

                String prId = deviceMapper.getParentIdBySubId(deviceId).getParentDevice();

                dr910WDevice.setPrId(prId);
                dr910WDevice.setRegDatetime(common.getCurrentDateTimeForEachRoomMode());

                // TBR_OPR_EACH_ROOM_MODE_INFO 테이블에 해당 일에 해당 기기가 있는지 확인 (없으면 INSERT 후 로직 진행)
                if(deviceMapper.checkEachRoomModeInfo(dr910WDevice).getDeviceCount().equals("0")){
                    deviceMapper.insertEachRoomStatInfo(dr910WDevice);
                }

                dr910WDevice.setColumn(common.getColumnForCurrentHour());
                deviceMapper.updateEachRoomStatInfo(dr910WDevice);

                return "DONE";
            } else if(modelType.equals(modelCodeMap.get("heatpump160"))){
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
            }

            System.out.println(modelType);
            System.out.println(modelCodeMap.get("heatpump160"));
            System.out.println(modelType.equals(modelCodeMap.get("heatpump160")));
            mobiusService.rtstHandler(dr910WDevice);
        } else if (functionId.equals("opIf")) {
            List<AuthServerDTO> opTmInfo;
            opTmInfo = memberMapper.getUserIdFromDeviceGroup(deviceId);

            List<AuthServerDTO> inputList = new ArrayList<>();
            for (AuthServerDTO authServerDTO : opTmInfo) {
                AuthServerDTO newWkTmInfo = new AuthServerDTO();
                newWkTmInfo.setWorkTime(common.readCon(jsonBody, "wkTm"));
                String msDt = common.readCon(jsonBody, "msDt");
                if(msDt == null || msDt.isEmpty()){
                    newWkTmInfo.setMsDt(common.getCurrentDateTimeDBForamt());
                } else {
                    newWkTmInfo.setMsDt(msDt);
                }
                newWkTmInfo.setDeviceId(deviceId);
                newWkTmInfo.setUserId(authServerDTO.getUserId());
                inputList.add(newWkTmInfo);
            }
            memberMapper.insertWorkTime(inputList);
        } else {
            return "0x0106-Devices 상태 보고 요청";
        }
        return null;
    }
}