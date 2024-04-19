package com.oauth.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oauth.constants.MobiusResponse;
import com.oauth.controller.MobiusController;
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
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
    private final String DEVICE_ID_PREFIX = "0.2.481.1.1.";

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
            serialNumber = device.getSerialNumber();

            if (!serialNumber.isEmpty()) {
                stringObject = "Y";
                response = mobiusService.createCin(serialNumber, userId, JSON.toJson(powerOnOff));
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
                    } else {
                        // 타임아웃이나 응답 없음 처리
                        stringObject = "T";
                        log.info("응답이 없거나 시간 초과");
                    }
                } catch (InterruptedException e) {
                    // 대기 중 인터럽트 처리
                    log.error("", e);
                }
            } else stringObject = "N";


            if(stringObject.equals("Y")) {
                conMap.put("body", "Device ON/OFF OK");
                msg = "전원 On/Off 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                result.setTestVariable(responseMessage);
            }
            else if(stringObject.equals("N")) {
                conMap.put("body", "Device ON/OFF OK");
                msg = "전원 On/Off 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
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
            return new ResponseEntity<>(result, HttpStatus.OK);
        }catch (Exception e){
            log.error("", e);
        }
        return null;
    }

    /** 홈 IoT 컨트롤러 정보 등록/수정 */
    @Override
    public ResponseEntity<?> doDeviceInfoUpsert(AuthServerDTO params) throws Exception {

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject = "N";
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

        int insertDeviceResult;
        int insertDeviceRegistResult;
        int insertDeviceDetailResult;

        int updateDeviceRegistLocationResult;
        int updateDeviceDetailLocationResult;

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
                response = mobiusService.createCin(serialNumber, userId, JSON.toJson(deviceInfoUpsert));

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
                updateDeviceDetailLocationResult = deviceMapper.updateDeviceDetailLocation(params);
                log.info("updateDeviceDetailLocationResult: " + updateDeviceDetailLocationResult);
                if(updateDeviceDetailLocationResult <= 0) throw new CustomException("507", "입력값 오류");

                updateDeviceRegistLocationResult = deviceMapper.updateDeviceRegistLocation(params);
                log.info("updateDeviceRegistLocationResult: " + updateDeviceRegistLocationResult);
                if(updateDeviceRegistLocationResult <= 0) throw new CustomException("507", "입력값 오류");

                try {
                    responseMessage = gwMessagingSystem.waitForResponse("mfAr" + deviceInfoUpsert.getUuId(), TIME_OUT, TimeUnit.SECONDS);
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
                    throw new CustomException("507", "입력값 오류");
                }
            } else {

                params.setSerialNumber(common.stringToHex(params.getSerialNumber()));
                params.setModelCode(common.stringToHex(params.getModelCode()));
                System.out.println("params.getSerialNumber(): " + params.getSerialNumber());
                System.out.println("params.getModelCode(): " + params.getModelCode());
                /* *
                 * IoT 디바이스 등록 INSERT 순서
                 * 1. TBD_IOT_DEVICE_MODL_CD - 디바이스 모델 코드
                 * 2. TBR_IOT_DEVICE - 디바이스
                 * 3. TBT_OPR_DEVICE_REGIST - 임시 단말 등록 정보
                 * 4. TBR_OPR_DEVICE_DETAIL - 단말정보상세
                 * */
                params.setDeviceId(DEVICE_ID_PREFIX + "." + params.getSerialNumber() + "." + params.getSerialNumber());
                params.setTmpRegistKey(params.getUserId() + "_" + common.getCurrentDateTime());

                insertDeviceResult = deviceMapper.insertDevice(params);
                if(insertDeviceResult <= 0) throw new CustomException("507", "DB저장 오류");

                insertDeviceRegistResult = deviceMapper.insertDeviceRegist(params);
                if(insertDeviceRegistResult <= 0) throw new CustomException("507", "DB저장 오류");

                insertDeviceDetailResult = deviceMapper.insertDeviceDetail(params);
                if(insertDeviceDetailResult <= 0) throw new CustomException("507", "DB저장 오류");
                else stringObject = "Y";

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
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (CustomException e){
            log.error("", e);
            throw new CustomException("507", "입력값 오류");
        }
    }

    /** 홈 IoT 컨트롤러 상태 정보 조회  */
    @Override
    public ResponseEntity<?> doDeviceStatusInfo(AuthServerDTO params) throws CustomException{

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject = null;
        String msg;
        String userId = params.getUserId();
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

            request.put("userId", params.getUserId());
            request.put("controlAuthKey", params.getControlAuthKey());
            request.put("deviceId", params.getDeviceId());
            request.put("modelCode", params.getModelCode());
            request.put("functionId", "fcnt");
            request.put("uuId", uuId);

            redisCommand.setValues(uuId, userId + "," + "fcnt");
            response = mobiusService.createCin(serialNumber.getSerialNumber(), userId, JSON.toJson(request));

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
            return new ResponseEntity<>(result, HttpStatus.OK);
        }catch (Exception e){
            log.error("", e);
        }
        return null;
    }

    /** 모드변경  */
    @Override
    public ResponseEntity<?> doModeChange(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        ModeChange modeChange = new ModeChange();
        String stringObject = null;
        String msg;
        String serialNumber;
        String modeCode = params.getModeCode();

        String sleepCode = null;
        if(params.getModeCode().equals("06")) sleepCode = params.getSleepCode();

        String userId = params.getUserId();
        String responseMessage;
        String redisValue;
        MobiusResponse response;
        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();

        try  {

            AuthServerDTO device = deviceMapper.getSingleSerialNumberBydeviceId(params.getDeviceId());
            serialNumber = device.getSerialNumber();

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
            response = mobiusService.createCin(serialNumber, params.getUserId(), JSON.toJson(modeChange));

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
            return new ResponseEntity<>(result, HttpStatus.OK);
        }catch (Exception e){
            log.error("", e);
        }
        return null;
    }

    /** 실내온도 설정  */
    @Override
    public ResponseEntity<?> doTemperatureSet(AuthServerDTO params) throws CustomException{

        ApiResponse.Data result = new ApiResponse.Data();
        TemperatureSet temperatureSet = new TemperatureSet();
        String stringObject = null;
        String msg;
        String responseMessage;
        String userId = params.getUserId();
        String redisValue;
        MobiusResponse response;
        String serialNumber;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        try {

            AuthServerDTO device = deviceMapper.getSingleSerialNumberBydeviceId(params.getDeviceId());
            serialNumber = device.getSerialNumber();

            temperatureSet.setUserId(userId);
            temperatureSet.setDeviceId(params.getDeviceId());
            temperatureSet.setControlAuthKey(params.getControlAuthKey());
            temperatureSet.setTemperature(params.getTemperture());
            temperatureSet.setFunctionId("htTp");
            temperatureSet.setUuId(common.getTransactionId());

            redisValue = userId + "," + temperatureSet.getFunctionId();
            redisCommand.setValues(temperatureSet.getUuId(), redisValue);
            response = mobiusService.createCin(serialNumber, userId, JSON.toJson(temperatureSet));

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
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
        }
        return null;
    }

    /** 난방수온도 설정  */
    @Override
    public ResponseEntity<?> doBoiledWaterTempertureSet(AuthServerDTO params) throws CustomException{

        ApiResponse.Data result = new ApiResponse.Data();
        BoiledWaterTempertureSet boiledWaterTempertureSet = new BoiledWaterTempertureSet();
        String stringObject = null;
        String msg;
        String userId = params.getUserId();
        String redisValue;
        String responseMessage;
        MobiusResponse response;
        String serialNumber;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        try {

            AuthServerDTO device = deviceMapper.getSingleSerialNumberBydeviceId(params.getDeviceId());
            serialNumber = device.getSerialNumber();

            boiledWaterTempertureSet.setUserId(userId);
            boiledWaterTempertureSet.setDeviceId(params.getDeviceId());
            boiledWaterTempertureSet.setControlAuthKey(params.getControlAuthKey());
            boiledWaterTempertureSet.setTemperature(params.getTemperture());
            boiledWaterTempertureSet.setFunctionId("wtTp");
            boiledWaterTempertureSet.setUuId(common.getTransactionId());

            redisValue = userId + "," + boiledWaterTempertureSet.getFunctionId();
            redisCommand.setValues(boiledWaterTempertureSet.getUuId(), redisValue);
            response = mobiusService.createCin(serialNumber, userId, JSON.toJson(boiledWaterTempertureSet));

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
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
        }
        return null;
    }

    /** 온수온도 설정 */
    @Override
    public ResponseEntity<?> doWaterTempertureSet(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        WaterTempertureSet waterTempertureSet = new WaterTempertureSet();
        String stringObject = null;
        String msg;
        String userId = params.getUserId();
        String redisValue;
        String responseMessage;
        MobiusResponse response;
        String serialNumber;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        try {

            AuthServerDTO device = deviceMapper.getSingleSerialNumberBydeviceId(params.getDeviceId());
            serialNumber = device.getSerialNumber();

            waterTempertureSet.setUserId(userId);
            waterTempertureSet.setDeviceId(params.getDeviceId());
            waterTempertureSet.setControlAuthKey(params.getControlAuthKey());
            waterTempertureSet.setTemperature(params.getTemperture());
            waterTempertureSet.setFunctionId("hwTp");
            waterTempertureSet.setUuId(common.getTransactionId());

            redisValue = userId + "," + waterTempertureSet.getFunctionId();
            redisCommand.setValues(waterTempertureSet.getUuId(), redisValue);
            response = mobiusResponse = mobiusService.createCin(serialNumber, userId, JSON.toJson(waterTempertureSet));

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
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
        }
        return null;
    }

    /** 빠른온수 설정 */
    @Override
    public ResponseEntity<?> doFastHotWaterSet(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        FastHotWaterSet fastHotWaterSet = new FastHotWaterSet();
        String stringObject = null;
        String msg;
        String userId = params.getUserId();
        String redisValue;
        MobiusResponse response;
        String responseMessage;
        String serialNumber;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        try {

            AuthServerDTO device = deviceMapper.getSingleSerialNumberBydeviceId(params.getDeviceId());
            serialNumber = device.getSerialNumber();

            fastHotWaterSet.setUserId(userId);
            fastHotWaterSet.setDeviceId(params.getDeviceId());
            fastHotWaterSet.setControlAuthKey(params.getControlAuthKey());
            fastHotWaterSet.setModeCode(params.getModeCode());
            fastHotWaterSet.setFunctionId("ftMd");
            fastHotWaterSet.setUuId(common.getTransactionId());

            redisValue = userId + "," + fastHotWaterSet.getFunctionId();
            redisCommand.setValues(fastHotWaterSet.getUuId(), redisValue);
            response = mobiusResponse = mobiusService.createCin(serialNumber, userId, JSON.toJson(fastHotWaterSet));

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
            return new ResponseEntity<>(result, HttpStatus.OK);

        } catch (Exception e){
            log.error("", e);
        }
        return null;
    }

    /** 잠금 모드 설정  */
    @Override
    public ResponseEntity<?> doLockSet(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        LockSet lockSet = new LockSet();
        String stringObject = null;
        String msg;
        String userId = params.getUserId();

        String redisValue;
        MobiusResponse response;
        String responseMessage;
        String serialNumber;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        try {

            AuthServerDTO device = deviceMapper.getSingleSerialNumberBydeviceId(params.getDeviceId());
            serialNumber = device.getSerialNumber();

            lockSet.setUserId(userId);
            lockSet.setDeviceId(params.getDeviceId());
            lockSet.setControlAuthKey(params.getControlAuthKey());
            lockSet.setLockSet(params.getLockSet());
            lockSet.setFunctionId("fcLc");
            lockSet.setUuId(common.getTransactionId());


            redisValue = userId + "," + lockSet.getFunctionId();
            redisCommand.setValues(lockSet.getUuId(), redisValue);
            response = mobiusService.createCin(serialNumber, userId, JSON.toJson(lockSet));

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
                conMap.put("body", "c FAIL");
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
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
        }
        return null;
    }

    /** 홈 IoT 컨트롤러 상태 정보 조회 – 홈 화면  */
    @Override
    public ResponseEntity<?> doBasicDeviceStatusInfo(AuthServerDTO params) throws Exception {

        /*
        * 구현전 생각해야 할 것
        * 1. 몇개의 응답을 올지 모름 (사용자가 몇개의 기기를 등록했는지 알아야함)
        * 2. 받은 응답을 어떻게 Passing 할 것인가
        * */

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject = null;
        String msg;

        String userId = params.getUserId();
        String uuId = common.getTransactionId();
        String functionId = "fcnt";

        String redisValue;
        MobiusResponse response;
        String responseMessage;

        List<String> serialNumberList;
        List<String> rKeyList;
        List<String> deviceIdList;
        List<String> deviceNicknameList;
        List<String> addrNicknameList;
        List<String> regSortList;
        List<String> responseList = new ArrayList<>();
        List<String> gwRKeyList;
        HashMap<String, String> request = new HashMap<>();
        List<Map<String, String>> appResponse = new ArrayList<>();
        try {

            rKeyList = Common.extractJson(deviceMapper.getControlAuthKeyByUserId(userId).toString(), "controlAuthKey");
            deviceIdList = Common.extractJson(deviceMapper.getControlAuthKeyByUserId(userId).toString(), "deviceId");
            deviceNicknameList = Common.extractJson(deviceMapper.getDeviceNicknameAndDeviceLocNickname(deviceMapper.getControlAuthKeyByUserId(userId)).toString(), "deviceNickname");
            addrNicknameList = Common.extractJson(deviceMapper.getDeviceNicknameAndDeviceLocNickname(deviceMapper.getControlAuthKeyByUserId(userId)).toString(), "addrNickname");
            regSortList = Common.extractJson(deviceMapper.getDeviceNicknameAndDeviceLocNickname(deviceMapper.getControlAuthKeyByUserId(userId)).toString(), "regSort");
            serialNumberList = Common.extractJson(deviceMapper.getMultiSerialNumberBydeviceId(deviceMapper.getControlAuthKeyByUserId(userId)).toString(), "serialNumber");

            log.info("rKeyList: " + rKeyList);
            log.info("deviceIdList: " + deviceIdList);
            log.info("deviceNicknameList: " + deviceNicknameList);
            log.info("addrNicknameList: " + addrNicknameList);
            log.info("regSortList: " + regSortList);
            log.info("serialNumberList: " + serialNumberList);

            if(rKeyList == null || deviceIdList == null){
                msg = "등록된 R/C가 없습니다";
                result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            request.put("userId", userId);
            request.put("controlAuthKey", rKeyList.get(0));
            request.put("deviceId", deviceIdList.get(0));
            request.put("functionId", functionId);
            request.put("uuId", uuId);
            log.info("request1: " + request);
            redisValue = userId + "," + functionId + "-homeView";
            redisCommand.setValues(uuId, redisValue);

            if(deviceIdList != null &&
                    deviceNicknameList != null &&
                    addrNicknameList != null &&
                    regSortList != null &&
                    serialNumberList != null){
                try {

                    for (String s : serialNumberList) {
                        response = mobiusService.createCin(s, userId, JSON.toJson(request));
                        if (!response.getResponseCode().equals("201")) return null;
                        // 메시징 시스템을 통해 응답 메시지 대기
                        responseMessage = gwMessagingSystem.waitForResponse(functionId + "-homeView" + uuId, TIME_OUT, TimeUnit.SECONDS);

                        // JSON 문자열 파싱
                        if (responseMessage != null) {
                            stringObject = "Y";
                            // 응답 처리
                            log.info("receiveCin에서의 응답 responseMessage: " + responseMessage);
                        } else {
                            // 타임아웃이나 응답 없음 처리
                            stringObject = "T";
                            log.info("응답이 없거나 시간 초과");
                        }
                        responseList.add(responseMessage);
                        log.info("responseList: " + responseList);
                    }

                    if(stringObject.equals("T")) {
                        msg = "홈 IoT 컨트롤러 상태 정보 조회 – 조회 Error";
                        result.setResult(ApiResponse.ResponseType.HTTP_500, msg);
                        return new ResponseEntity<>(result, HttpStatus.OK);
                    } else gwRKeyList = common.getHomeViewDataList(responseList, "rkey");

                    for(int i = 0; i < responseList.size(); ++i){
                        for(int j = 0; j < responseList.size(); ++j){
                            if(rKeyList.get(i).equals(gwRKeyList.get(j))){
                                Map<String, String> data = new HashMap<>();
                                data.put("rKey", rKeyList.get(i));
                                data.put("deviceNickname", deviceNicknameList.get(i));
                                data.put("addrNickName", addrNicknameList.get(i));
                                data.put("regSort", regSortList.get(i));
                                data.put("deviceId", deviceIdList.get(i));
                                data.put("controlAuthKey", deviceIdList.get(i));
                                data.put("deviceStatus", "1");
                                data.put("powr", common.getHomeViewDataList(responseList, "powr").get(j));
                                data.put("opMd", common.getHomeViewDataList(responseList, "opMd").get(j));
                                data.put("htTp", common.getHomeViewDataList(responseList, "htTp").get(j));
                                data.put("wtTp", common.getHomeViewDataList(responseList, "wtTp").get(j));
                                data.put("hwTp", common.getHomeViewDataList(responseList, "hwTp").get(j));
                                data.put("ftMd", common.getHomeViewDataList(responseList, "ftMd").get(j));
                                data.put("chTp", common.getHomeViewDataList(responseList, "chTp").get(j));
                                data.put("mfDt", common.getHomeViewDataList(responseList, "mfDt").get(j));
                                data.put("type24h", common.getHomeViewDataList(responseList, "type24h").get(j));
                                data.put("slCd", common.getHomeViewDataList(responseList, "slCd").get(j));
                                data.put("hwSt", common.getHomeViewDataList(responseList, "hwSt").get(j));
                                data.put("fcLc", common.getHomeViewDataList(responseList, "fcLc").get(j));
                                appResponse.add(data);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    // 대기 중 인터럽트 처리
                    log.error("", e);
                }
            }else {
                msg = "중계 서버 오류";
                result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }
            log.info("appResponse: " + appResponse);

            if(stringObject.equals("Y")) {
                msg = "홈 IoT 컨트롤러 상태 정보 조회 – 홈 화면 성공";
                result.setHomeViewValue(appResponse);
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            }
            else if(stringObject.equals("N")) {
                msg = "홈 IoT 컨트롤러 상태 정보 조회 – 홈 화면 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }
            else {
                msg = "응답이 없거나 시간 초과";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }

            redisCommand.deleteValues(uuId);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
        }
        return null;
    }

    /** 홈 IoT 컨트롤러 정보 조회-단건 */
    @Override
    public HashMap<String, Object> doDeviceInfoSearch(AuthServerDTO params) throws Exception {

        String rtCode;
        String msg;
        AuthServerDTO resultDto;
        HashMap<String, Object> result = new HashMap<>();

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
            return result;
        } catch (Exception e) {
            log.error("", e);
        }
        return null;
    }

    /**	홈 IoT 컨트롤러 에러 정보 조회  */
    @Override
    public ResponseEntity<?> doDeviceErrorInfo(AuthServerDTO params) throws Exception {

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject;
        String msg;
        AuthServerDTO device;
        try {

            device = memberMapper.identifyRKey(params);

            System.out.println("device: " + device.getDeviceId());
            System.out.println("device: " + device.getSerialNumber());
            System.out.println("device: " + device.getControlAuthKey());

            if(device.getDeviceId() == null ||
                    device.getSerialNumber() == null ||
                    device.getControlAuthKey() == null){
                stringObject = "N";
            } else stringObject = "Y";

            if(stringObject.equals("Y")) {
                msg = "홈 IoT 컨트롤러 에러 정보 조회 성공";
                // TODO: 추후 Input 값 변경
                result.setErrorCode("ERROR_CODE");
                result.setErrorName("ERROR_NAME");
                result.setErrorMessage("ERROR_MESSAGE");
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            } else {
                msg = "홈 IoT 컨트롤러 에러 정보 조회 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
        }
        return null;
    }
}
