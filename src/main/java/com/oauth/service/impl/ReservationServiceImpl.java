package com.oauth.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.oauth.constants.MobiusResponse;
import com.oauth.dto.AuthServerDTO;
import com.oauth.dto.gw.*;
import com.oauth.mapper.DeviceMapper;
import com.oauth.mapper.MemberMapper;
import com.oauth.message.GwMessagingSystem;
import com.oauth.response.ApiResponse;
import com.oauth.service.mapper.ReservationService;
import com.oauth.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ReservationServiceImpl implements ReservationService{

    @Autowired
    Common common;
    @Autowired
    MobiusService mobiusService;
    @Autowired
    RedisCommand redisCommand;
    @Autowired
    MobiusResponse mobiusResponse;
    @Autowired
    GwMessagingSystem gwMessagingSystem;
    @Autowired
    DeviceMapper deviceMapper;
    @Autowired
    MemberMapper memberMapper;
    @Value("${server.timeout}")
    private long TIME_OUT;

    /** 24시간 예약 */
    @Override
    public ResponseEntity<?> doSet24(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        Set24 set24 = new Set24();
        String stringObject = null;
        String msg;
        String userId = params.getUserId();
        String deviceId = params.getDeviceId();
        String hoursString = params.getHours();
        String redisValue;
        MobiusResponse response;
        String responseMessage;
        AuthServerDTO device;
        DeviceStatusInfo.Device deviceInfo = new DeviceStatusInfo.Device();
        ConcurrentHashMap<String, Object> map = new ConcurrentHashMap<String, Object>();
        try {

            device = deviceMapper.getSingleSerialNumberBydeviceId(deviceId);

            set24.setAccessToken(params.getAccessToken());
            set24.setUserId(params.getUserId());
            set24.setDeviceId(deviceId);
            set24.setControlAuthKey(params.getControlAuthKey());
            set24.setHours(params.getHours());
            set24.setType24h(params.getType24h());
            set24.setOnOffFlag(params.getOnOffFlag());
            set24.setFunctionId("24h");
            set24.setUuId(common.getTransactionId());
            List<String> ls = new ArrayList<>();
            ls.add(params.getHours());
            System.out.println(ls.get(0).getClass());
            System.out.println(params.getType24h());

            // 문자열을 리스트로 변환
            hoursString = hoursString.replace("[", "").replace("]", "").replace("\"", "");
            String[] hoursList = hoursString.split(",");

            // ArrayList에 숫자값 추가
            ArrayList<String> hourArray = new ArrayList<>(Arrays.asList(hoursList));

            // Map에 데이터 추가
            map.put("md", params.getType24h());
            map.put("hs", hourArray);

            // 결과 출력
            System.out.println("결과 출력");
            System.out.println(map);
            System.out.println(JSON.toJson(map));

            redisValue = userId + "," + set24.getFunctionId();
            redisCommand.setValues(set24.getUuId(), redisValue);
            response = mobiusService.createCin(common.stringToHex("    " + device.getSerialNumber()), userId, JSON.toJson(set24));

            if(!response.getResponseCode().equals("201")){
                msg = "중계서버 오류";
                result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            try {
                // 메시징 시스템을 통해 응답 메시지 대기
                responseMessage = gwMessagingSystem.waitForResponse(set24.getFunctionId() + set24.getUuId(), TIME_OUT, TimeUnit.SECONDS);
                if(responseMessage == null) stringObject = "T";
                else {
                    if(responseMessage.equals("0")) stringObject = "Y";
                    else stringObject = "N";
                    // 응답 처리
                    log.info("receiveCin에서의 응답: " + responseMessage);
                }
            } catch (InterruptedException e) {
                log.error("", e);
            }

            if(stringObject.equals("Y")) {
                msg = "24시간 예약 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            }
            else if(stringObject.equals("N")) {
                msg = "24시간 예약 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }
            else {
                msg = "응답이 없거나 시간 초과";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }

            deviceInfo.setH24(JSON.toJson(map));
            deviceInfo.setDeviceId(deviceId);
            log.info("setH24: " + JSON.toJson(map));
            log.info("deviceId: " + deviceId);
            deviceMapper.updateDeviceStatusFromApplication(deviceInfo);

            params.setCodeType("1");
            params.setCommandId("Set24");
            params.setControlCode("24h");
            params.setControlCodeName("24시간 예약");
            params.setCommandFlow("0");
            params.setDeviceId(deviceId);
            params.setUserId(userId);
            if(memberMapper.insertCommandHistory(params) <= 0) {
                msg = "DB_ERROR 잠시 후 다시 시도 해주십시오.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }

            redisCommand.deleteValues(set24.getUuId());
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
        }
        return null;
    }

    /** 반복(12시간) 예약 */
    @Override
    public ResponseEntity<?> doSet12(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        Set12 set12 = new Set12();
        String stringObject = null;
        String msg;

        String userId = params.getUserId();
        String deviceId = params.getDeviceId();
        String responseMessage;
        String redisValue;
        MobiusResponse response;
        AuthServerDTO device;
        DeviceStatusInfo.Device deviceInfo = new DeviceStatusInfo.Device();
        ConcurrentHashMap<String, String> dbMap = new ConcurrentHashMap<String, String>();
        try {

            device = deviceMapper.getSingleSerialNumberBydeviceId(deviceId);

            set12.setAccessToken(params.getAccessToken());
            set12.setUserId(userId);
            set12.setDeviceId(deviceId);
            set12.setControlAuthKey(params.getControlAuthKey());
            set12.setWorkPeriod(params.getWorkPeriod());
            set12.setWorkTime(params.getWorkTime());
            set12.setOnOffFlag(params.getOnOffFlag());

            set12.setFunctionId("12h");
            set12.setUuId(common.getTransactionId());

            redisValue = userId + "," + set12.getFunctionId();
            redisCommand.setValues(set12.getUuId(), redisValue);
            response = mobiusResponse = mobiusService.createCin(common.stringToHex("    " + device.getSerialNumber()), userId, JSON.toJson(set12));

            if(!response.getResponseCode().equals("201")){
                msg = "중계서버 오류";
                result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            try {
                // 메시징 시스템을 통해 응답 메시지 대기
                responseMessage = gwMessagingSystem.waitForResponse(set12.getFunctionId() + set12.getUuId(), TIME_OUT, TimeUnit.SECONDS);
                if(responseMessage == null) stringObject = "T";
                else {
                    if(responseMessage.equals("0")) stringObject = "Y";
                    else stringObject = "N";
                    // 응답 처리
                    log.info("receiveCin에서의 응답: " + responseMessage);
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                log.error("", e);
            }

            if(stringObject.equals("Y")) {
                msg = "12시간 예약 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            }
            else if(stringObject.equals("N")) {
                msg = "12시간 예약 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }
            else {
                msg = "응답이 없거나 시간 초과";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }

            dbMap.put("hr", params.getWorkPeriod());
            dbMap.put("mn", params.getWorkTime());
            System.out.println(JSON.toJson(dbMap));
            deviceInfo.setH12(common.convertToJsonString(JSON.toJson(dbMap)));
            deviceInfo.setDeviceId(deviceId);
            deviceMapper.updateDeviceStatusFromApplication(deviceInfo);

            params.setCodeType("1");
            params.setCommandId("Set12");
            params.setControlCode("12h");
            params.setControlCodeName("12시간 예약");
            params.setCommandFlow("0");
            params.setDeviceId(deviceId);
            params.setUserId(userId);
            if(memberMapper.insertCommandHistory(params) <= 0) {
                msg = "DB_ERROR 잠시 후 다시 시도 해주십시오.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }

            redisCommand.deleteValues(set12.getUuId());
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
        }
        return null;
    }

    /** 빠른 온수 예약  */
    @Override
    public ResponseEntity<?> doAwakeAlarmSet(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject = null;
        String msg;
        String userId = params.getUserId();
        String deviceId = params.getDeviceId();
        AwakeAlarmSet awakeAlarmSet = new AwakeAlarmSet();
        List<HashMap<String, Object>> awakeList = new ArrayList<HashMap<String, Object>>();
        HashMap<String, Object> map = new HashMap<>();

        String redisValue;
        MobiusResponse response;
        String responseMessage;
        AuthServerDTO device;
        DeviceStatusInfo.Device deviceInfo = new DeviceStatusInfo.Device();
        ConcurrentHashMap<String, String> dbMap = new ConcurrentHashMap<String, String>();
        try {

            device = deviceMapper.getSingleSerialNumberBydeviceId(deviceId);

            /**
             * “awakeList” :
             * [
             *      {
             *          "ws":["1","2","3"], "hr" : "06", "mn" : "30"
             *      },
             *      {
             *          "ws":["4","5","6"], "hr" : "07", "mn": "30"
             *      }
             * ]
             * */
            awakeAlarmSet.setAccessToken(params.getAccessToken());
            awakeAlarmSet.setUuId(params.getUserId());
            awakeAlarmSet.setDeviceId(deviceId);
            awakeAlarmSet.setControlAuthKey(params.getControlAuthKey());
            awakeAlarmSet.setFunctionId("ftMd");
            awakeAlarmSet.setUuId(common.getTransactionId());

            for(int i = 0 ; i < params.getWs().length; ++i){
                map.put("ws", Arrays.asList(params.getWs()[i]));
                map.put("mn", params.getMn()[i]);
                map.put("hr", params.getHr()[i]);
                awakeList.add(map);
                map = new HashMap<>();
            }

            awakeAlarmSet.setAwakeList(awakeList);

            redisValue = userId + "," + awakeAlarmSet.getFunctionId();
            redisCommand.setValues(awakeAlarmSet.getUuId(), redisValue);
            response = mobiusService.createCin(common.stringToHex("    " + device.getSerialNumber()), userId, JSON.toJson(awakeAlarmSet));

            if(!response.getResponseCode().equals("201")){
                msg = "중계서버 오류";
                result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            try {
                // 메시징 시스템을 통해 응답 메시지 대기
                responseMessage = gwMessagingSystem.waitForResponse(awakeAlarmSet.getFunctionId() + awakeAlarmSet.getUuId(), TIME_OUT, TimeUnit.SECONDS);
                if(responseMessage == null) stringObject = "T";
                else {
                    if(responseMessage.equals("0")) stringObject = "Y";
                    else stringObject = "N";
                    // 응답 처리
                    log.info("receiveCin에서의 응답: " + responseMessage);
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                log.error("", e);
            }

            if(stringObject.equals("Y")) {
                msg = "빠른 온수 예약 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            }
            else if(stringObject.equals("N")) {
                msg = "빠른 온수 예약 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }
            else {
                msg = "응답이 없거나 시간 초과";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }

            dbMap.put("hs", params.getHours().toString());
            dbMap.put("md", params.getType24h());

            deviceInfo.setH24(common.convertToJsonString(JSON.toJson(dbMap)));
            deviceInfo.setDeviceId(deviceId);
            deviceMapper.updateDeviceStatusFromApplication(deviceInfo);

            params.setCodeType("1");
            params.setCommandId("AwakeAlarmSet");
            params.setControlCode("fwh");
            params.setControlCodeName("빠른온수 예약");
            params.setCommandFlow("0");
            params.setDeviceId(deviceId);
            params.setUserId(userId);
            if(memberMapper.insertCommandHistory(params) <= 0) {
                msg = "DB_ERROR 잠시 후 다시 시도 해주십시오.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }

            redisCommand.deleteValues(awakeAlarmSet.getUuId());
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
        }
        return null;
    }

    /** 주간 예약  */
    @Override
    public ResponseEntity<?> doSetWeek(AuthServerDTO params) throws CustomException {

        log.info("Params:" + params);

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject = null;
        String msg;
        String userId = params.getUserId();
        String deviceId = params.getDeviceId();
        SetWeek setWeek = new SetWeek();
        List<HashMap<String, Object>> weekList = new ArrayList<HashMap<String, Object>>();
        HashMap<String, Object> map = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();

        String responseMessage;
        String redisValue;
        MobiusResponse response;
        AuthServerDTO device;
        DeviceStatusInfo.Device deviceInfo = new DeviceStatusInfo.Device();
        try {

            if(params.getOnOffFlag().equals("of")){
                msg = "최초 빠른온수 설정";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            device = deviceMapper.getSingleSerialNumberBydeviceId(params.getDeviceId());

            setWeek.setUserId(params.getUserId());
            setWeek.setDeviceId(params.getDeviceId());
            setWeek.setControlAuthKey(params.getControlAuthKey());
            setWeek.setFunctionId("7wk");
            setWeek.setUuId(common.getTransactionId());
            setWeek.setOnOffFlag(params.getOnOffFlag());

            log.info("params.getWeekList(): " + (common.convertToJsonString(params.getWeekList())));
            JsonNode jsonNode = objectMapper.readTree(common.convertToJsonString(params.getWeekList()));

            for(int i = 0; i < jsonNode.path("7wk").size(); ++i){
                map.put("wk", jsonNode.path("7wk").get(i).path("wk"));
                map.put("hs", jsonNode.path("7wk").get(i).path("hs"));
                weekList.add(map);
                map = new HashMap<>();
            }
            setWeek.setWeekList(weekList);
            redisValue = userId + "," + setWeek.getFunctionId();
            redisCommand.setValues(setWeek.getUuId(), redisValue);

            log.info("JSON.toJson(setWeek, true): " + JSON.toJson(setWeek, true));

            response = mobiusService.createCin(common.stringToHex("    " + device.getSerialNumber()), userId, JSON.toJson(setWeek));

            if(!response.getResponseCode().equals("201")){
                msg = "중계서버 오류";
                result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            try {
                // 메시징 시스템을 통해 응답 메시지 대기
                responseMessage = gwMessagingSystem.waitForResponse(setWeek.getFunctionId() + setWeek.getUuId(), TIME_OUT, TimeUnit.SECONDS);
                if(responseMessage == null) stringObject = "T";
                else {
                    if(responseMessage.equals("0")) stringObject = "Y";
                    else stringObject = "N";
                    // 응답 처리
                    log.info("receiveCin에서의 응답: " + responseMessage);
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                log.error("", e);
            }

            if(stringObject.equals("Y")) {
                msg = "주간 예약 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            }
            else if(stringObject.equals("N")) {
                msg = "주간 예약 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }
            else {
                msg = "응답이 없거나 시간 초과";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }

            deviceInfo.setWk7(JSON.toJson(weekList));
            deviceInfo.setDeviceId(deviceId);
            deviceMapper.updateDeviceStatusFromApplication(deviceInfo);

            params.setCodeType("1");
            params.setCommandId("SetWeek");
            params.setControlCode("7wk");
            params.setControlCodeName("주간 예약");
            params.setCommandFlow("0");
            params.setDeviceId(deviceId);
            params.setUserId(userId);
            if(memberMapper.insertCommandHistory(params) <= 0) {
                msg = "DB_ERROR 잠시 후 다시 시도 해주십시오.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }

            params.setWeekList("");

            redisCommand.deleteValues(setWeek.getUuId());
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
        }
        return null;
    }
}
