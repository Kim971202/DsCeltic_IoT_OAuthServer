package com.oauth.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oauth.constants.MobiusResponse;
import com.oauth.controller.MobiusController;
import com.oauth.dto.AuthServerDTO;
import com.oauth.dto.gw.*;
import com.oauth.mapper.DeviceMapper;
import com.oauth.message.GwMessagingSystem;
import com.oauth.response.ApiResponse;
import com.oauth.service.mapper.DeviceService;
import com.oauth.utils.Common;
import com.oauth.utils.CustomException;
import com.oauth.utils.JSON;
import com.oauth.utils.RedisCommand;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class DeviceServiceImpl implements DeviceService {

    @Autowired
    Common common;
    @Autowired
    MobiusService mobiusService;
    @Autowired
    DeviceMapper deviceMapper;
    @Autowired
    RedisCommand redisCommand;
    @Autowired
    SqlSessionFactory sqlSessionFactory;
    @Autowired
    MobiusResponse mobiusResponse;
    @Autowired
    MobiusController mobiusController;
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
        try {
            powerOnOff.setUserId(params.getUserId());
            powerOnOff.setDeviceId(params.getDeviceId());
            powerOnOff.setControlAuthKey(params.getControlAuthKey());
            powerOnOff.setDeviceType(params.getDeviceType());
            powerOnOff.setModelCode(params.getModelCode());
            powerOnOff.setPowerStatus(params.getPowerStatus());
            powerOnOff.setFunctionId("powr");
            powerOnOff.setUuId(common.getTransactionId());

            redisValue = userId + "," + powerOnOff.getFunctionId();

            redisCommand.setValues(powerOnOff.getUuId(), redisValue);

            AuthServerDTO device = deviceMapper.getSerialNumberBydeviceId(deviceId);
            serialNumber = device.getSerialNumber();

            if (!serialNumber.isEmpty()) {
                stringObject = "Y";
                response = mobiusService.createCin("serialNumber", userId, JSON.toJson(powerOnOff));
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
                        System.out.println("receiveCin에서의 응답: " + responseMessage);
                    } else {
                        // 타임아웃이나 응답 없음 처리
                        stringObject = "T";
                        System.out.println("응답이 없거나 시간 초과");
                    }
                } catch (InterruptedException e) {
                    // 대기 중 인터럽트 처리
                    e.printStackTrace();
                }
            } else {
                stringObject = "N";
            }

            if(stringObject.equals("Y")) {
                msg = "전원 On/Off 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                result.setTestVariable(responseMessage);
            }
            else if(stringObject.equals("N")) {
                msg = "전원 On/Off 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }
            else {
                msg = "응답이 없거나 시간 초과";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }

            return new ResponseEntity<>(result, HttpStatus.OK);
        }catch (Exception e){
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /** 홈 IoT 컨트롤러 정보 등록/수정 */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public ResponseEntity<?> doDeviceInfoUpsert(AuthServerDTO params) throws CustomException, SQLException {

        // Transaction용 클래스 선언
        SqlSession session = sqlSessionFactory.openSession();

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject;
        String msg;
        DeviceInfoUpsert deviceInfoUpsert = new DeviceInfoUpsert();
        String userId = params.getUserId();
        String deviceId = params.getDeviceId();
        String controlAuthKey = params.getControlAuthKey();
        String registYn = params.getRegistYn();
        String responseMessage;
        String redisValue;

        MobiusResponse response;

        DeviceMapper dMapper = session.getMapper(DeviceMapper.class);

        int insertDeviceModelCodeResult;
        int insertDeviceResult;
        int insertDeviceRegistResult;
        int insertDeviceDetailResult;

        int updateDeviceRegistLocationResult;
        int updateDeviceDetailLocationResult;
        try {

            deviceInfoUpsert.setAccessToken(params.getAccessToken());
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

            if(registYn.equals("N")){

                /* *
                 * IoT 디바이스 UPDATE 순서
                 * 1. TBT_OPR_DEVICE_REGIST - 임시 단말 등록 정보
                 * 2. TBR_OPR_DEVICE_DETAIL - 단말정보상세
                 * */
                updateDeviceRegistLocationResult = dMapper.updateDeviceRegistLocation(params);
                updateDeviceDetailLocationResult = dMapper.updateDeviceDetailLocation(params);

                if(updateDeviceRegistLocationResult > 0 && updateDeviceDetailLocationResult > 0){
                    stringObject = "Y";

                    redisValue = userId + "," + deviceInfoUpsert.getFunctionId();
                    redisCommand.setValues(deviceInfoUpsert.getUuId(), redisValue);
                    response = mobiusService.createCin("gwSever", "gwSeverCnt", JSON.toJson(deviceInfoUpsert));
                    if(!response.getResponseCode().equals("201")){
                        msg = "중계서버 오류";
                        result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                        return new ResponseEntity<>(result, HttpStatus.OK);
                    }

                    try {
                        responseMessage = gwMessagingSystem.waitForResponse("mfAr" + deviceInfoUpsert.getUuId(), TIME_OUT, TimeUnit.SECONDS);
                        if (responseMessage != null) {
                            // 응답 처리
                            System.out.println("receiveCin에서의 응답: " + responseMessage);
                        } else {
                            // 타임아웃이나 응답 없음 처리
                            stringObject = "T";
                            System.out.println("응답이 없거나 시간 초과");
                        }
                    } catch (InterruptedException e) {
                        // 대기 중 인터럽트 처리
                        e.printStackTrace();
                    }
                }
                else stringObject = "N";
            } else {

                /* *
                 * IoT 디바이스 등록 INSERT 순서
                 * 1. TBD_IOT_DEVICE_MODL_CD - 디바이스 모델 코드
                 * 2. TBR_IOT_DEVICE - 디바이스
                 * 3. TBT_OPR_DEVICE_REGIST - 임시 단말 등록 정보
                 * 4. TBR_OPR_DEVICE_DETAIL - 단말정보상세
                 * */
                params.setDeviceId(DEVICE_ID_PREFIX + "." + params.getModelCode() + "." + params.getSerialNumber());
                params.setTmpRegistKey(params.getUserId() + "_" +common.getCurrentDateTime());

                insertDeviceModelCodeResult = dMapper.insertDeviceModelCode(params);
                insertDeviceResult = dMapper.insertDevice(params);
                insertDeviceRegistResult = dMapper.insertDeviceRegist(params);
                insertDeviceDetailResult = dMapper.insertDeviceDetail(params);

                if(insertDeviceModelCodeResult > 0 &&
                        insertDeviceResult > 0 &&
                        insertDeviceRegistResult > 0 &&
                        insertDeviceDetailResult > 0)
                    stringObject = "Y";
                else stringObject = "N";

            }


            System.out.println("stringObject: " + stringObject);
            if (stringObject.equals("Y") && registYn.equals("Y")) {
                msg = "홈 IoT 컨트롤러 정보 등록 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                result.setLatitude(params.getLatitude());
                result.setLongitude(params.getLongitude());
                result.setTmpRegistKey(params.getTmpRegistKey());

            } else if(stringObject.equals("Y") && registYn.equals("N")){
                msg = "홈 IoT 컨트롤러 정보 수정 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                result.setLatitude(params.getLatitude());
                result.setLongitude(params.getLongitude());
                result.setTmpRegistKey("NULL");

            } else if(stringObject.equals("N")) {
                msg = "홈 IoT 컨트롤러 정보 등록/수정 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }
            else {
                msg = "응답이 없거나 시간 초과";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }

            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (CustomException e){
            System.out.println(e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    /** 홈 IoT 컨트롤러 상태 정보 조회  */
    @Override
    public ResponseEntity<?> doDeviceStatusInfo(AuthServerDTO params) throws CustomException{

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject = null;
        String msg;
        String userId = params.getUserId();
        String uuId = common.getTransactionId();
        HashMap<String, String> request = new HashMap<>();
        String responseMessage = null;
        MobiusResponse response;
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = null;
        try {

            request.put("accessToken", params.getAccessToken());
            request.put("userId", params.getUserId());
            request.put("controlAuthKey", params.getControlAuthKey());
            request.put("deviceId", params.getDeviceId());
            request.put("modelCode", params.getModelCode());
            request.put("functionId", "fcnt");
            request.put("uuId", uuId);

            redisCommand.setValues(uuId, userId + "," + "fcnt");
            response = mobiusService.createCin("gwSever", "gwSeverCnt", JSON.toJson(request));
            if(response.getResponseCode().equals("201")){
                try {
                    // 메시징 시스템을 통해 응답 메시지 대기
                    responseMessage = gwMessagingSystem.waitForResponse("fcnt" + uuId, TIME_OUT, TimeUnit.SECONDS);
                    // JSON 문자열 파싱
                    rootNode = objectMapper.readTree(responseMessage);
                    if (responseMessage != null) {
                        stringObject = "Y";
                        // 응답 처리
                        System.out.println("receiveCin에서의 응답: " + responseMessage);
                    } else {
                        // 타임아웃이나 응답 없음 처리
                        stringObject = "T";
                        System.out.println("응답이 없거나 시간 초과");
                    }
                } catch (InterruptedException e) {
                    // 대기 중 인터럽트 처리
                    e.printStackTrace();
                }
            }else {
                msg = "중계서버 오류";
                result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            if(stringObject.equals("Y")) {
                msg = "홈 IoT 컨트롤러 상태 정보 조회 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                result.setDevice(rootNode);
            }
            else if(stringObject.equals("N")) {
                msg = "홈 IoT 컨트롤러 상태 정보 조회 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }
            else {
                msg = "응답이 없거나 시간 초과";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }

            return new ResponseEntity<>(result, HttpStatus.OK);
        }catch (Exception e){
            System.out.println(e.getMessage());
            e.printStackTrace();
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

        String modeCode = params.getModeCode();
        String sleepCode = params.getSleepCode();
        String userId = params.getUserId();
        String responseMessage;
        String redisValue;
        MobiusResponse response;
        try  {

            modeChange.setAccessToken(params.getAccessToken());
            modeChange.setUserId(params.getUserId());
            modeChange.setDeviceId(params.getDeviceId());
            modeChange.setControlAuthKey(params.getControlAuthKey());
            modeChange.setModelCode(params.getModelCode());
            modeChange.setModeCode(modeCode);
            modeChange.setSleepCode(sleepCode);
            modeChange.setFunctionId("opMd");
            modeChange.setUuid(common.getTransactionId());

            redisValue = userId + "," + modeChange.getFunctionId();
            redisCommand.setValues(modeChange.getUuid(), redisValue);
            response = mobiusService.createCin("gwSever", "gwSeverCnt", JSON.toJson(modeChange));

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
                    System.out.println("receiveCin에서의 응답: " + responseMessage);
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                e.printStackTrace();
            }

            if(stringObject.equals("Y")) {
                msg = "모드변경 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            }
            else if(stringObject.equals("N")) {
                msg = "모드변경 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }
            else {
                msg = "응답이 없거나 시간 초과";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }

            return new ResponseEntity<>(result, HttpStatus.OK);
        }catch (Exception e){
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /** 실내온도 설정  */
    @Override
    public ResponseEntity<?> doTemperatureSet(AuthServerDTO params) throws CustomException{

        ApiResponse.Data result = new ApiResponse.Data();
        TemperatureSet temperatureSet = new TemperatureSet();
        String stringObject = null;
        String msg ;
        String responseMessage;
        String userId = params.getUserId();
        String redisValue;
        MobiusResponse response;
        try {

            temperatureSet.setAccessToken(params.getAccessToken());
            temperatureSet.setUserId(userId);
            temperatureSet.setDeviceId(params.getDeviceId());
            temperatureSet.setControlAuthKey(params.getControlAuthKey());
            temperatureSet.setTemperature(params.getTemperture());
            temperatureSet.setFunctionId("htTp");
            temperatureSet.setUuId(common.getTransactionId());

            redisValue = userId + "," + temperatureSet.getFunctionId();
            redisCommand.setValues(temperatureSet.getUuId(), redisValue);
            response = mobiusService.createCin("gwSever", "gwSeverCnt", JSON.toJson(temperatureSet));

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
                    System.out.println("receiveCin에서의 응답: " + responseMessage);
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                e.printStackTrace();
            }

            if(stringObject.equals("Y")) {
                msg = "실내온도 설정 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            }
            else if(stringObject.equals("N")) {
                msg = "실내온도 설정 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }
            else {
                msg = "응답이 없거나 시간 초과";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            e.printStackTrace();
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
        try {

            boiledWaterTempertureSet.setAccessToken(params.getAccessToken());
            boiledWaterTempertureSet.setUserId(userId);
            boiledWaterTempertureSet.setDeviceId(params.getDeviceId());
            boiledWaterTempertureSet.setControlAuthKey(params.getControlAuthKey());
            boiledWaterTempertureSet.setTemperature(params.getTemperture());
            boiledWaterTempertureSet.setFunctionId("wtTp");
            boiledWaterTempertureSet.setUuId(common.getTransactionId());

            redisValue = userId + "," + boiledWaterTempertureSet.getFunctionId();
            redisCommand.setValues(boiledWaterTempertureSet.getUuId(), redisValue);
            response = mobiusService.createCin("gwSever", "gwSeverCnt", JSON.toJson(boiledWaterTempertureSet));

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
                    System.out.println("receiveCin에서의 응답: " + responseMessage);
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                e.printStackTrace();
            }

            if(stringObject.equals("Y")) {
                msg = "난방수온도 설정 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            }
            else if(stringObject.equals("N")) {
                msg = "난방수온도 설정 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }
            else {
                msg = "응답이 없거나 시간 초과";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }
            return new ResponseEntity<>(result, HttpStatus.OK);

        } catch (Exception e){
            e.printStackTrace();
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
        try {

            waterTempertureSet.setAccessToken(params.getAccessToken());
            waterTempertureSet.setUserId(userId);
            waterTempertureSet.setDeviceId(params.getDeviceId());
            waterTempertureSet.setControlAuthKey(params.getControlAuthKey());
            waterTempertureSet.setTemperature(params.getTemperture());
            waterTempertureSet.setFunctionId("hwTp");
            waterTempertureSet.setUuId(common.getTransactionId());

            redisValue = userId + "," + waterTempertureSet.getFunctionId();
            redisCommand.setValues(waterTempertureSet.getUuId(), redisValue);
            response = mobiusResponse = mobiusService.createCin("gwSever", "gwSeverCnt", JSON.toJson(waterTempertureSet));

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
                    System.out.println("receiveCin에서의 응답: " + responseMessage);
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                e.printStackTrace();
            }

            if(stringObject.equals("Y")) {
                msg = "온수온도 설정 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            }
            else if(stringObject.equals("N")) {
                msg = "온수온도 설정 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }
            else {
                msg = "응답이 없거나 시간 초과";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }

            result.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            e.printStackTrace();
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
        try {

            fastHotWaterSet.setAccessToken(params.getAccessToken());
            fastHotWaterSet.setUserId(userId);
            fastHotWaterSet.setDeviceId(params.getDeviceId());
            fastHotWaterSet.setControlAuthKey(params.getControlAuthKey());
            fastHotWaterSet.setModeCode(params.getModeCode());
            fastHotWaterSet.setFunctionId("ftMd");
            fastHotWaterSet.setUuId(common.getTransactionId());

            redisValue = userId + "," + fastHotWaterSet.getFunctionId();
            redisCommand.setValues(fastHotWaterSet.getUuId(), redisValue);
            response = mobiusResponse = mobiusService.createCin("gwSever", "gwSeverCnt", JSON.toJson(fastHotWaterSet));

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
                    System.out.println("receiveCin에서의 응답: " + responseMessage);
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                e.printStackTrace();
            }

            if(stringObject.equals("Y")) {
                msg = "빠른온수 설정 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            }
            else if(stringObject.equals("N")) {
                msg = "빠른온수 설정 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }
            else {
                msg = "응답이 없거나 시간 초과";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }

            return new ResponseEntity<>(result, HttpStatus.OK);

        } catch (Exception e){
            e.printStackTrace();
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
        MobiusResponse response = null;
        try {

            lockSet.setAccessToken(params.getAccessToken());
            lockSet.setUserId(userId);
            lockSet.setDeviceId(params.getDeviceId());
            lockSet.setControlAuthKey(params.getControlAuthKey());
            lockSet.setLockSet(params.getLockSet());
            lockSet.setFunctionId("fcLc");
            lockSet.setUuId(common.getTransactionId());

            redisCommand.setValues(lockSet.getUuId(), userId);
            response = mobiusService.createCin("gwSever", "gwSeverCnt", JSON.toJson(lockSet));

            if(!response.getResponseCode().equals("201")){
                msg = "중계서버 오류";
                result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            if(stringObject.equals("Y")) msg = "잠금 모드 설정 성공";
            else msg = "잠금 모드 설정 실패";

            result.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    /** 홈 IoT 컨트롤러 상태 정보 조회 – 홈 화면  */
    @Override
    public ResponseEntity<?> doBasicDeviceStatusInfo(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
//        ApiResponse.Data.DeviceStatusInfo info = new ApiResponse.Data.DeviceStatusInfo();

        String stringObject;
        String msg;
        String redisValue;
        DeviceStatusInfoDR910W dr910W = DeviceStatusInfoDR910W.getInstance();
        List<DeviceStatusInfoDR910W.Device> devicesList = dr910W.getDevices();
        try {
            dr910W.setFunctionId("basicDeviceStatusInfo");
            dr910W.setUuId(common.getTransactionId());

            redisValue = params.getUserId() + "," + dr910W.getFunctionId();
            redisCommand.setValues(dr910W.getUuId(), redisValue);

//            for (DeviceStatusInfoDR910W.Device device : devicesList) {
//                mfcd = device.getMfcd();
//            }




        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
