package com.oauth.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oauth.constants.MobiusResponse;
import com.oauth.dto.AuthServerDTO;
import com.oauth.dto.gw.*;
import com.oauth.mapper.DeviceMapper;
import com.oauth.mapper.MemberMapper;
import com.oauth.message.GwMessagingSystem;
import com.oauth.response.ApiResponse;
import com.oauth.service.mapper.DeviceService;
import com.oauth.utils.Common;
import com.oauth.utils.CustomException;
import com.oauth.utils.JSON;
import com.oauth.utils.RedisCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(rollbackFor = {Exception.class, CustomException.class})
public class DeviceServiceImpl implements DeviceService {

    @Autowired
    Common common;
    @Autowired
    MobiusService mobiusService;
    @Autowired
    DeviceMapper deviceMapper;
    @Autowired
    MemberMapper memberMapper;
    @Autowired
    RedisCommand redisCommand;
    @Autowired
    MobiusResponse mobiusResponse;
    @Autowired
    GwMessagingSystem gwMessagingSystem;
    @Value("${server.timeout}")
    private long TIME_OUT;
    private final String DEVICE_ID_PREFIX = "0.2.481.1.1";

    /** 전원 On/Off */
    @Override
    public ResponseEntity<?> doPowerOnOff(AuthServerDTO params) throws CustomException{

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject;
        String msg;
        PowerOnOff powerOnOff = new PowerOnOff();
        String userId = params.getUserId();
        String deviceId = params.getDeviceId();
        String redisValue;
        String serialNumber;
        String responseMessage = null;
        MobiusResponse response;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            powerOnOff.setUserId(params.getUserId());
            powerOnOff.setDeviceId(params.getDeviceId());
            powerOnOff.setControlAuthKey(params.getControlAuthKey());
            powerOnOff.setDeviceType(params.getDeviceType());
            powerOnOff.setModelCode(common.stringToHex(params.getModelCode()));
            powerOnOff.setPowerStatus(params.getPowerStatus());
            powerOnOff.setFunctionId("powr");
            powerOnOff.setUuId(common.getTransactionId());

            redisValue = userId + "," + powerOnOff.getFunctionId();
            redisCommand.setValues(powerOnOff.getUuId(), redisValue);

            AuthServerDTO device = deviceMapper.getSingleSerialNumberBydeviceId(deviceId);

            if (device == null) {
                msg = "기기정보가 없습니다.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            } else serialNumber = device.getSerialNumber();

            if(serialNumber == null) {
                msg = "전원 On/Off 실패";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            } else {
                stringObject = "Y";
                response = mobiusService.createCin(common.stringToHex("    " + serialNumber), userId, JSON.toJson(powerOnOff));
                if(!response.getResponseCode().equals("201")){
                    msg = "중계서버 오류";
                    result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }
                try {
                    // 메시징 시스템을 통해 응답 메시지 대기
                    responseMessage = gwMessagingSystem.waitForResponse("powr" + powerOnOff.getUuId(), TIME_OUT, TimeUnit.SECONDS);
                    if (responseMessage != null) {
                        // 응답 처리
                        log.info("receiveCin에서의 응답: " + responseMessage);
                        if (responseMessage.equals("0")) stringObject = "Y";
                        else stringObject = "N";
                    } else {
                        // 타임아웃이나 응답 없음 처리
                        stringObject = "T";
                        log.info("응답이 없거나 시간 초과");
                    }
                } catch (InterruptedException e) {
                    // 대기 중 인터럽트 처리
                    log.error("", e);
                }
            }

            if(stringObject.equals("Y")) {
                conMap.put("body", "Device ON/OFF OK");
                msg = "전원 On/Off 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                result.setTestVariable(responseMessage);
            }
            else {
                conMap.put("body", "Service TIME-OUT");
                msg = "응답이 없거나 시간 초과";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }

            conMap.put("targetToken", params.getPushToken());
            conMap.put("title", "Device ON/OFF");
            conMap.put("id", "Device ON/OFF ID");
            conMap.put("isEnd", "false");

            String jsonString = objectMapper.writeValueAsString(conMap);
            log.info("doPowerOnOff jsonString: " + jsonString);

            mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);
            redisCommand.deleteValues(powerOnOff.getUuId());

            params.setFunctionId("PowerOnOff");
            params.setDeviceId(deviceId);
            params.setUserId(userId);
            if(memberMapper.insertCommandHistory(params) <= 0) {
                msg = "DB_ERROR 잠시 후 다시 시도 해주십시오.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }
            return new ResponseEntity<>(result, HttpStatus.OK);
        }catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /** 홈 IoT 컨트롤러 정보 등록/수정 */
    @Override
    public ResponseEntity<?> doDeviceInfoUpsert(AuthServerDTO params) throws Exception {

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject;
        String msg;
        DeviceInfoUpsert deviceInfoUpsert = new DeviceInfoUpsert();
        String userId = params.getUserId();
        String deviceId = params.getDeviceId();
        String serialNumber = params.getSerialNumber();
        String controlAuthKey = params.getControlAuthKey();
        String registYn = params.getRegistYn();
        String responseMessage;
        String redisValue;

        MobiusResponse response;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();

        try {

            deviceInfoUpsert.setUserId(params.getUserId());
            deviceInfoUpsert.setHp(params.getHp());
            deviceInfoUpsert.setRegisYn(registYn);
            deviceInfoUpsert.setDeviceId(deviceId);
            deviceInfoUpsert.setControlAuthKey(controlAuthKey);
            deviceInfoUpsert.setTmpRegistryKey(params.getTmpRegistKey());
            deviceInfoUpsert.setDeviceType(params.getDeviceType());
            deviceInfoUpsert.setModelCode(params.getModelCode());
            deviceInfoUpsert.setSerialNumber(params.getSerialNumber());
            deviceInfoUpsert.setZipCode(params.getZipCode());
            deviceInfoUpsert.setLatitude(params.getLatitude());
            deviceInfoUpsert.setLongitude(params.getLongitude());
            deviceInfoUpsert.setDeviceNickname(params.getDeviceNickname());
            deviceInfoUpsert.setFunctionId("mfAr");
            deviceInfoUpsert.setUuId(common.getTransactionId());

            // 수정
            if(registYn.equals("N")){

                if(params.getTmpRegistKey() == null || params.getDeviceId() == null) {
                    msg = "TEMP-KEY-MISSING";
                    result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                redisValue = userId + "," + deviceInfoUpsert.getFunctionId();
                redisCommand.setValues(deviceInfoUpsert.getUuId(), redisValue);
                response = mobiusService.createCin(common.stringToHex("    " + serialNumber), userId, JSON.toJson(deviceInfoUpsert));

                if(!response.getResponseCode().equals("201")){
                    msg = "중계서버 오류";
                    result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                /* *
                 * IoT 디바이스 UPDATE 순서
                 * 1. TBT_OPR_DEVICE_REGIST - 임시 단말 등록 정보
                 * 2. TBR_OPR_DEVICE_DETAIL - 단말정보상세
                 * */
                if(deviceMapper.updateDeviceDetailLocation(params) <= 0) {
                    msg = "홈 IoT 컨트롤러 정보 수정 실패.";
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                    return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
                }

                if(deviceMapper.updateDeviceRegistLocation(params) <= 0) {
                    msg = "홈 IoT 컨트롤러 정보 수정 실패.";
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                    return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
                }

                try {
                    responseMessage = gwMessagingSystem.waitForResponse("mfAr" + deviceInfoUpsert.getUuId(), TIME_OUT, TimeUnit.SECONDS);
                    if (responseMessage != null) {
                        stringObject = "Y";
                        // 응답 처리
                        log.info("receiveCin에서의 응답: " + responseMessage);
                        if (responseMessage.equals("0")) stringObject = "Y";
                        else stringObject = "N";
                    } else {
                        // 타임아웃이나 응답 없음 처리
                        stringObject = "T";
                        log.info("응답이 없거나 시간 초과");
                    }
                } catch (InterruptedException e) {
                    // 대기 중 인터럽트 처리
                    log.error("", e);
                    throw new CustomException("507", "입력값 오류");
                }
            } else {

                /* *
                 * IoT 디바이스 등록 INSERT 순서
                 * 1. TBR_IOT_DEVICE - 디바이스
                 * 2. TBT_OPR_DEVICE_REGIST - 임시 단말 등록 정보
                 * 3. TBR_OPR_DEVICE_DETAIL - 단말정보상세
                 * 4. TBR_OPR_USER_DEVICE - 사용자 단말 정보
                 * */

                params.setModelCode(" " + params.getModelCode());
                params.setSerialNumber("    " + params.getSerialNumber());

                params.setDeviceId(DEVICE_ID_PREFIX + "." + common.stringToHex(params.getModelCode()) + "." + common.stringToHex(params.getSerialNumber()));
                params.setTmpRegistKey(params.getUserId() + "_" + common.getCurrentDateTime());

                params.setModelCode(params.getModelCode().replaceAll(" ", ""));
                params.setSerialNumber(params.getSerialNumber().replaceAll(" ", ""));

                if(deviceMapper.insertDevice(params) <= 0){
                    msg = "홈 IoT 컨트롤러 정보 등록 실패.";
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                    return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
                } else stringObject = "Y";

                if(deviceMapper.insertDeviceRegist(params) <= 0){
                    msg = "홈 IoT 컨트롤러 정보 등록 실패.";
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                    return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
                } else stringObject = "Y";

                if(deviceMapper.insertDeviceDetail(params) <= 0){
                    msg = "홈 IoT 컨트롤러 정보 등록 실패.";
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                    return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
                } else stringObject = "Y";

                if(deviceMapper.insertUserDevice(params) <= 0){
                    msg = "홈 IoT 컨트롤러 정보 등록 실패.";
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                    return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
                } else stringObject = "Y";
            }

            if (stringObject.equals("Y") && registYn.equals("Y")) {
                conMap.put("body", "Device Insert OK");
                msg = "홈 IoT 컨트롤러 정보 등록 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                result.setLatitude(params.getLatitude());
                result.setLongitude(params.getLongitude());
                result.setTmpRegistKey(params.getTmpRegistKey());

            } else if(stringObject.equals("Y") && registYn.equals("N")){
                conMap.put("body", "Device Update OK");
                msg = "홈 IoT 컨트롤러 정보 수정 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                result.setLatitude(params.getLatitude());
                result.setLongitude(params.getLongitude());
                result.setTmpRegistKey("NULL");

            } else {
                conMap.put("body", "Service TIME-OUT");
                msg = "응답이 없거나 시간 초과";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }

            conMap.put("targetToken", params.getPushToken());
            conMap.put("title", "DeviceInfoUpsert");
            conMap.put("id", "DeviceInfoUpsert ID");
            conMap.put("isEnd", "false");

            String jsonString = objectMapper.writeValueAsString(conMap);
            mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);
            redisCommand.deleteValues(deviceInfoUpsert.getUuId());

            params.setFunctionId("DeviceInfoUpsert");
            if(deviceId == null) deviceId = "EMPTy";
            params.setDeviceId(deviceId);
            params.setUserId(userId);
            if(memberMapper.insertCommandHistory(params) <= 0) {
                msg = "DB_ERROR 잠시 후 다시 시도 해주십시오.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (CustomException e){
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /** 홈 IoT 컨트롤러 상태 정보 조회  */
    // TODO: DB쿼리 정보 Return 하는 걸로 수정
    @Override
    public ResponseEntity<?> doDeviceStatusInfo(AuthServerDTO params) throws CustomException{

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject = "N";
        String msg;
        String userId = params.getUserId();
        String deviceId = params.getDeviceId();
        String uuId = common.getTransactionId();
        AuthServerDTO serialNumber;
        String responseMessage;
        MobiusResponse response;
        HashMap<String, String> request = new HashMap<>();
        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = null;

        try {
            serialNumber = deviceMapper.getSingleSerialNumberBydeviceId(params.getDeviceId());
            if(serialNumber == null) {
                msg = "홈 IoT 컨트롤러 상태 정보 조회 실패";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            } else {
                request.put("userId", params.getUserId());
                request.put("controlAuthKey", params.getControlAuthKey());
                request.put("deviceId", params.getDeviceId());
                request.put("modelCode", params.getModelCode());
                request.put("functionId", "fcnt");
                request.put("uuId", uuId);
                redisCommand.setValues(uuId, userId + "," + "fcnt");
                response = mobiusService.createCin(common.stringToHex("    " + serialNumber.getSerialNumber()), userId, JSON.toJson(request));

                if(response.getResponseCode().equals("201")){
                    try {
                        // 메시징 시스템을 통해 응답 메시지 대기
                        responseMessage = gwMessagingSystem.waitForResponse("fcnt" + uuId, TIME_OUT, TimeUnit.SECONDS);
                        // JSON 문자열 파싱
                        rootNode = objectMapper.readTree(responseMessage);
                        if (responseMessage != null) {
                            stringObject = "Y";
                            // 응답 처리
                            log.info("receiveCin에서의 응답: " + responseMessage);
                        } else {
                            // 타임아웃이나 응답 없음 처리
                            stringObject = "T";
                            log.info("응답이 없거나 시간 초과");
                        }
                    } catch (InterruptedException e) {
                        // 대기 중 인터럽트 처리
                        log.error("", e);
                    }
                }else {
                    msg = "중계서버 오류";
                    result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }
            }

            if(stringObject.equals("Y")) {
                conMap.put("body", "Device Status Info OK");
                msg = "홈 IoT 컨트롤러 상태 정보 조회 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                result.setDevice(rootNode);
            }
            else if(stringObject.equals("N")) {
                conMap.put("body", "Device Status Info FAIL");
                msg = "홈 IoT 컨트롤러 상태 정보 조회 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }
            else {
                conMap.put("body", "Service TIME-OUT");
                msg = "응답이 없거나 시간 초과";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }

            conMap.put("targetToken", params.getPushToken());
            conMap.put("title", "Device Status Info");
            conMap.put("id", "Device Status Info ID");
            conMap.put("isEnd", "false");

            String jsonString = objectMapper.writeValueAsString(conMap);
            mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);
            redisCommand.deleteValues(uuId);

            params.setFunctionId("DeviceStatusInfo");
            params.setDeviceId(deviceId);
            params.setUserId(userId);
            if(memberMapper.insertCommandHistory(params) <= 0) {
                msg = "DB_ERROR 잠시 후 다시 시도 해주십시오.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }
            return new ResponseEntity<>(result, HttpStatus.OK);
        }catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /** 모드변경  */
    @Override
    public ResponseEntity<?> doModeChange(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        ModeChange modeChange = new ModeChange();
        String stringObject = "N";
        String msg;
        String serialNumber;
        String modeCode = params.getModeCode();
        String userId = params.getUserId();
        String deviceId = params.getDeviceId();
        String sleepCode = null;
        if(params.getModeCode().equals("06")) sleepCode = params.getSleepCode();

        String responseMessage;
        String redisValue;
        MobiusResponse response;
        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();

        try  {

            AuthServerDTO device = deviceMapper.getSingleSerialNumberBydeviceId(params.getDeviceId());
            if (device == null) {
                msg = "기기정보가 없습니다.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }

            serialNumber = device.getSerialNumber();
            if(serialNumber == null) {
                msg = "모드변경 실패";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }
            modeChange.setUserId(params.getUserId());
            modeChange.setDeviceId(params.getDeviceId());
            modeChange.setControlAuthKey(params.getControlAuthKey());
            modeChange.setModelCode(params.getModelCode());
            modeChange.setModeCode(modeCode);

            if(modeCode.equals("06")) modeChange.setSleepCode(sleepCode);

            modeChange.setFunctionId("opMd");
            modeChange.setUuid(common.getTransactionId());
            log.info("modeChange.getUuid(): " + modeChange.getUuid());
            redisValue = userId + "," + modeChange.getFunctionId();
            redisCommand.setValues(modeChange.getUuid(), redisValue);
            response = mobiusService.createCin(common.stringToHex("    " + serialNumber), params.getUserId(), JSON.toJson(modeChange));

            if(!response.getResponseCode().equals("201")){
                msg = "중계서버 오류";
                result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            try {
                // 메시징 시스템을 통해 응답 메시지 대기
                responseMessage = gwMessagingSystem.waitForResponse("opMd" + modeChange.getUuid(), TIME_OUT, TimeUnit.SECONDS);
                if(responseMessage == null) stringObject = "T";
                else {
                    if (responseMessage.equals("0")) stringObject = "Y";
                    else stringObject = "N";
                    // 응답 처리
                    log.info("receiveCin에서의 응답: " + responseMessage);
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                log.error("", e);
            }

            if(stringObject.equals("Y")) {
                conMap.put("body", "Mode Change OK");
                msg = "모드변경 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            }
            else if(stringObject.equals("N")) {
                conMap.put("body", "Mode Change FAIL");
                msg = "모드변경 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }
            else {
                conMap.put("body", "Service TIME-OUT");
                msg = "응답이 없거나 시간 초과";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }

            conMap.put("targetToken", params.getPushToken());
            conMap.put("title", "Mode Change");
            conMap.put("id", "Mode Change ID");
            conMap.put("isEnd", "false");

            String jsonString = objectMapper.writeValueAsString(conMap);
            log.info("jsonString: " + jsonString);
            mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);

            redisCommand.deleteValues(modeChange.getUuid());

            params.setFunctionId("ModeChange");
            params.setDeviceId(deviceId);
            params.setUserId(userId);
            if(memberMapper.insertCommandHistory(params) <= 0) {
                msg = "DB_ERROR 잠시 후 다시 시도 해주십시오.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }
            return new ResponseEntity<>(result, HttpStatus.OK);
        }catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /** 실내온도 설정  */
    @Override
    public ResponseEntity<?> doTemperatureSet(AuthServerDTO params) throws CustomException{

        ApiResponse.Data result = new ApiResponse.Data();
        TemperatureSet temperatureSet = new TemperatureSet();
        String stringObject = "N";
        String msg;
        String responseMessage;
        String userId = params.getUserId();
        String deviceId = params.getDeviceId();
        String redisValue;
        MobiusResponse response;
        String serialNumber;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        try {

            AuthServerDTO device = deviceMapper.getSingleSerialNumberBydeviceId(params.getDeviceId());
            if (device == null) {
                msg = "기기정보가 없습니다.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }

            serialNumber = device.getSerialNumber();
            if(serialNumber == null) {
                msg = "실내온도 설정 실패";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }

            temperatureSet.setUserId(userId);
            temperatureSet.setDeviceId(params.getDeviceId());
            temperatureSet.setControlAuthKey(params.getControlAuthKey());
            temperatureSet.setTemperature(params.getTemperture());
            temperatureSet.setFunctionId("htTp");
            temperatureSet.setUuId(common.getTransactionId());

            redisValue = userId + "," + temperatureSet.getFunctionId();
            redisCommand.setValues(temperatureSet.getUuId(), redisValue);
            response = mobiusService.createCin(common.stringToHex("    " + serialNumber), userId, JSON.toJson(temperatureSet));

            if(!response.getResponseCode().equals("201")){
                msg = "중계서버 오류";
                result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            try {
                // 메시징 시스템을 통해 응답 메시지 대기
                responseMessage = gwMessagingSystem.waitForResponse("htTp" + temperatureSet.getUuId(), TIME_OUT, TimeUnit.SECONDS);
                if(responseMessage == null) stringObject = "T";
                else {
                    if (responseMessage.equals("0")) stringObject = "Y";
                    else stringObject = "N";
                    // 응답 처리
                    log.info("receiveCin에서의 응답: " + responseMessage);
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                log.error("", e);
            }

            if(stringObject.equals("Y")) {
                conMap.put("body", "TemperatureSet OK");
                msg = "실내온도 설정 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            }
            else if(stringObject.equals("N")) {
                conMap.put("body", "TemperatureSet OK");
                msg = "실내온도 설정 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }
            else {
                conMap.put("body", "Service TIME-OUT");
                msg = "응답이 없거나 시간 초과";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }

            conMap.put("targetToken", params.getPushToken());
            conMap.put("title", "TemperatureSet");
            conMap.put("id", "TemperatureSet ID");
            conMap.put("isEnd", "false");

            String jsonString = objectMapper.writeValueAsString(conMap);
            mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);
            redisCommand.deleteValues(temperatureSet.getUuId());

            params.setFunctionId("TemperatureSet");
            params.setDeviceId(deviceId);
            params.setUserId(userId);
            if(memberMapper.insertCommandHistory(params) <= 0) {
                msg = "DB_ERROR 잠시 후 다시 시도 해주십시오.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /** 난방수온도 설정  */
    @Override
    public ResponseEntity<?> doBoiledWaterTempertureSet(AuthServerDTO params) throws CustomException{

        ApiResponse.Data result = new ApiResponse.Data();
        BoiledWaterTempertureSet boiledWaterTempertureSet = new BoiledWaterTempertureSet();
        String stringObject = "N";
        String msg;
        String userId = params.getUserId();
        String deviceId = params.getDeviceId();
        String redisValue;
        String responseMessage;
        MobiusResponse response;
        String serialNumber;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        try {

            AuthServerDTO device = deviceMapper.getSingleSerialNumberBydeviceId(params.getDeviceId());
            if (device == null) {
                msg = "기기정보가 없습니다.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }

            serialNumber = device.getSerialNumber();
            if(serialNumber == null) {
                msg = "난방수온도 설정 실패";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }

            boiledWaterTempertureSet.setUserId(userId);
            boiledWaterTempertureSet.setDeviceId(params.getDeviceId());
            boiledWaterTempertureSet.setControlAuthKey(params.getControlAuthKey());
            boiledWaterTempertureSet.setTemperature(params.getTemperture());
            boiledWaterTempertureSet.setFunctionId("wtTp");
            boiledWaterTempertureSet.setUuId(common.getTransactionId());

            redisValue = userId + "," + boiledWaterTempertureSet.getFunctionId();
            redisCommand.setValues(boiledWaterTempertureSet.getUuId(), redisValue);
            response = mobiusService.createCin(common.stringToHex("    " + serialNumber), userId, JSON.toJson(boiledWaterTempertureSet));

            if(!response.getResponseCode().equals("201")){
                msg = "중계서버 오류";
                result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            try {
                // 메시징 시스템을 통해 응답 메시지 대기
                responseMessage = gwMessagingSystem.waitForResponse("wtTp" + boiledWaterTempertureSet.getUuId(), TIME_OUT, TimeUnit.SECONDS);
                if(responseMessage == null) stringObject = "T";
                else {
                    if (responseMessage.equals("0")) stringObject = "Y";
                    else stringObject = "N";
                    // 응답 처리
                    log.info("receiveCin에서의 응답: " + responseMessage);
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                log.error("", e);
            }

            if(stringObject.equals("Y")) {
                conMap.put("body", "BoiledWaterTempertureSet OK");
                msg = "난방수온도 설정 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            }
            else if(stringObject.equals("N")) {
                conMap.put("body", "BoiledWaterTempertureSet FAIL");
                msg = "난방수온도 설정 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }
            else {
                conMap.put("body", "Service TIME-OUT ");
                msg = "응답이 없거나 시간 초과";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }

            conMap.put("targetToken", params.getPushToken());
            conMap.put("title", "BoiledWaterTempertureSet");
            conMap.put("id", "BoiledWaterTempertureSet ID");
            conMap.put("isEnd", "false");

            String jsonString = objectMapper.writeValueAsString(conMap);
            log.info("jsonString: " + jsonString);
            mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);
            redisCommand.deleteValues(boiledWaterTempertureSet.getUuId());

            params.setFunctionId("BoiledWaterTempertureSet");
            params.setDeviceId(deviceId);
            params.setUserId(userId);
            if(memberMapper.insertCommandHistory(params) <= 0) {
                msg = "DB_ERROR 잠시 후 다시 시도 해주십시오.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /** 온수온도 설정 */
    @Override
    public ResponseEntity<?> doWaterTempertureSet(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        WaterTempertureSet waterTempertureSet = new WaterTempertureSet();
        String stringObject = "N";
        String msg;
        String userId = params.getUserId();
        String deviceId = params.getDeviceId();
        String redisValue;
        String responseMessage;
        MobiusResponse response;
        String serialNumber;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        try {

            AuthServerDTO device = deviceMapper.getSingleSerialNumberBydeviceId(params.getDeviceId());
            if (device == null) {
                msg = "기기정보가 없습니다.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }

            serialNumber = device.getSerialNumber();
            if(serialNumber == null) {
                msg = "온수온도 설정 실패";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }
            waterTempertureSet.setUserId(userId);
            waterTempertureSet.setDeviceId(params.getDeviceId());
            waterTempertureSet.setControlAuthKey(params.getControlAuthKey());
            waterTempertureSet.setTemperature(params.getTemperture());
            waterTempertureSet.setFunctionId("hwTp");
            waterTempertureSet.setUuId(common.getTransactionId());

            redisValue = userId + "," + waterTempertureSet.getFunctionId();
            redisCommand.setValues(waterTempertureSet.getUuId(), redisValue);
            response = mobiusResponse = mobiusService.createCin(common.stringToHex("    " + serialNumber), userId, JSON.toJson(waterTempertureSet));

            if(!response.getResponseCode().equals("201")){
                msg = "중계서버 오류";
                result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            try {
                // 메시징 시스템을 통해 응답 메시지 대기
                responseMessage = gwMessagingSystem.waitForResponse("hwTp" + waterTempertureSet.getUuId(), TIME_OUT, TimeUnit.SECONDS);
                if(responseMessage == null) stringObject = "T";
                else {
                    if (responseMessage.equals("0")) stringObject = "Y";
                    else stringObject = "N";
                    // 응답 처리
                    log.info("receiveCin에서의 응답: " + responseMessage);
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                log.error("", e);
            }

            if(stringObject.equals("Y")) {
                conMap.put("body", "WaterTempertureSet OK");
                msg = "온수온도 설정 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            }
            else if(stringObject.equals("N")) {
                conMap.put("body", "WaterTempertureSet FAIL");
                msg = "온수온도 설정 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }
            else {
                conMap.put("body", "Service TIME-OUT");
                msg = "응답이 없거나 시간 초과";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }

            conMap.put("targetToken", params.getPushToken());
            conMap.put("title", "WaterTempertureSet");
            conMap.put("id", "WaterTempertureSet ID");
            conMap.put("isEnd", "false");

            String jsonString = objectMapper.writeValueAsString(conMap);
            mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);
            redisCommand.deleteValues(waterTempertureSet.getUuId());

            params.setFunctionId("WaterTempertureSet");
            params.setDeviceId(deviceId);
            params.setUserId(userId);
            if(memberMapper.insertCommandHistory(params) <= 0) {
                msg = "DB_ERROR 잠시 후 다시 시도 해주십시오.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }

            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /** 빠른온수 설정 */
    @Override
    public ResponseEntity<?> doFastHotWaterSet(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        FastHotWaterSet fastHotWaterSet = new FastHotWaterSet();
        String stringObject = "N";
        String msg;
        String userId = params.getUserId();
        String deviceId = params.getDeviceId();
        String redisValue;
        MobiusResponse response;
        String responseMessage;
        String serialNumber;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        try {

            AuthServerDTO device = deviceMapper.getSingleSerialNumberBydeviceId(params.getDeviceId());
            if (device == null) {
                msg = "기기정보가 없습니다.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }

            serialNumber = device.getSerialNumber();
            if(serialNumber == null) {
                msg = "빠른온수 설정 실패";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }

            fastHotWaterSet.setUserId(userId);
            fastHotWaterSet.setDeviceId(params.getDeviceId());
            fastHotWaterSet.setControlAuthKey(params.getControlAuthKey());
            fastHotWaterSet.setModeCode(params.getModeCode());
            fastHotWaterSet.setFunctionId("ftMd");
            fastHotWaterSet.setUuId(common.getTransactionId());

            redisValue = userId + "," + fastHotWaterSet.getFunctionId();
            redisCommand.setValues(fastHotWaterSet.getUuId(), redisValue);
            response = mobiusResponse = mobiusService.createCin(common.stringToHex("    " + serialNumber), userId, JSON.toJson(fastHotWaterSet));

            if(!response.getResponseCode().equals("201")){
                msg = "중계서버 오류";
                result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            try {
                // 메시징 시스템을 통해 응답 메시지 대기
                responseMessage = gwMessagingSystem.waitForResponse("opMd" + fastHotWaterSet.getUuId(), TIME_OUT, TimeUnit.SECONDS);
                if(responseMessage == null) stringObject = "T";
                else {
                    if (responseMessage.equals("0")) stringObject = "Y";
                    else stringObject = "N";
                    // 응답 처리
                    log.info("receiveCin에서의 응답: " + responseMessage);
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                log.error("", e);
            }

            if(stringObject.equals("Y")) {
                conMap.put("body", "FastHotWaterSet OK");
                msg = "빠른온수 설정 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            }
            else if(stringObject.equals("N")) {
                conMap.put("body", "FastHotWaterSet FAIL");
                msg = "빠른온수 설정 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }
            else {
                conMap.put("body", "Service TIME-OUT");
                msg = "응답이 없거나 시간 초과";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }

            conMap.put("targetToken", params.getPushToken());
            conMap.put("title", "FastHotWaterSet");
            conMap.put("id", "FastHotWaterSet ID");
            conMap.put("isEnd", "false");

            String jsonString = objectMapper.writeValueAsString(conMap);
            mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);
            redisCommand.deleteValues(fastHotWaterSet.getUuId());

            params.setFunctionId("FastHotWaterSet");
            params.setDeviceId(deviceId);
            params.setUserId(userId);
            if(memberMapper.insertCommandHistory(params) <= 0) {
                msg = "DB_ERROR 잠시 후 다시 시도 해주십시오.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /** 잠금 모드 설정  */
    @Override
    public ResponseEntity<?> doLockSet(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        LockSet lockSet = new LockSet();
        String stringObject = "N";
        String msg;
        String userId = params.getUserId();
        String deviceId = params.getDeviceId();

        String redisValue;
        MobiusResponse response;
        String responseMessage;
        String serialNumber;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        try {

            AuthServerDTO device = deviceMapper.getSingleSerialNumberBydeviceId(params.getDeviceId());
            if (device == null) {
                msg = "기기정보가 없습니다.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }

            serialNumber = device.getSerialNumber();
            if(serialNumber == null) {
                msg = "잠금 모드 설정 실패";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }

            lockSet.setUserId(userId);
            lockSet.setDeviceId(params.getDeviceId());
            lockSet.setControlAuthKey(params.getControlAuthKey());
            lockSet.setLockSet(params.getLockSet());
            lockSet.setFunctionId("fcLc");
            lockSet.setUuId(common.getTransactionId());


            redisValue = userId + "," + lockSet.getFunctionId();
            redisCommand.setValues(lockSet.getUuId(), redisValue);
            response = mobiusService.createCin(common.stringToHex("    " + serialNumber), userId, JSON.toJson(lockSet));

            if(!response.getResponseCode().equals("201")){
                msg = "중계서버 오류";
                result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            try {
                // 메시징 시스템을 통해 응답 메시지 대기
                responseMessage = gwMessagingSystem.waitForResponse("fcLc" + lockSet.getUuId(), TIME_OUT, TimeUnit.SECONDS);
                gwMessagingSystem.removeMessageQueue("fcLc" + lockSet.getUuId());
                if(responseMessage == null) stringObject = "T";
                else {
                    if(responseMessage.equals("\"200\"")) stringObject = "Y";
                    else stringObject = "N";
                    // 응답 처리
                    log.info("receiveCin에서의 응답: " + responseMessage);
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                log.error("", e);
            }

            if(stringObject.equals("Y")) {
                conMap.put("body", "LockSet OK");
                msg = "잠금 모드 설정 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            }
            else if(stringObject.equals("N")) {
                conMap.put("body", "LockSet FAIL");
                msg = "잠금 모드 설정 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }
            else {
                conMap.put("body", "Service TIME-OUT");
                msg = "응답이 없거나 시간 초과";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }

            conMap.put("targetToken", params.getPushToken());
            conMap.put("title", "LockSet");
            conMap.put("id", "LockSet ID");
            conMap.put("isEnd", "false");

            String jsonString = objectMapper.writeValueAsString(conMap);
            mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);
            redisCommand.deleteValues(lockSet.getUuId());

            params.setFunctionId("LockSet");
            params.setDeviceId(deviceId);
            params.setUserId(userId);
            if(memberMapper.insertCommandHistory(params) <= 0) {
                msg = "DB_ERROR 잠시 후 다시 시도 해주십시오.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /** 홈 IoT 컨트롤러 상태 정보 조회 – 홈 화면  */
    @Override
    public ResponseEntity<?> doBasicDeviceStatusInfo(AuthServerDTO params) throws CustomException {

        /*
        * 구현전 생각해야 할 것
        * 1. 몇개의 응답을 올지 모름 (사용자가 몇개의 기기를 등록했는지 알아야함)
        * 2. 받은 응답을 어떻게 Passing 할 것인가
        * */

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject;
        String msg;

        String userId = params.getUserId();
        String uuId = common.getTransactionId();

        List<String> serialNumberList;
        List<String> rKeyList;
        List<String> deviceIdList;
        List<String> deviceNicknameList;
        List<String> addrNicknameList;
        List<String> regSortList;

        List<Map<String, String>> appResponse = new ArrayList<>();

        List<AuthServerDTO> controlAuthKeyByUserIdResult;
        List<AuthServerDTO> deviceNicknameAndDeviceLocNicknameResult;
        List<AuthServerDTO> multiSerialNumberBydeviceIdResult;
        List<DeviceStatusInfo.Device> devicesStatusInfo;
        try {

            controlAuthKeyByUserIdResult = deviceMapper.getControlAuthKeyByUserId(userId);
            if (controlAuthKeyByUserIdResult == null) {
                msg = "기기정보가 없습니다.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }

            deviceNicknameAndDeviceLocNicknameResult = deviceMapper.getDeviceNicknameAndDeviceLocNickname(controlAuthKeyByUserIdResult);
            if (deviceNicknameAndDeviceLocNicknameResult == null) {
                msg = "기기정보가 없습니다.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }

            multiSerialNumberBydeviceIdResult = deviceMapper.getMultiSerialNumberBydeviceId(controlAuthKeyByUserIdResult);
            if (multiSerialNumberBydeviceIdResult == null) {
                msg = "기기정보가 없습니다.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }

            rKeyList = Common.extractJson(controlAuthKeyByUserIdResult.toString(), "controlAuthKey");
            deviceIdList = Common.extractJson(controlAuthKeyByUserIdResult.toString(), "deviceId");
            deviceNicknameList = Common.extractJson(deviceNicknameAndDeviceLocNicknameResult.toString(), "deviceNickname");
            addrNicknameList = Common.extractJson(deviceNicknameAndDeviceLocNicknameResult.toString(), "addrNickname");
            serialNumberList = Common.extractJson(multiSerialNumberBydeviceIdResult.toString(), "serialNumber");
            regSortList = Common.extractJson(deviceNicknameAndDeviceLocNicknameResult.toString(), "regSort");

            devicesStatusInfo = deviceMapper.getDeviceStauts(serialNumberList);
            if (devicesStatusInfo == null) {
                msg = "기기정보가 없습니다.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }

            if(rKeyList == null || deviceIdList == null){
                msg = "등록된 R/C가 없습니다";
                result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            if(deviceNicknameList != null && addrNicknameList != null && regSortList != null && serialNumberList != null){
                for(int i = 0; i < rKeyList.size(); ++i){
                    System.out.println("i: " + i);
                    Map<String, String> data = new HashMap<>();
                    data.put("rKey", rKeyList.get(i));
                    data.put("deviceNickname", deviceNicknameList.get(i));
                    data.put("addrNickName", addrNicknameList.get(i));
                    data.put("regSort", regSortList.get(i));
                    data.put("deviceId", deviceIdList.get(i));
                    data.put("controlAuthKey", deviceIdList.get(i));
                    data.put("deviceStatus", "1");
                    data.put("powr", devicesStatusInfo.get(i).getPowr());
                    data.put("opMd", devicesStatusInfo.get(i).getOpMd());
                    data.put("htTp", devicesStatusInfo.get(i).getHtTp());
                    data.put("wtTp", devicesStatusInfo.get(i).getWtTp());
                    data.put("hwTp", devicesStatusInfo.get(i).getHwTp());
                    data.put("ftMd", devicesStatusInfo.get(i).getFtMd());
                    data.put("chTp", devicesStatusInfo.get(i).getChTp());
                    data.put("mfDt", devicesStatusInfo.get(i).getMfDt());
                    data.put("hwSt", devicesStatusInfo.get(i).getHwSt());
                    data.put("fcLc", devicesStatusInfo.get(i).getFcLc());
                    data.put("type24h", common.readCon(devicesStatusInfo.get(i).getStringRsCf(), "md"));
                    data.put("slCd", devicesStatusInfo.get(i).getSlCd());
                    appResponse.add(data);
                }
                stringObject = "Y";
            }else stringObject = "N";

            if(stringObject.equals("Y")) {
                msg = "홈 IoT 컨트롤러 상태 정보 조회 – 홈 화면 성공";
                result.setHomeViewValue(appResponse);
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            }
            if(stringObject.equals("N")) {
                msg = "홈 IoT 컨트롤러 상태 정보 조회 – 홈 화면 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }

            redisCommand.deleteValues(uuId);

            params.setFunctionId("BasicDeviceStatusInfo");
            params.setDeviceId("EMPTY");
            params.setUserId(userId);
            if(memberMapper.insertCommandHistory(params) <= 0) {
                msg = "DB_ERROR 잠시 후 다시 시도 해주십시오.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /** 홈 IoT 컨트롤러 정보 조회-단건 */
    @Override
    public HashMap<String, Object> doDeviceInfoSearch(AuthServerDTO params) throws CustomException {

        String userId = params.getUserId();
        String deviceId = params.getDeviceId();
        String rtCode;
        String msg;
        AuthServerDTO resultDto;
        HashMap<String, Object> result = new HashMap<>();
        HashMap<String, Object> errorResult = new HashMap<>();

        try {

        resultDto = deviceMapper.getDeviceInfoSearch(params.getDeviceId());

        if(resultDto == null){
            rtCode = "404";
            msg = "홈 IoT 정보 조회 실패";
        } else {
            rtCode = "200";
            msg = "홈 IoT 정보 조회 성공";

            result.put("modelCategoryCode", resultDto.getModelCode());
            result.put("deviceNickname", resultDto.getDeviceNickname());
            result.put("addrNickname", resultDto.getAddrNickname());
            result.put("zipCode", resultDto.getZipCode());
            result.put("oldAddr", resultDto.getOldAddr());
            result.put("newAddr", resultDto.getNewAddr());
            result.put("addrDetail", resultDto.getAddrDetail());
            result.put("latitude", resultDto.getLatitude());
            result.put("longitude", resultDto.getLongitude());
        }
            result.put("resultCode", rtCode);
            result.put("resultMsg", msg);

            params.setFunctionId("DeviceInfoSearch");
            params.setDeviceId(deviceId);
            params.setUserId(userId);
            if(memberMapper.insertCommandHistory(params) <= 0) {
                msg = "DB_ERROR 잠시 후 다시 시도 해주십시오.";
                errorResult.put("resultCode", "404");
                errorResult.put("resultMsg", msg);
                return errorResult;
            }
            return result;
        } catch (Exception e) {
            log.error("", e);
            return result;
        }
    }

    /**	홈 IoT 컨트롤러 에러 정보 조회  */
    @Override
    public ResponseEntity<?> doDeviceErrorInfo(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject;
        String msg;

        String userId = params.getUserId();
        String deviceId = params.getDeviceId();
        String serialNumber;
        List<Map<String, String>> resultMap = new ArrayList<>();
        List<AuthServerDTO> errorInfoList;
        AuthServerDTO rKeyIdentification;
        try {

            String[] parts = params.getDeviceId().split("\\.");
            serialNumber = parts[parts.length - 1]; // 배열의 마지막 요소 가져오기
            rKeyIdentification = memberMapper.identifyRKey(common.hexToString(serialNumber).replaceAll(" ", ""));
            if (rKeyIdentification == null) {
                msg = "기기정보가 없습니다.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }

            if(rKeyIdentification.getDeviceId() != null &&
                    rKeyIdentification.getSerialNumber() != null &&
                    rKeyIdentification.getControlAuthKey() != null) {
                errorInfoList = deviceMapper.getDeviceErroInfo(rKeyIdentification.getSerialNumber());
                if(errorInfoList != null){
                    for (AuthServerDTO authServerDTO : errorInfoList) {
                        Map<String, String> data = new HashMap<>();
                        data.put("errorMessage", authServerDTO.getErrorMessage());
                        data.put("errorCode", authServerDTO.getErrorCode());
                        data.put("errorDateTime", authServerDTO.getErrorDateTime());
                        data.put("serialNumber", authServerDTO.getSerialNumber());
                        resultMap.add(data);
                    }
                    stringObject = "Y";
                } else stringObject = "N";
            } else stringObject = "N";

            if(stringObject.equals("Y")) {
                msg = "홈 IoT 컨트롤러 에러 정보 조회 성공";
                result.setErrorInfo(resultMap);
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            }
            if(stringObject.equals("N")) {
                msg = "홈 IoT 컨트롤러 에러 정보 조회 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }

            params.setFunctionId("DeviceErrorInfo");
            params.setDeviceId(deviceId);
            params.setUserId(userId);
            if(memberMapper.insertCommandHistory(params) <= 0) {
                msg = "DB_ERROR 잠시 후 다시 시도 해주십시오.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }
}
