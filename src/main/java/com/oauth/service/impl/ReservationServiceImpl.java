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
        String stringObject = "N";
        String msg;
        String userId;
        String deviceId = params.getDeviceId();
        String hoursString = params.getHours();
        String redisValue;
        MobiusResponse response;
        String responseMessage = null;
        AuthServerDTO device;
        AuthServerDTO userNickname;
        AuthServerDTO household;
        AuthServerDTO firstDeviceUser;

        DeviceStatusInfo.Device deviceInfo = new DeviceStatusInfo.Device();
        ConcurrentHashMap<String, Object> map = new ConcurrentHashMap<String, Object>();
        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        try {

            firstDeviceUser = memberMapper.getFirstDeviceUser(deviceId);
            userId = firstDeviceUser.getUserId();

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
            log.info("결과 출력");
            log.info("map: " + map);
            log.info(JSON.toJson(map));

            redisValue = params.getUserId() + "," + set24.getFunctionId();
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

            gwMessagingSystem.removeMessageQueue(set24.getFunctionId() + set24.getUuId());
            redisCommand.deleteValues(set24.getUuId());

            if(memberMapper.updatePushToken(params) <= 0) log.info("구글 FCM TOKEN 갱신 실패.");

            if(responseMessage != null && responseMessage.equals("2")){
                conMap.put("body", "RemoteController WIFI ERROR");
                msg = "기기 네트워크 연결 오류. 잠시후 다시 시도하십시오";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1014, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else {
                if(stringObject.equals("Y")) {
                    msg = "24시간 예약 성공";
                    conMap.put("body", "24hSet OK");
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                }
                else if(stringObject.equals("N")) {
                    msg = "24시간 예약 실패";
                    conMap.put("body", "24hSet FAIL");
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }
                else {
                    msg = "기기 응답이 없습니다. 잠시후 다시 시도하십시오";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1015, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                household = memberMapper.getHouseholdByUserId(params.getUserId());
                params.setGroupId(household.getGroupId());
                List<AuthServerDTO> userIds = memberMapper.getUserIdsByDeviceId(params);
                List<AuthServerDTO> pushYnList = memberMapper.getPushYnStatusByUserIds(userIds);
                userNickname = memberMapper.getUserNickname(params.getUserId());
                userNickname.setUserNickname(common.stringToHex(userNickname.getUserNickname()));

                for(int i = 0; i < userIds.size(); ++i){
                    log.info("쿼리한 UserId: " + userIds.get(i).getUserId());
                    conMap.put("pushYn", pushYnList.get(i).getFPushYn());
                    conMap.put("modelCode", common.getModelCodeFromDeviceId(deviceId));
                    conMap.put("targetToken", memberMapper.getPushTokenByUserId(userIds.get(i).getUserId()).getPushToken());
                    conMap.put("userNickname", userNickname.getUserNickname());
                    conMap.put("deviceNick", common.returnDeviceNickname(deviceId));
                    conMap.put("title", "24h");
                    conMap.put("deviceId", deviceId);
                    conMap.put("id", "Set24 ID");

                    String jsonString = objectMapper.writeValueAsString(conMap);
                    log.info("jsonString: " + jsonString);

                    if(!mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString).getResponseCode().equals("201")) log.info("PUSH 메세지 전송 오류");
                }

                deviceInfo.setH24(JSON.toJson(map));
                deviceInfo.setDeviceId(deviceId);
                deviceMapper.updateDeviceStatusFromApplication(deviceInfo);

                common.insertHistory(
                        "1",
                        "Set24",
                        "24h",
                        "24시간 예약",
                        "0",
                        deviceId,
                        params.getUserId(),
                        "예약 설정",
                        "24시간 예약",
                        "01");

//                params.setCodeType("1");
//                params.setCommandId("Set24");
//                params.setControlCode("24h");
//                params.setControlCodeName("24시간 예약");
//                params.setCommandFlow("0");
//                params.setDeviceId(deviceId);
//                params.setUserId(params.getUserId());
//
//                if(memberMapper.insertCommandHistory(params) <= 0) log.info("DB_ERROR 잠시 후 다시 시도 해주십시오.");
//
//                params.setPushTitle("기기제어");
//                params.setPushContent("24시간 예약");
//                params.setDeviceType("01");
//                if(memberMapper.insertPushHistory(params) <= 0) log.info("PUSH HISTORY INSERT ERROR");
            }

            log.info("result: " + result);
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
        String stringObject = "N";
        String msg;

        String userId;
        String deviceId = params.getDeviceId();
        String responseMessage = null;
        String redisValue;
        MobiusResponse response;
        AuthServerDTO device;
        AuthServerDTO userNickname;
        AuthServerDTO household;
        AuthServerDTO firstDeviceUser;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        DeviceStatusInfo.Device deviceInfo = new DeviceStatusInfo.Device();
        ConcurrentHashMap<String, String> dbMap = new ConcurrentHashMap<String, String>();
        try {

            firstDeviceUser = memberMapper.getFirstDeviceUser(deviceId);
            userId = firstDeviceUser.getUserId();

            device = deviceMapper.getSingleSerialNumberBydeviceId(deviceId);

            set12.setAccessToken(params.getAccessToken());
            set12.setUserId(params.getUserId());
            set12.setDeviceId(deviceId);
            set12.setControlAuthKey(params.getControlAuthKey());
            set12.setWorkPeriod(params.getWorkPeriod());
            set12.setWorkTime(params.getWorkTime());
            set12.setOnOffFlag(params.getOnOffFlag());

            set12.setFunctionId("12h");
            set12.setUuId(common.getTransactionId());

            redisValue = params.getUserId() + "," + set12.getFunctionId();
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

            gwMessagingSystem.removeMessageQueue(set12.getFunctionId() + set12.getUuId());
            redisCommand.deleteValues(set12.getUuId());

            if(memberMapper.updatePushToken(params) <= 0) log.info("구글 FCM TOKEN 갱신 실패.");

            if(responseMessage != null && responseMessage.equals("2")){
                conMap.put("body", "RemoteController WIFI ERROR");
                msg = "기기 네트워크 연결 오류. 잠시후 다시 시도하십시오";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1014, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else {
                if(stringObject.equals("Y")) {
                    msg = "12시간 예약 성공";
                    conMap.put("body", "12hSet OK");
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                }
                else if(stringObject.equals("N")) {
                    msg = "12시간 예약 실패";
                    conMap.put("body", "12hSet FAIL");
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }
                else {
                    msg = "기기 응답이 없습니다. 잠시후 다시 시도하십시오";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1015, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                dbMap.put("hr", params.getWorkPeriod());
                dbMap.put("mn", params.getWorkTime());
                System.out.println(JSON.toJson(dbMap));
                deviceInfo.setH12(common.convertToJsonString(JSON.toJson(dbMap)));
                deviceInfo.setDeviceId(deviceId);
                deviceMapper.updateDeviceStatusFromApplication(deviceInfo);

                household = memberMapper.getHouseholdByUserId(params.getUserId());
                params.setGroupId(household.getGroupId());
                List<AuthServerDTO> userIds = memberMapper.getUserIdsByDeviceId(params);
                List<AuthServerDTO> pushYnList = memberMapper.getPushYnStatusByUserIds(userIds);
                userNickname = memberMapper.getUserNickname(params.getUserId());
                userNickname.setUserNickname(common.stringToHex(userNickname.getUserNickname()));

                for(int i = 0; i < userIds.size(); ++i){
                    log.info("쿼리한 UserId: " + userIds.get(i).getUserId());
                    conMap.put("pushYn", pushYnList.get(i).getFPushYn());
                    conMap.put("modelCode", common.getModelCodeFromDeviceId(deviceId));
                    conMap.put("targetToken", memberMapper.getPushTokenByUserId(userIds.get(i).getUserId()).getPushToken());
                    conMap.put("userNickname", userNickname.getUserNickname());
                    conMap.put("deviceNick", common.returnDeviceNickname(deviceId));
                    conMap.put("title", "12h");
                    conMap.put("deviceId", deviceId);
                    conMap.put("id", "Set12 ID");

                    String jsonString = objectMapper.writeValueAsString(conMap);
                    log.info("jsonString: " + jsonString);

                    if(!mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString).getResponseCode().equals("201")) log.info("PUSH 메세지 전송 오류");
                }

                common.insertHistory(
                        "1",
                        "Set12",
                        "12h",
                        "12시간 예약",
                        "0",
                        deviceId,
                        params.getUserId(),
                        "예약 설정",
                        "12시간 예약",
                        "01");

//                params.setCodeType("1");
//                params.setCommandId("Set12");
//                params.setControlCode("12h");
//                params.setControlCodeName("12시간 예약");
//                params.setCommandFlow("0");
//                params.setDeviceId(deviceId);
//                params.setUserId(params.getUserId());
//
//                if(memberMapper.insertCommandHistory(params) <= 0) log.info("DB_ERROR 잠시 후 다시 시도 해주십시오.");
//
//                params.setPushTitle("기기제어");
//                params.setPushContent("12시간 예약");
//                params.setDeviceType("01");
//                if(memberMapper.insertPushHistory(params) <= 0) log.info("PUSH HISTORY INSERT ERROR");
            }

            log.info("result: " + result);
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
        String stringObject = "N";
        String msg;
        String userId;
        String deviceId = params.getDeviceId();
        AwakeAlarmSet awakeAlarmSet = new AwakeAlarmSet();
        List<HashMap<String, Object>> awakeList = new ArrayList<HashMap<String, Object>>();
        Map<String, String> conMap = new HashMap<>();
        String redisValue;
        MobiusResponse response;
        String responseMessage = null;
        AuthServerDTO device;
        AuthServerDTO household;
        AuthServerDTO userNickname;
        AuthServerDTO firstDeviceUser;

        DeviceStatusInfo.Device deviceInfo = new DeviceStatusInfo.Device();
        ObjectMapper objectMapper = new ObjectMapper();
        try {

            firstDeviceUser = memberMapper.getFirstDeviceUser(deviceId);
            userId = firstDeviceUser.getUserId();

            device = deviceMapper.getSingleSerialNumberBydeviceId(deviceId);

            /* *
             * “awakeList” :
             * [
             *      {
             *         tf ,"ws":["1","2","3"], "hr" : "06", "mn" : "30"
             *      },
             *      {
             *          "ws":["4","5","6"], "hr" : "07", "mn": "30"
             *      }
             * ]
             * */
            awakeAlarmSet.setUserId(params.getUserId());
            awakeAlarmSet.setAccessToken(params.getAccessToken());
            awakeAlarmSet.setDeviceId(deviceId);
            awakeAlarmSet.setControlAuthKey(params.getControlAuthKey());
            awakeAlarmSet.setFunctionId("fwh");
            awakeAlarmSet.setUuId(common.getTransactionId());
            log.info("params.getAwakeList(): " + params.getAwakeList());

            JsonNode jsonNode = objectMapper.readTree(common.convertToJsonString(params.getAwakeList()));
            // awakeList 배열을 순회하며 처리
            for (int i = 0; i < jsonNode.path("awakeList").size(); i++) {

                HashMap<String, Object> map = new LinkedHashMap<>();

                // ws를 처리하여 List<String>으로 변환
                List<String> wsList = new ArrayList<>();
                JsonNode wsNode = jsonNode.path("awakeList").get(i).path("ws");

                // ws가 문자열로 처리된 경우 배열로 변환하는 로직 추가
                if (wsNode.isTextual()) {
                    String wsString = wsNode.asText();
                    // 문자열을 제거하고 배열로 변환
                    wsString = wsString.replace("[", "").replace("]", "").replace("\"", "");
                    String[] wsArray = wsString.split(", ");
                    wsList.addAll(Arrays.asList(wsArray));
                } else if (wsNode.isArray()) {
                    for (JsonNode wsElement : wsNode) {
                        wsList.add(wsElement.asText());
                    }
                }

                map.put("tf", jsonNode.path("awakeList").get(i).path("tf").asText());

                // ws 값을 map에 추가
                map.put("ws", wsList);

                // hr과 mn 처리
                map.put("hr", jsonNode.path("awakeList").get(i).path("hr").asText());
                map.put("mn", jsonNode.path("awakeList").get(i).path("mn").asText());
                map.put("i", jsonNode.path("awakeList").get(i).path("i").asText());

                // 완성된 map을 awakeList에 추가
                awakeList.add(map);
            }

            awakeAlarmSet.setAwakeList(awakeList);
            deviceInfo.setFwh(JSON.toJson(awakeList));

            redisValue = params.getUserId() + "," + awakeAlarmSet.getFunctionId();
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
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                log.error("", e);
            }

            gwMessagingSystem.removeMessageQueue(awakeAlarmSet.getFunctionId() + awakeAlarmSet.getUuId());
            redisCommand.deleteValues(awakeAlarmSet.getUuId());

            if(memberMapper.updatePushToken(params) <= 0) log.info("구글 FCM TOKEN 갱신 실패.");

            if(responseMessage != null && responseMessage.equals("2")){
                conMap.put("body", "RemoteController WIFI ERROR");
                msg = "기기 네트워크 연결 오류. 잠시후 다시 시도하십시오";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1014, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else {
                if(stringObject.equals("Y")) {
                    msg = "빠른 온수 예약 성공";
                    conMap.put("body", "FastHotWaterReservation OK");
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                } else if(stringObject.equals("N")) {
                    msg = "빠른 온수 예약 실패";
                    conMap.put("body", "FastHotWaterReservation FAIL");
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                } else {
                    msg = "기기 응답이 없습니다. 잠시후 다시 시도하십시오";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1015, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                deviceInfo.setDeviceId(deviceId);
                deviceMapper.updateDeviceStatusFromApplication(deviceInfo);

                household = memberMapper.getHouseholdByUserId(params.getUserId());
                params.setGroupId(household.getGroupId());
                List<AuthServerDTO> userIds = memberMapper.getUserIdsByDeviceId(params);
                List<AuthServerDTO> pushYnList = memberMapper.getPushYnStatusByUserIds(userIds);
                userNickname = memberMapper.getUserNickname(params.getUserId());
                userNickname.setUserNickname(common.stringToHex(userNickname.getUserNickname()));

                for(int i = 0; i < userIds.size(); ++i){
                    conMap.put("pushYn", pushYnList.get(i).getFPushYn());
                    conMap.put("modelCode", common.getModelCodeFromDeviceId(deviceId));
                    conMap.put("targetToken", memberMapper.getPushTokenByUserId(userIds.get(i).getUserId()).getPushToken());
                    conMap.put("userNickname", userNickname.getUserNickname());
                    conMap.put("deviceNick", common.returnDeviceNickname(deviceId));
                    conMap.put("title", "fwh");
                    conMap.put("deviceId", deviceId);
                    conMap.put("id", "AwakeAlarmSet ID");

                    String jsonString = objectMapper.writeValueAsString(conMap);
                    log.info("jsonString: " + jsonString);

                    if(!mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString).getResponseCode().equals("201")) log.info("PUSH 메세지 전송 오류");
                }

                common.insertHistory(
                        "1",
                        "AwakeAlarmSet",
                        "fwh",
                        "빠른온수 예약",
                        "0",
                        deviceId,
                        params.getUserId(),
                        "예약 설정",
                        "빠른온수 예약",
                        "01");

//                params.setCodeType("1");
//                params.setCommandId("AwakeAlarmSet");
//                params.setControlCode("fwh");
//                params.setControlCodeName("빠른온수 예약");
//                params.setCommandFlow("0");
//                params.setDeviceId(deviceId);
//                params.setUserId(params.getUserId());
//
//                if(memberMapper.insertCommandHistory(params) <= 0) log.info("DB_ERROR 잠시 후 다시 시도 해주십시오.");
//
//                params.setPushTitle("기기제어");
//                params.setPushContent("빠른온수 예약");
//                params.setDeviceType("01");
//                if(memberMapper.insertPushHistory(params) <= 0) log.info("PUSH HISTORY INSERT ERROR");
            }

            log.info("result: " + result);
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
        String stringObject = "N";
        String msg;
        String userId;
        String deviceId = params.getDeviceId();
        SetWeek setWeek = new SetWeek();
        List<HashMap<String, Object>> weekList = new ArrayList<HashMap<String, Object>>();
        HashMap<String, Object> map = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        AuthServerDTO household;
        String responseMessage = null;
        String redisValue;
        MobiusResponse response;
        AuthServerDTO device;
        AuthServerDTO userNickname;
        AuthServerDTO firstDeviceUser;

        Map<String, String> conMap = new HashMap<>();
        DeviceStatusInfo.Device deviceInfo = new DeviceStatusInfo.Device();
        try {

            firstDeviceUser = memberMapper.getFirstDeviceUser(deviceId);
            userId = firstDeviceUser.getUserId();

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
            redisValue = params.getUserId() + "," + setWeek.getFunctionId();
            redisCommand.setValues(setWeek.getUuId(), redisValue);

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
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                log.error("", e);
            }

            gwMessagingSystem.removeMessageQueue(setWeek.getFunctionId() + setWeek.getUuId());
            redisCommand.deleteValues(setWeek.getUuId());

            if(memberMapper.updatePushToken(params) <= 0) log.info("구글 FCM TOKEN 갱신 실패.");

            if(responseMessage != null && responseMessage.equals("2")){
                conMap.put("body", "RemoteController WIFI ERROR");
                msg = "기기 네트워크 연결 오류. 잠시후 다시 시도하십시오";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1014, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else {
                if(stringObject.equals("Y")) {
                    msg = "주간 예약 성공";
                    conMap.put("body", "7wkSet OK");
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                }
                else if(stringObject.equals("N")) {
                    msg = "주간 예약 실패";
                    conMap.put("body", "7wkSet FAIL");
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }
                else {
                    msg = "기기 응답이 없습니다. 잠시후 다시 시도하십시오";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1015, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                deviceInfo.setWk7(JSON.toJson(weekList));
                deviceInfo.setDeviceId(deviceId);
                deviceMapper.updateDeviceStatusFromApplication(deviceInfo);

                household = memberMapper.getHouseholdByUserId(params.getUserId());
                params.setGroupId(household.getGroupId());
                List<AuthServerDTO> userIds = memberMapper.getUserIdsByDeviceId(params);
                List<AuthServerDTO> pushYnList = memberMapper.getPushYnStatusByUserIds(userIds);
                userNickname = memberMapper.getUserNickname(params.getUserId());
                userNickname.setUserNickname(common.stringToHex(userNickname.getUserNickname()));
                for(int i = 0; i < userIds.size(); ++i){
                    conMap.put("pushYn", pushYnList.get(i).getFPushYn());
                    conMap.put("modelCode", common.getModelCodeFromDeviceId(deviceId));
                    conMap.put("targetToken", memberMapper.getPushTokenByUserId(userIds.get(i).getUserId()).getPushToken());
                    conMap.put("userNickname", userNickname.getUserNickname());
                    conMap.put("deviceNick", common.returnDeviceNickname(deviceId));
                    conMap.put("title", "7wk");
                    conMap.put("deviceId", deviceId);
                    conMap.put("id", "Mode Change ID");

                    String jsonString = objectMapper.writeValueAsString(conMap);

                    if(!mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString).getResponseCode().equals("201")) log.info("PUSH 메세지 전송 오류");
                }

                common.insertHistory(
                        "1",
                        "SetWeek",
                        "7wk",
                        "주간 예약",
                        "0",
                        deviceId,
                        params.getUserId(),
                        "예약 설정",
                        "주간 예약",
                        "01");

//                params.setCodeType("1");
//                params.setCommandId("SetWeek");
//                params.setControlCode("7wk");
//                params.setControlCodeName("주간 예약");
//                params.setCommandFlow("0");
//                params.setDeviceId(deviceId);
//                params.setUserId(params.getUserId());
//                if(memberMapper.insertCommandHistory(params) <= 0) log.info("DB_ERROR 잠시 후 다시 시도 해주십시오.");
//
//                params.setPushTitle("기기제어");
//                params.setPushContent("주간 예약");
//                params.setDeviceType("01");
//                if(memberMapper.insertPushHistory(params) <= 0) log.info("PUSH HISTORY INSERT ERROR");

                params.setWeekList("");
                redisCommand.deleteValues(setWeek.getUuId());
            }

            log.info("result: " + result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
        }
        return null;
    }

    /** 환기 취침 예약  */
    @Override
    public ResponseEntity<?> doSetSleepMode(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject = "N";
        String msg;
        String serialNumber;

        SetSleepMode setSleepMode = new SetSleepMode();

        String accessToken = params.getAccessToken();
        String userId = params.getUserId();
        String deviceId = params.getDeviceId();
        String controlAuthKey = params.getControlAuthKey();
        String onHour = params.getOnHour();
        String onMinute = params.getOnMinute();
        String offHour = params.getOffHour();
        String offMinute = params.getOffMinute();
        String onOffFlag = params.getOnOffFlag();

        String redisValue;
        MobiusResponse response;
        String responseMessage = null;
        AuthServerDTO userNickname;
        AuthServerDTO household;
        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();

        DeviceStatusInfo.Device deviceInfo = new DeviceStatusInfo.Device();
        try {

            /*
            {
               "onTm":{"hr":"22", "mn":"30"},
               "ofTm":{"hr":"08", "mn":"00"}
            }
            */

            // onOffFlag가 on인 경우에만 기기제어
            if(onOffFlag.equals("on")){
                setSleepMode.setAccessToken(accessToken);
                setSleepMode.setUserId(userId);
                setSleepMode.setDeviceId(deviceId);
                setSleepMode.setControlAuthKey(controlAuthKey);
                setSleepMode.setFunctionId("rsSl");
                setSleepMode.setUuId(common.getTransactionId());

                // onTm 객체 생성
                Map<String, String> onTm = new HashMap<>();
                onTm.put("hr", onHour);
                onTm.put("mn", onMinute);

                // ofTm 객체 생성
                Map<String, String> ofTm = new HashMap<>();
                ofTm.put("hr", offHour);
                ofTm.put("mn", offMinute);

                // 최종 JSON 구조를 위한 객체 생성
                Map<String, Map<String, String>> timeMap = new HashMap<>();
                timeMap.put("onTm", onTm);
                timeMap.put("ofTm", ofTm);

                setSleepMode.setSleepTimerList(timeMap);

                redisValue = userId + "," + "setSleepMode";
                redisCommand.setValues(setSleepMode.getUuId(), redisValue);

                AuthServerDTO device = deviceMapper.getSingleSerialNumberBydeviceId(deviceId);

                if(device == null) {
                    msg = "기기정보가 없습니다.";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1009, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                } else serialNumber = device.getSerialNumber();

                if(serialNumber == null) {
                    msg = "기기정보가 없습니다.";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1009, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                } else {
                    stringObject = "Y";
                    response = mobiusService.createCin(common.stringToHex("    " + serialNumber), userId, JSON.toJson(setSleepMode));
                    if(!response.getResponseCode().equals("201")){
                        msg = "중계서버 오류";
                        result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                        return new ResponseEntity<>(result, HttpStatus.OK);
                    }
                    try {
                        // 메시징 시스템을 통해 응답 메시지 대기
                        gwMessagingSystem.printMessageQueues();
                        responseMessage = gwMessagingSystem.waitForResponse("setSleepMode" + setSleepMode.getUuId(), TIME_OUT, TimeUnit.SECONDS);
                        if (responseMessage != null) {
                            // 응답 처리
                            log.info("receiveCin에서의 응답: " + responseMessage);
                            if (responseMessage.equals("0")) stringObject = "Y";
                            else stringObject = "N";
                        } else {
                            // 타임아웃이나 응답 없음 처리
                            stringObject = "T";
                        }
                    } catch (InterruptedException e) {
                        // 대기 중 인터럽트 처리
                        log.error("", e);
                    }
                }
                gwMessagingSystem.removeMessageQueue("setSleepMode" + setSleepMode.getUuId());

                if(memberMapper.updatePushToken(params) <= 0) log.info("구글 FCM TOKEN 갱신 실패.");

                if(responseMessage != null && responseMessage.equals("2")){
                    conMap.put("body", "RemoteController WIFI ERROR");
                    msg = "기기 네트워크 연결 오류. 잠시후 다시 시도하십시오";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1014, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                } else {
                    if(stringObject.equals("Y")) {
                        conMap.put("body", "Ventilation ON/OFF OK");
                        msg = "환기 취침 모드 성공";
                        result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                        result.setTestVariable(responseMessage);
                    }
                    else {
                        conMap.put("body", "Service TIME-OUT");
                        msg = "기기 응답이 없습니다. 잠시후 다시 시도하십시오";
                        result.setResult(ApiResponse.ResponseType.CUSTOM_1015, msg);
                        return new ResponseEntity<>(result, HttpStatus.OK);
                    }
                }
                redisCommand.deleteValues(setSleepMode.getUuId());

                deviceInfo.setPowr(params.getPowerStatus());
                deviceInfo.setDeviceId(deviceId);
                deviceMapper.updateDeviceStatusFromApplication(deviceInfo);


                params.setCodeType("1");
                params.setCommandId("SetSleepMode");
                params.setControlCode("setSleepMode");
                params.setControlCodeName("환기 취침 모드");
                params.setCommandFlow("0");
                params.setDeviceId(deviceId);
                params.setUserId(userId);
                if(memberMapper.insertCommandHistory(params) <= 0) log.info("DB_ERROR 잠시 후 다시 시도 해주십시오.");

                params.setPushTitle("기기제어");
                params.setPushContent("환기 취침 모드");
                params.setDeviceId(deviceId);
                params.setDeviceType("07");
                if(memberMapper.insertPushHistory(params) <= 0) log.info("PUSH HISTORY INSERT ERROR");

                household = memberMapper.getHouseholdByUserId(userId);
                params.setGroupId(household.getGroupId());
                List<AuthServerDTO> userIds = memberMapper.getUserIdsByDeviceId(params);
                List<AuthServerDTO> pushYnList = memberMapper.getPushYnStatusByUserIds(userIds);
                userNickname = memberMapper.getUserNickname(userId);
                userNickname.setUserNickname(common.stringToHex(userNickname.getUserNickname()));

                for(int i = 0; i < userIds.size(); ++i){
                    conMap.put("targetToken", memberMapper.getPushTokenByUserId(userIds.get(i).getUserId()).getPushToken());
                    conMap.put("title", "setSleepMode");
                    conMap.put("powr", params.getPowerStatus());
                    conMap.put("userNickname", userNickname.getUserNickname());
                    conMap.put("pushYn", pushYnList.get(i).getFPushYn());
                    conMap.put("deviceNick", common.returnDeviceNickname(deviceId));
                    conMap.put("modelCode", common.getModelCodeFromDeviceId(deviceId));
                    conMap.put("deviceId", deviceId);
                    String jsonString = objectMapper.writeValueAsString(conMap);

                    if(!mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString).getResponseCode().equals("201"))
                        log.info("PUSH 메세지 전송 오류");
                }
            }
            log.info("result: " + result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
        }

        return null;
    }

    /** 환기 꺼짐/켜짐 예약 */
    @Override
    public ResponseEntity<?> doSetOnOffPower(AuthServerDTO params) throws CustomException {

        SetOnOffPower setOnOffPower = new SetOnOffPower();
        ApiResponse.Data result = new ApiResponse.Data();

        String stringObject = "N";
        String msg;
        String userId;
        String deviceId = params.getDeviceId();
        String controlAuthKey = params.getControlAuthKey();
        String redisValue;
        String responseMessage = null;

        String power = params.getPowerStatus();
        String waitHour = params.getWaitHour();
        String waitMinute = params.getWaitMinute();

        AuthServerDTO userNickname;
        AuthServerDTO household;
        AuthServerDTO firstDeviceUser;
        MobiusResponse response;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();

        DeviceStatusInfo.Device deviceInfo = new DeviceStatusInfo.Device();

        try {

            firstDeviceUser = memberMapper.getFirstDeviceUser(deviceId);
            userId = firstDeviceUser.getUserId();
            
            setOnOffPower.setUserId(params.getUserId());
            setOnOffPower.setDeviceId(deviceId);
            setOnOffPower.setControlAuthkey(controlAuthKey);
            setOnOffPower.setFunctionId("rsPw");
            setOnOffPower.setUuId(common.getTransactionId());

            // onOffTimerList 객체 생성
            Map<String, String> onOffTimerList = new HashMap<>();
            onOffTimerList.put("pw", power);
            onOffTimerList.put("hr", waitHour);
            onOffTimerList.put("mn", waitMinute);

            setOnOffPower.setOnOffTimerList(onOffTimerList);

            redisValue = params.getUserId() + "," + setOnOffPower.getFunctionId();
            redisCommand.setValues(setOnOffPower.getUuId(), redisValue);

            AuthServerDTO device = deviceMapper.getSingleSerialNumberBydeviceId(deviceId);

            stringObject = "Y";

            response = mobiusService.createCin(common.stringToHex("    " + device.getSerialNumber()), userId, JSON.toJson(setOnOffPower));
            if (!response.getResponseCode().equals("201")) {
                msg = "중계서버 오류";
                result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }
            try {
                // 메시징 시스템을 통해 응답 메시지 대기
                gwMessagingSystem.printMessageQueues();
                responseMessage = gwMessagingSystem.waitForResponse("rsPw" + setOnOffPower.getUuId(), TIME_OUT, TimeUnit.SECONDS);
                if (responseMessage != null) {
                    // 응답 처리
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

            gwMessagingSystem.removeMessageQueue("rsPw" + setOnOffPower.getUuId());

            if(memberMapper.updatePushToken(params) <= 0) log.info("구글 FCM TOKEN 갱신 실패.");

            if(responseMessage != null && responseMessage.equals("2")){
                conMap.put("body", "RemoteController WIFI ERROR");
                msg = "기기 네트워크 연결 오류. 잠시후 다시 시도하십시오";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1014, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else {
                if(stringObject.equals("Y")) {
                    conMap.put("body", "Device ON/OFF OK");
                    msg = "환기 꺼짐/켜짐 예약 성공";
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                    result.setTestVariable(responseMessage);
                }
                else {
                    conMap.put("body", "Service TIME-OUT");
                    msg = "기기 응답이 없습니다. 잠시후 다시 시도하십시오";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1015, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                redisCommand.deleteValues(setOnOffPower.getUuId());

                deviceInfo.setPowr(params.getPowerStatus());
                deviceInfo.setDeviceId(deviceId);
                deviceMapper.updateDeviceStatusFromApplication(deviceInfo);

//                params.setCodeType("1");
//                params.setCommandId("SetOnOffPower");
//                params.setControlCode("rsPw");
//                params.setControlCodeName("환기 꺼짐/켜짐 예약");
//                params.setCommandFlow("0");
//                params.setDeviceId(deviceId);
//                params.setUserId(params.getUserId());
//                if(memberMapper.insertCommandHistory(params) <= 0) log.info("DB_ERROR 잠시 후 다시 시도 해주십시오.");
//
//                params.setPushTitle("기기제어");
//                params.setPushContent("환기 꺼짐/켜짐 예약");
//                params.setDeviceId(deviceId);
//                params.setDeviceType("07");
//                if(memberMapper.insertPushHistory(params) <= 0) log.info("PUSH HISTORY INSERT ERROR");

                household = memberMapper.getHouseholdByUserId(params.getUserId());
                params.setGroupId(household.getGroupId());
                List<AuthServerDTO> userIds = memberMapper.getUserIdsByDeviceId(params);
                List<AuthServerDTO> pushYnList = memberMapper.getPushYnStatusByUserIds(userIds);
                userNickname = memberMapper.getUserNickname(params.getUserId());
                userNickname.setUserNickname(common.stringToHex(userNickname.getUserNickname()));

                for(int i = 0; i < userIds.size(); ++i){
                    conMap.put("targetToken", memberMapper.getPushTokenByUserId(userIds.get(i).getUserId()).getPushToken());
                    conMap.put("title", "rsPw");
                    conMap.put("rsPw", params.getPowerStatus());
                    conMap.put("userNickname", userNickname.getUserNickname());
                    conMap.put("pushYn", pushYnList.get(i).getFPushYn());
                    conMap.put("deviceNick", common.returnDeviceNickname(deviceId));
                    conMap.put("modelCode", common.getModelCodeFromDeviceId(deviceId));
                    conMap.put("deviceId", deviceId);
                    String jsonString = objectMapper.writeValueAsString(conMap);

                    if(!mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString).getResponseCode().equals("201"))
                        log.info("PUSH 메세지 전송 오류");
                }

                common.insertHistory(
                        "1",
                        "SetOnOffPower",
                        "rsPw",
                        "환기 꺼짐/켜짐 예약",
                        "0",
                        deviceId,
                        params.getUserId(),
                        "예약 설정",
                        "환기 꺼짐/켜짐 예약",
                        "07");

            }
            log.info("result: " + result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
        }
        return null;
    }
}
