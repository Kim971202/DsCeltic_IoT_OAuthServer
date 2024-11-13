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
import java.util.concurrent.ConcurrentHashMap;
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
    UserServiceImpl userService;
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
    @Value("#{${device.model.code}}")
    Map<String, String> modelCodeMap;

    /** 전원 On/Off */
    @Override
    public ResponseEntity<?> doPowerOnOff(AuthServerDTO params) throws CustomException{

        ApiResponse.Data result = new ApiResponse.Data();
        PowerOnOff powerOnOff = new PowerOnOff();

        String stringObject;
        String msg;
        String userId;
        String deviceId = params.getDeviceId();
        String deviceType = params.getDeviceType();
        String modelCode = params.getModelCode();
        String redisValue;
        String serialNumber;
        String responseMessage = null;

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

            powerOnOff.setUserId(params.getUserId());
            powerOnOff.setDeviceId(params.getDeviceId());
            powerOnOff.setControlAuthKey(params.getControlAuthKey());
            powerOnOff.setDeviceType(params.getDeviceType());
            powerOnOff.setModelCode(common.stringToHex(params.getModelCode()));
            powerOnOff.setPowerStatus(params.getPowerStatus());
            powerOnOff.setFunctionId("powr");
            powerOnOff.setUuId(common.getTransactionId());

            redisValue = params.getUserId() + "," + powerOnOff.getFunctionId();
            redisCommand.setValues(powerOnOff.getUuId(), redisValue);

            AuthServerDTO device = deviceMapper.getSingleSerialNumberBydeviceId(deviceId);

            if (device == null) {
                msg = "기기정보가 없습니다.";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1009, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else serialNumber = device.getSerialNumber();

            if(serialNumber == null) {
                msg = "전원 On/Off 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1009, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
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
                    gwMessagingSystem.printMessageQueues();
                    responseMessage = gwMessagingSystem.waitForResponse("powr" + powerOnOff.getUuId(), TIME_OUT, TimeUnit.SECONDS);
                    if (responseMessage != null) {
                        // 응답 처리
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

            gwMessagingSystem.removeMessageQueue("powr" + powerOnOff.getUuId());
            redisCommand.deleteValues(powerOnOff.getUuId());

            if(responseMessage != null && responseMessage.equals("2")){
                conMap.put("body", "RemoteController WIFI ERROR");
                msg = "RC WIFI 오류";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1014, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else {
                if(stringObject.equals("Y")) {
                    conMap.put("body", "Device ON/OFF OK");
                    msg = "전원 On/Off 성공";
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                    result.setTestVariable(responseMessage);
                }
                else {
                    conMap.put("body", "Service TIME-OUT");
                    msg = "응답이 없거나 시간 초과";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1015, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                deviceInfo.setPowr(params.getPowerStatus());
                deviceInfo.setDeviceId(deviceId);
                deviceMapper.updateDeviceStatusFromApplication(deviceInfo);

                params.setCodeType("1");
                params.setCommandId("PowerOnOff");
                params.setControlCode("powr");
                params.setControlCodeName("전원 ON/OFF");
                params.setCommandFlow("0");
                params.setDeviceId(deviceId);
                params.setUserId(params.getUserId());
                if(memberMapper.insertCommandHistory(params) <= 0) log.info("DB_ERROR 잠시 후 다시 시도 해주십시오.");

                params.setPushTitle("기기제어");
                params.setPushContent("전원 ON/OFF");
                params.setDeviceId(deviceId);
                params.setDeviceType(deviceType);
                if(memberMapper.insertPushHistory(params) <= 0) log.info("PUSH HISTORY INSERT ERROR");

                household = memberMapper.getHouseholdByUserId(params.getUserId());
                params.setGroupId(household.getGroupId());
                List<AuthServerDTO> userIds = memberMapper.getUserIdsByDeviceId(params);
                List<AuthServerDTO> pushYnList = memberMapper.getPushYnStatusByUserIds(userIds);
                userNickname = memberMapper.getUserNickname(params.getUserId());
                userNickname.setUserNickname(common.stringToHex(userNickname.getUserNickname()));

                for(int i = 0; i < userIds.size(); ++i){
                    conMap.put("targetToken", memberMapper.getPushTokenByUserId(userIds.get(i).getUserId()).getPushToken());
                    conMap.put("title", "powr");
                    conMap.put("powr", params.getPowerStatus());
                    conMap.put("isEnd", "false");
                    conMap.put("userNickname", userNickname.getUserNickname());
                    conMap.put("pushYn", pushYnList.get(i).getFPushYn());
                    conMap.put("modelCode", modelCode);
                    conMap.put("deviceNick", common.returnDeviceNickname(deviceId));
                    String jsonString = objectMapper.writeValueAsString(conMap);
                    if(!mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString).getResponseCode().equals("201"))
                        log.info("PUSH 메세지 전송 오류");
                }
            }
            if(memberMapper.updatePushToken(params) <= 0) log.info("구글 FCM TOKEN 갱신 실패.");
            log.info("result: " + result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        }catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /** 홈 IoT 컨트롤러 정보 등록/수정 */
    @Override
    public ResponseEntity<?> doDeviceInfoUpsert(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();

        String stringObject = "N";
        String msg;
        String userId = params.getUserId();
        String deviceId = params.getDeviceId();
        String registYn = params.getRegistYn();

        AuthServerDTO checkDeviceExist;
        AuthServerDTO groupLeaderId;
        AuthServerDTO groupLeaderIdByGroupIdx;

        List<AuthServerDTO> familyMemberList;

        try {
            // 수정
            if(registYn.equals("N")){

                if(params.getTmpRegistKey() == null || params.getDeviceId() == null) {
                    msg = "TEMP-KEY-MISSING";
                    result.setResult(ApiResponse.ResponseType.HTTP_400, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                /* *
                 * IoT 디바이스 UPDATE 순서
                 * 1. TBT_OPR_DEVICE_REGIST - 임시 단말 등록 정보
                 * 2. TBR_OPR_DEVICE_DETAIL - 단말정보상세
                 * */
                if(deviceMapper.updateDeviceDetailLocation(params) <= 0) {
                    msg = "홈 IoT 컨트롤러 정보 수정 실패.";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                if(deviceMapper.updateDeviceRegistLocation(params) <= 0) {
                    msg = "홈 IoT 컨트롤러 정보 수정 실패.";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                } else stringObject = "Y";

            // 등록
            } else if(registYn.equals("Y")){

                /* *
                 * IoT 디바이스 등록 INSERT 순서
                 * 1. TBR_IOT_DEVICE - 디바이스
                 * 2. TBT_OPR_DEVICE_REGIST - 임시 단말 등록 정보
                 * 3. TBR_OPR_DEVICE_DETAIL - 단말정보상세
                 * 4. TBR_OPR_USER_DEVICE - 사용자 단말 정보
                 *
                 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                 * 1. Push 설정 관련 기본 DB 추가 (기본값: Y)
                 * 2. IOT_DEVICE 테이블 등록 시 최초 기기 등록자 ID도 같이 등록
                 * */

                params.setModelCode(" " + params.getModelCode());
                params.setSerialNumber("    " + params.getSerialNumber());

                params.setDeviceId(params.getDeviceId());
                params.setTmpRegistKey(params.getTmpRegistKey());

                params.setModelCode(params.getModelCode().replaceAll(" ", ""));
                params.setSerialNumber(params.getSerialNumber().replaceAll(" ", ""));

                // TODO: 같은 기기를 이전에 등록한 사람이 있다면, 해당 기기를 삭제후 등록 진행 한다.
                checkDeviceExist = deviceMapper.checkDeviceExist(deviceId);
                if(!checkDeviceExist.getDeviceCount().equals("0")){
                    AuthServerDTO authServerDTO = deviceMapper.getCheckedDeviceExist(deviceId);
                    memberMapper.deleteControllerMapping(authServerDTO);
                }

                System.out.println(params.getGroupIdx() == null);    // false
                System.out.println(params.getGroupIdx().isEmpty()); // true

                params.setGroupId(userId);
                // TODO: GroupIdx가 NULL이면 신규 등록
                if(params.getGroupIdx().isEmpty()){
                    memberMapper.insertInviteGroup(params);
                    memberMapper.updateInviteGroup(params);
                    // TODO: 신규 등록 시 등록한 Idx를 기반으로 사용자 ID 쿼리
                    groupLeaderId = memberMapper.getGroupLeaderId(params.getIdx());
                    familyMemberList = memberMapper.getFailyMemberByUserId(groupLeaderId.getGroupId());
                } else {
                    params.setIdx(Long.parseLong(params.getGroupIdx()));
                    groupLeaderIdByGroupIdx = memberMapper.getGroupLeaderIdByGroupIdx(params.getGroupIdx());
                    familyMemberList = memberMapper.getFailyMemberByUserId(groupLeaderIdByGroupIdx.getGroupId());
                }

                // Push 설정 관련 기본 DB 추가
                List<AuthServerDTO> inputList = new ArrayList<>();
                for (AuthServerDTO authServerDTO : familyMemberList){
                    // 새로운 AuthServerDTO 객체 생성
                    AuthServerDTO memberInfo = new AuthServerDTO();
                    // 한개의 기기만 추가
                    memberInfo.setDeviceId(deviceId);
                    // 각 사용자의 ID와 HP 설정
                    memberInfo.setHp(authServerDTO.getHp());
                    memberInfo.setUserId(authServerDTO.getUserId());
                    // 리스트에 추가
                    inputList.add(memberInfo);
                }

                if(memberMapper.insertUserDevicePushByList(inputList) <= 0){
                    msg = "사용자 PUSH 정보 등록 실패.";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                if(deviceMapper.insertDevice(params) <= 0){
                    msg = "홈 IoT 컨트롤러 정보 등록 실패.";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                if(deviceMapper.insertFristDeviceUser(params) <= 0){
                    msg = "홈 IoT 컨트롤러 정보 등록 실패.";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                if(deviceMapper.insertDeviceRegist(params) <= 0){
                    msg = "홈 IoT 컨트롤러 정보 등록 실패.";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                if(deviceMapper.insertDeviceDetail(params) <= 0){
                    msg = "홈 IoT 컨트롤러 정보 등록 실패.";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                if(deviceMapper.insertUserDevice(params) <= 0){
                    msg = "홈 IoT 컨트롤러 정보 등록 실패.";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                if(deviceMapper.insertDeviceGrpInfo(params) <= 0){
                    msg = "홈 IoT 컨트롤러 정보 등록 실패.";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }
                stringObject = "Y";
            }

            log.info("stringObject: " + stringObject);
            log.info("registYn: " + registYn);

            if (stringObject.equals("Y") && registYn.equals("Y")) {
                msg = "홈 IoT 컨트롤러 정보 등록 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                result.setLatitude(params.getLatitude());
                result.setLongitude(params.getLongitude());
                result.setTmpRegistKey(params.getTmpRegistKey());

            } else if(stringObject.equals("Y")){
                msg = "홈 IoT 컨트롤러 정보 수정 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                result.setLatitude(params.getLatitude());
                result.setLongitude(params.getLongitude());
                result.setTmpRegistKey("NULL");
            }

            if(memberMapper.updatePushToken(params) <= 0) log.info("구글 FCM TOKEN 갱신 실패.");

            params.setUserId(userId);
            params.setPushTitle("기기제어");
            if(deviceId == null) deviceId = "EMPTY";
            params.setDeviceId(deviceId);

            if(registYn.equals("N"))
                params.setPushContent("기기정보 수정");
            else if(registYn.equals("Y"))
                params.setPushContent("신규기기 등록");

            log.info("result: " + result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (CustomException e){
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /** 홈 IoT 컨트롤러 상태 정보 조회  */
    @Override
    public ResponseEntity<?> doDeviceStatusInfo(AuthServerDTO params) throws CustomException{

        ApiResponse.Data result = new ApiResponse.Data();
        String msg;
        String modelCode = params.getModelCode();
        String uuId = common.getTransactionId();
        AuthServerDTO serialNumber;
        Map<String, String> resultMap = new HashMap<>();
        List<DeviceStatusInfo.Device> device;

        try {

            serialNumber = deviceMapper.getSingleSerialNumberBydeviceId(params.getDeviceId());
            if(serialNumber == null) {
                msg = "홈 IoT 컨트롤러 상태 정보 조회 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else {
                device = deviceMapper.getDeviceStauts(Collections.singletonList(serialNumber.getSerialNumber()));

                if(device == null) {
                    msg = "홈 IoT 컨트롤러 상태 정보 조회 실패";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                } else if(modelCode.equals("ESCeco13S") || modelCode.equals("DCR-91/WF")) {
                    resultMap.put("deviceStatus", "01");
                    resultMap.put("modelCategoryCode", "01");
                    for (DeviceStatusInfo.Device value : device) {
                        resultMap.put("rKey", value.getRKey());
                        resultMap.put("powr", value.getPowr());
                        resultMap.put("opMd", value.getOpMd());
                        resultMap.put("htTp", value.getHtTp());
                        resultMap.put("wtTp", value.getWtTp());
                        resultMap.put("hwTp", value.getHwTp());
                        resultMap.put("ftMd", value.getFtMd());
                        resultMap.put("bCdt", value.getBCdt());
                        resultMap.put("chTp", value.getChTp());
                        resultMap.put("cwTp", value.getCwTp());
                        resultMap.put("mfDt", value.getMfDt());
                        resultMap.put("type24h", common.readCon(value.getH24(), "serviceMd"));
                        resultMap.put("slCd", value.getSlCd());
                        resultMap.put("hwSt", value.getHwSt());
                        resultMap.put("fcLc", value.getFcLc());

                        ConcurrentHashMap<String, ConcurrentHashMap<String, String>> rscfMap = new ConcurrentHashMap<>();
                        ConcurrentHashMap<String, String> eleMap = new ConcurrentHashMap<>();

                        eleMap.put("24h", value.getH24());
                        eleMap.put("12h", value.getH12());
                        eleMap.put("7wk", value.getWk7());

                        if (value.getFwh() == null) {
                            eleMap.put("fwt", "null");
                        } else {
                            eleMap.put("fwh", value.getFwh());
                        }

                        rscfMap.put("rsCf", eleMap);
                        resultMap.put("rsCf", JSON.toJson(rscfMap));
                    }
                } else if(modelCode.equals("DCR-47/WF")){
                    resultMap.put("deviceStatus", "01");
                    resultMap.put("modelCategoryCode", "07");
                    for (DeviceStatusInfo.Device value : device) {
                        resultMap.put("rKey", value.getRKey());
                        resultMap.put("powr", value.getPowr());
                        resultMap.put("opMd", value.getOpMd());
                        resultMap.put("vtSp", value.getVtSp());
                        resultMap.put("inAq", value.getInAq());
                        resultMap.put("mfDt", value.getMfDt());

                        ConcurrentHashMap<String, ConcurrentHashMap<String, String>> rscfMap = new ConcurrentHashMap<String, ConcurrentHashMap<String, String>>() {{

                            // 내부 맵 생성 및 초기화
                            ConcurrentHashMap<String, String> eleMap = new ConcurrentHashMap<>();
                            eleMap.put("rsSl", value.getRsSl());
                            eleMap.put("rsPw", value.getRsPw());

                            // 외부 맵에 내부 맵 추가
                            put("rsCf", eleMap);
                        }};
                        resultMap.put("rsCf", JSON.toJson(rscfMap));
                    }
                }
            }

            msg = "홈 IoT 컨트롤러 상태 정보 조회 성공";
            result.setResult(ApiResponse.ResponseType.HTTP_200, msg);

            redisCommand.deleteValues(uuId);

            result.setDeviceStatusInfo(resultMap);
            log.info("result: " + result);
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
        String userId;
        String deviceId = params.getDeviceId();
        String modelCode = params.getModelCode();
        String sleepCode = null;

        AuthServerDTO userNickname;
        AuthServerDTO household;
        AuthServerDTO firstDeviceUser;

        if(params.getModeCode().equals("06")) sleepCode = params.getSleepCode();

        String responseMessage = null;
        String redisValue;
        MobiusResponse response;
        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        DeviceStatusInfo.Device deviceInfo = new DeviceStatusInfo.Device();
        try  {

            firstDeviceUser = memberMapper.getFirstDeviceUser(deviceId);
            userId = firstDeviceUser.getUserId();

            AuthServerDTO device = deviceMapper.getSingleSerialNumberBydeviceId(params.getDeviceId());
            if (device == null) {
                msg = "기기정보가 없습니다.";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1009, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            serialNumber = device.getSerialNumber();
            if(serialNumber == null) {
                msg = "모드변경 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1009, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }
            modeChange.setUserId(params.getUserId());
            modeChange.setDeviceId(params.getDeviceId());
            modeChange.setControlAuthKey(params.getControlAuthKey());
            modeChange.setModelCode(params.getModelCode());
            modeChange.setModeCode(modeCode);

            if(modeCode.equals("06")) modeChange.setSleepCode(sleepCode);

            modeChange.setFunctionId("opMd");

            modeChange.setUuId(common.getTransactionId());
            redisValue = params.getUserId() + "," + modeChange.getFunctionId();
            redisCommand.setValues(modeChange.getUuId(), redisValue);
            response = mobiusService.createCin(common.stringToHex("    " + serialNumber), userId, JSON.toJson(modeChange));

            if(!response.getResponseCode().equals("201")){
                msg = "중계서버 오류";
                result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            try {
                // 메시징 시스템을 통해 응답 메시지 대기
                responseMessage = gwMessagingSystem.waitForResponse("opMd" + modeChange.getUuId(), TIME_OUT, TimeUnit.SECONDS);
                if(responseMessage == null) stringObject = "T";
                else {
                    if (responseMessage.equals("0")) stringObject = "Y";
                    else stringObject = "N";
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                log.error("", e);
            }

            gwMessagingSystem.removeMessageQueue("opMd" + modeChange.getUuId());
            redisCommand.deleteValues(modeChange.getUuId());

            if(memberMapper.updatePushToken(params) <= 0) log.info("구글 FCM TOKEN 갱신 실패.");

            if(responseMessage != null && responseMessage.equals("2")){
                conMap.put("body", "RemoteController WIFI ERROR");
                msg = "RC WIFI 오류";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1014, msg);
            } else {
                if(stringObject.equals("Y")) {
                    conMap.put("body", "Mode Change OK");
                    msg = "모드변경 성공";
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                }
                else if(stringObject.equals("N")) {
                    conMap.put("body", "Mode Change FAIL");
                    msg = "모드변경 실패";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1008, msg);
                }
                else {
                    conMap.put("body", "Service TIME-OUT");
                    msg = "응답이 없거나 시간 초과";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1015, msg);
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
                    conMap.put("modelCode", modelCode);
                    conMap.put("targetToken", memberMapper.getPushTokenByUserId(userIds.get(i).getUserId()).getPushToken());
                    conMap.put("userNickname", userNickname.getUserNickname());
                    conMap.put("deviceNick", common.returnDeviceNickname(deviceId));
                    conMap.put("title", "opMd");
                    conMap.put("id", "Mode Change ID");
                    conMap.put("isEnd", "false");

                    String jsonString = objectMapper.writeValueAsString(conMap);

                    if(!mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString).getResponseCode().equals("201"))
                        log.info("PUSH 메세지 전송 오류");

                }

                deviceInfo.setOpMd(modeCode);
                deviceInfo.setDeviceId(deviceId);
                if(params.getModeCode().equals("06")) deviceInfo.setSlCd(sleepCode);

                deviceMapper.updateDeviceStatusFromApplication(deviceInfo);

                switch (modeCode){
                    case "01":
                        params.setControlCodeName("실내난방");
                        break;
                    case "02":
                        params.setControlCodeName("온돌난방");
                        break;
                    case "03":
                        params.setControlCodeName("외출");
                        break;
                    case "04":
                        params.setControlCodeName("자동");
                        break;
                    case "05":
                        params.setControlCodeName("절약난방");
                        break;
                    case "06":
                        params.setControlCodeName("취침");
                        break;
                    case "07":
                        params.setControlCodeName("온수전용");
                        break;
                    case "08":
                        params.setControlCodeName("온수-빠른온수");
                        break;
                    case "09":
                        params.setControlCodeName("귀가");
                        break;
                    case "10":
                        params.setControlCodeName("예약난방-24시간");
                        break;
                    case "11":
                        params.setControlCodeName("예약난방-반복(12시간)");
                        break;
                    case "12":
                        params.setControlCodeName("예약난방-주간");
                        break;
                    // TODO: 환기도 추가
                    default:
                        params.setControlCodeName("NONE_MODE");
                        break;
                }

                params.setCodeType("0");
                params.setCommandId("ModeChange");
                params.setControlCode(modeCode);
                params.setCommandFlow("0");
                params.setDeviceId(deviceId);
                params.setUserId(params.getUserId());
                if(memberMapper.insertCommandHistory(params) <= 0) log.info("DB_ERROR 잠시 후 다시 시도 해주십시오.");

                params.setPushTitle("기기제어");
                params.setPushContent("모드변경");
                params.setDeviceId(deviceId);
                params.setDeviceType(common.getModelCode(modelCode));
                if(memberMapper.insertPushHistory(params) <= 0) log.info("PUSH HISTORY INSERT ERROR");
            }

            log.info("result: " + result);
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
        String responseMessage = null;
        String userId;
        String deviceId = params.getDeviceId();
        String redisValue;
        MobiusResponse response;
        String serialNumber;
        AuthServerDTO userNickname;
        AuthServerDTO household;
        AuthServerDTO firstDeviceUser;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        DeviceStatusInfo.Device deviceInfo = new DeviceStatusInfo.Device();
        try {

            firstDeviceUser = memberMapper.getFirstDeviceUser(deviceId);
            userId = firstDeviceUser.getUserId();

            AuthServerDTO device = deviceMapper.getSingleSerialNumberBydeviceId(params.getDeviceId());
            if (device == null) {
                msg = "기기정보가 없습니다.";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1009, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            serialNumber = device.getSerialNumber();
            if(serialNumber == null) {
                msg = "실내온도 설정 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1009, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            temperatureSet.setUserId(params.getUserId());
            temperatureSet.setDeviceId(params.getDeviceId());
            temperatureSet.setControlAuthKey(params.getControlAuthKey());
            temperatureSet.setTemperature(params.getTemperture());
            temperatureSet.setFunctionId("htTp");
            temperatureSet.setUuId(common.getTransactionId());

            redisValue = params.getUserId() + "," + temperatureSet.getFunctionId();
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
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                log.error("", e);
            }

            gwMessagingSystem.removeMessageQueue("htTp" + temperatureSet.getUuId());
            redisCommand.deleteValues(temperatureSet.getUuId());

            if(memberMapper.updatePushToken(params) <= 0) log.info("구글 FCM TOKEN 갱신 실패.");

            if(responseMessage != null && responseMessage.equals("2")){
                conMap.put("body", "RemoteController WIFI ERROR");
                msg = "RC WIFI 오류";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1014, msg);
            } else {
                if(stringObject.equals("Y")) {
                    conMap.put("body", "TemperatureSet OK");
                    msg = "실내온도 설정 성공";
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                }
                else if(stringObject.equals("N")) {
                    conMap.put("body", "TemperatureSet OK");
                    msg = "실내온도 설정 실패";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1008, msg);
                }
                else {
                    conMap.put("body", "Service TIME-OUT");
                    msg = "응답이 없거나 시간 초과";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1015, msg);
                }

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
                    conMap.put("title", "htTp");
                    conMap.put("id", "TemperatureSet ID");
                    conMap.put("isEnd", "false");

                    String jsonString = objectMapper.writeValueAsString(conMap);

                    if(!mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString).getResponseCode().equals("201"))
                        log.info("PUSH 메세지 전송 오류");
                }

                deviceInfo.setHtTp(params.getTemperture());
                deviceInfo.setDeviceId(params.getDeviceId());
                deviceMapper.updateDeviceStatusFromApplication(deviceInfo);

                params.setCodeType("1");
                params.setCommandId("TemperatureSet");
                params.setControlCode("hwTp");
                params.setControlCodeName("실내 온도 설정");
                params.setCommandFlow("0");
                params.setDeviceId(deviceId);
                params.setUserId(params.getUserId());
                if(memberMapper.insertCommandHistory(params) <= 0) log.info("DB_ERROR 잠시 후 다시 시도 해주십시오.");

                params.setPushTitle("기기제어");
                params.setPushContent("실내온도 설정");
                params.setDeviceId(deviceId);
                params.setDeviceType("01");
                if(memberMapper.insertPushHistory(params) <= 0) log.info("PUSH HISTORY INSERT ERROR");
            }
            log.info("result: " + result);
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
        String userId;
        String deviceId = params.getDeviceId();
        String redisValue;
        String responseMessage = null;
        MobiusResponse response;
        String serialNumber;
        AuthServerDTO userNickname;
        AuthServerDTO household;
        AuthServerDTO firstDeviceUser;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        DeviceStatusInfo.Device deviceInfo = new DeviceStatusInfo.Device();
        try {

            firstDeviceUser = memberMapper.getFirstDeviceUser(deviceId);
            userId = firstDeviceUser.getUserId();

            AuthServerDTO device = deviceMapper.getSingleSerialNumberBydeviceId(params.getDeviceId());
            if (device == null) {
                msg = "기기정보가 없습니다.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            serialNumber = device.getSerialNumber();
            if(serialNumber == null) {
                msg = "난방수온도 설정 실패";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            boiledWaterTempertureSet.setUserId(params.getUserId());
            boiledWaterTempertureSet.setDeviceId(params.getDeviceId());
            boiledWaterTempertureSet.setControlAuthKey(params.getControlAuthKey());
            boiledWaterTempertureSet.setTemperature(params.getTemperture());
            boiledWaterTempertureSet.setFunctionId("wtTp");
            boiledWaterTempertureSet.setUuId(common.getTransactionId());

            redisValue = params.getUserId() + "," + boiledWaterTempertureSet.getFunctionId();
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
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                log.error("", e);
            }

            gwMessagingSystem.removeMessageQueue("wtTp" + boiledWaterTempertureSet.getUuId());
            redisCommand.deleteValues(boiledWaterTempertureSet.getUuId());

            if(memberMapper.updatePushToken(params) <= 0) log.info("구글 FCM TOKEN 갱신 실패.");

            if(responseMessage != null && responseMessage.equals("2")){
                conMap.put("body", "RemoteController WIFI ERROR");
                msg = "RC WIFI 오류";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1014, msg);
            } else {
                if(stringObject.equals("Y")) {
                    conMap.put("body", "BoiledWaterTempertureSet OK");
                    msg = "난방수온도 설정 성공";
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                }
                else if(stringObject.equals("N")) {
                    conMap.put("body", "BoiledWaterTempertureSet FAIL");
                    msg = "난방수온도 설정 실패";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1008, msg);
                }
                else {
                    conMap.put("body", "Service TIME-OUT ");
                    msg = "응답이 없거나 시간 초과";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1015, msg);
                }

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
                    conMap.put("title", "wtTp");
                    conMap.put("id", "BoiledWaterTempertureSet ID");
                    conMap.put("isEnd", "false");

                    String jsonString = objectMapper.writeValueAsString(conMap);
                    log.info("jsonString: " + jsonString);

                    if(!mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString).getResponseCode().equals("201"))
                        log.info("PUSH 메세지 전송 오류");
                }

                deviceInfo.setWtTp(params.getTemperture());
                deviceInfo.setDeviceId(deviceId);
                deviceMapper.updateDeviceStatusFromApplication(deviceInfo);

                params.setCodeType("1");
                params.setCommandId("BoiledWaterTempertureSet");
                params.setControlCode("wtTp");
                params.setControlCodeName("난방수 온도 설정");
                params.setCommandFlow("0");
                params.setDeviceId(deviceId);
                params.setUserId(params.getUserId());
                if(memberMapper.insertCommandHistory(params) <= 0) log.info("DB_ERROR 잠시 후 다시 시도 해주십시오.");

                params.setPushTitle("기기제어");
                params.setPushContent("난방수온도 설정");
                params.setDeviceId(deviceId);
                params.setDeviceType("01");
                if(memberMapper.insertPushHistory(params) <= 0) log.info("PUSH HISTORY INSERT ERROR");
            }

            log.info("result: " + result);
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
        String userId;
        String deviceId = params.getDeviceId();
        String redisValue;
        String responseMessage = null;
        MobiusResponse response;
        String serialNumber;
        AuthServerDTO household;
        AuthServerDTO userNickname;
        AuthServerDTO firstDeviceUser;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        DeviceStatusInfo.Device deviceInfo = new DeviceStatusInfo.Device();
        try {

            firstDeviceUser = memberMapper.getFirstDeviceUser(deviceId);
            userId = firstDeviceUser.getUserId();

            AuthServerDTO device = deviceMapper.getSingleSerialNumberBydeviceId(params.getDeviceId());
            if (device == null) {
                msg = "기기정보가 없습니다.";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1009, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            serialNumber = device.getSerialNumber();
            if(serialNumber == null) {
                msg = "온수온도 설정 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1009, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }
            waterTempertureSet.setUserId(params.getUserId());
            waterTempertureSet.setDeviceId(params.getDeviceId());
            waterTempertureSet.setControlAuthKey(params.getControlAuthKey());
            waterTempertureSet.setTemperature(params.getTemperture());
            waterTempertureSet.setFunctionId("hwTp");
            waterTempertureSet.setUuId(common.getTransactionId());

            redisValue = params.getUserId() + "," + waterTempertureSet.getFunctionId();
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
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                log.error("", e);
            }

            gwMessagingSystem.removeMessageQueue("hwTp" + waterTempertureSet.getUuId());
            redisCommand.deleteValues(waterTempertureSet.getUuId());

            if(memberMapper.updatePushToken(params) <= 0) log.info("구글 FCM TOKEN 갱신 실패.");

            if(responseMessage != null && responseMessage.equals("2")){
                conMap.put("body", "RemoteController WIFI ERROR");
                msg = "RC WIFI 오류";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1014, msg);
            } else {
                if(stringObject.equals("Y")) {
                    conMap.put("body", "WaterTempertureSet OK");
                    msg = "온수온도 설정 성공";
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                }
                else if(stringObject.equals("N")) {
                    conMap.put("body", "WaterTempertureSet FAIL");
                    msg = "온수온도 설정 실패";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1008, msg);
                }
                else {
                    conMap.put("body", "Service TIME-OUT");
                    msg = "응답이 없거나 시간 초과";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1015, msg);
                }

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
                    conMap.put("title", "hwTp");
                    conMap.put("id", "WaterTempertureSet ID");
                    conMap.put("isEnd", "false");

                    String jsonString = objectMapper.writeValueAsString(conMap);

                    if(!mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString).getResponseCode().equals("201"))
                        log.info("PUSH 메세지 전송 오류");

                }

                deviceInfo.setHwTp(params.getTemperture());
                deviceInfo.setDeviceId(deviceId);
                deviceMapper.updateDeviceStatusFromApplication(deviceInfo);

                params.setCodeType("1");
                params.setCommandId("WaterTempertureSet");
                params.setControlCode("hwTp");
                params.setControlCodeName("온수 온도 설정");
                params.setCommandFlow("0");
                params.setDeviceId(deviceId);
                params.setUserId(params.getUserId());
                if(memberMapper.insertCommandHistory(params) <= 0) log.info("DB_ERROR 잠시 후 다시 시도 해주십시오.");

                params.setPushTitle("기기제어");
                params.setPushContent("온수온도 설정");
                params.setDeviceId(deviceId);
                params.setDeviceType("01");
                if(memberMapper.insertPushHistory(params) <= 0) log.info("PUSH HISTORY INSERT ERROR");
            }

            log.info("result: " + result);
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
        String userId;
        String deviceId = params.getDeviceId();
        String redisValue;
        MobiusResponse response;
        String responseMessage = null;
        String serialNumber;
        AuthServerDTO userNickname;
        AuthServerDTO household;
        AuthServerDTO firstDeviceUser;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        DeviceStatusInfo.Device deviceInfo = new DeviceStatusInfo.Device();
        try {

            firstDeviceUser = memberMapper.getFirstDeviceUser(deviceId);
            userId = firstDeviceUser.getUserId();

            AuthServerDTO device = deviceMapper.getSingleSerialNumberBydeviceId(params.getDeviceId());
            if (device == null) {
                msg = "기기정보가 없습니다.";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1009, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            serialNumber = device.getSerialNumber();
            if(serialNumber == null) {
                msg = "빠른온수 설정 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1009, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            fastHotWaterSet.setUserId(params.getUserId());
            fastHotWaterSet.setDeviceId(params.getDeviceId());
            fastHotWaterSet.setControlAuthKey(params.getControlAuthKey());
            fastHotWaterSet.setFtMdSet(params.getModeCode());
            fastHotWaterSet.setFunctionId("ftMd");
            fastHotWaterSet.setUuId(common.getTransactionId());

            redisValue = params.getUserId() + "," + fastHotWaterSet.getFunctionId();
            redisCommand.setValues(fastHotWaterSet.getUuId(), redisValue);
            response = mobiusResponse = mobiusService.createCin(common.stringToHex("    " + serialNumber), userId, JSON.toJson(fastHotWaterSet));

            if(!response.getResponseCode().equals("201")){
                msg = "중계서버 오류";
                result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            try {
                // 메시징 시스템을 통해 응답 메시지 대기
                responseMessage = gwMessagingSystem.waitForResponse("ftMd" + fastHotWaterSet.getUuId(), TIME_OUT, TimeUnit.SECONDS);
                if(responseMessage == null) stringObject = "T";
                else {
                    if (responseMessage.equals("0")) stringObject = "Y";
                    else stringObject = "N";
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                log.error("", e);
            }

            gwMessagingSystem.removeMessageQueue("ftMd" + fastHotWaterSet.getUuId());
            redisCommand.deleteValues(fastHotWaterSet.getUuId());

            if(memberMapper.updatePushToken(params) <= 0) log.info("구글 FCM TOKEN 갱신 실패.");

            if(responseMessage != null && responseMessage.equals("2")){
                conMap.put("body", "RemoteController WIFI ERROR");
                msg = "RC WIFI 오류";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1014, msg);
            } else {
                if(stringObject.equals("Y")) {
                    conMap.put("body", "FastHotWaterSet OK");
                    msg = "빠른온수 설정 성공";
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                }
                else if(stringObject.equals("N")) {
                    conMap.put("body", "FastHotWaterSet FAIL");
                    msg = "빠른온수 설정 실패";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1008, msg);
                }
                else {
                    conMap.put("body", "Service TIME-OUT");
                    msg = "응답이 없거나 시간 초과";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1015, msg);
                }

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
                    conMap.put("title", "ftMd");
                    conMap.put("id", "FastHotWaterSet ID");
                    conMap.put("isEnd", "false");

                    String jsonString = objectMapper.writeValueAsString(conMap);

                    if(!mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString).getResponseCode().equals("201"))
                        log.info("PUSH 메세지 전송 오류");
                }

                deviceInfo.setFtMd(params.getModeCode());
                deviceInfo.setDeviceId(deviceId);
                deviceMapper.updateDeviceStatusFromApplication(deviceInfo);

                params.setCodeType("1");
                params.setCommandId("FastHotWaterSet");
                params.setControlCode("ftMd");
                params.setControlCodeName("빠른 온수 설정");
                params.setCommandFlow("0");
                params.setDeviceId(deviceId);
                params.setUserId(params.getUserId());
                if(memberMapper.insertCommandHistory(params) <= 0) log.info("DB_ERROR 잠시 후 다시 시도 해주십시오.");

                params.setPushTitle("기기제어");
                params.setPushContent("빠른온수 설정");
                params.setDeviceId(deviceId);
                params.setDeviceType("01");
                if(memberMapper.insertPushHistory(params) <= 0) log.info("PUSH HISTORY INSERT ERROR");
            }
            log.info("result: " + result);
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
        String userId;
        String deviceId = params.getDeviceId();
        AuthServerDTO userNickname;
        AuthServerDTO household;
        AuthServerDTO firstDeviceUser;

        String redisValue;
        MobiusResponse response;
        String responseMessage = null;
        String serialNumber;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        DeviceStatusInfo.Device deviceInfo = new DeviceStatusInfo.Device();
        try {

            firstDeviceUser = memberMapper.getFirstDeviceUser(deviceId);
            userId = firstDeviceUser.getUserId();

            AuthServerDTO device = deviceMapper.getSingleSerialNumberBydeviceId(params.getDeviceId());
            if (device == null) {
                msg = "기기정보가 없습니다.";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            serialNumber = device.getSerialNumber();
            if(serialNumber == null) {
                msg = "잠금 모드 설정 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            lockSet.setUserId(params.getUserId());
            lockSet.setDeviceId(params.getDeviceId());
            lockSet.setControlAuthKey(params.getControlAuthKey());
            lockSet.setLockSet(params.getLockSet());
            lockSet.setFunctionId("fcLc");
            lockSet.setUuId(common.getTransactionId());


            redisValue = params.getUserId() + "," + lockSet.getFunctionId();
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
                    if(responseMessage.equals("0")) stringObject = "Y";
                    else stringObject = "N";
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                log.error("", e);
            }

            gwMessagingSystem.removeMessageQueue("fcLc" + lockSet.getUuId());
            redisCommand.deleteValues(lockSet.getUuId());

            if(memberMapper.updatePushToken(params) <= 0) log.info("구글 FCM TOKEN 갱신 실패.");

            if(responseMessage != null && responseMessage.equals("2")){
                conMap.put("body", "RemoteController WIFI ERROR");
                msg = "RC WIFI 오류";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1014, msg);
            } else {
                if(stringObject.equals("Y")) {
                    conMap.put("body", "LockSet OK");
                    msg = "잠금 모드 설정 성공";
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                }
                else if(stringObject.equals("N")) {
                    conMap.put("body", "LockSet FAIL");
                    msg = "잠금 모드 설정 실패";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1008, msg);
                }
                else {
                    conMap.put("body", "Service TIME-OUT");
                    msg = "응답이 없거나 시간 초과";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1015, msg);
                }
                
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
                    conMap.put("title", "fcLc");
                    conMap.put("id", "LockSet ID");
                    conMap.put("isEnd", "false");
                    String jsonString = objectMapper.writeValueAsString(conMap);
                    if(!mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString).getResponseCode().equals("201")) log.info("PUSH 메세지 전송 오류");
                }

                deviceInfo.setFcLc(params.getLockSet());
                deviceInfo.setDeviceId(deviceId);
                deviceMapper.updateDeviceStatusFromApplication(deviceInfo);
            }

            log.info("result: " + result);
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
        String stringObject = "N";
        String msg;

        String userId = params.getUserId();
        String uuId = common.getTransactionId();
        List<AuthServerDTO> groupInfo;

        List<String> serialNumberList;
        List<String> rKeyList;
        List<String> deviceIdList;
        List<String> deviceNicknameList;
        List<String> groupIdxList;
        List<String> latitudeList;
        List<String> longitudeList;
        List<String> regSortList;
        List<String> modelCodeList;
        List<String> tmpKeyListList;

        List<Map<String, String>> appResponse = new ArrayList<>();

        List<AuthServerDTO> controlAuthKeyByUserIdResult;
        List<AuthServerDTO> deviceNicknameAndDeviceLocNicknameResult;
        List<AuthServerDTO> multiSerialNumberBydeviceIdResult;
        List<AuthServerDTO> groupIdxListByUserIdResult;
        List<DeviceStatusInfo.Device> devicesStatusInfo;
        List<DeviceStatusInfo.Device> activeStatusInfo;
        try {

            // 1. 사용자 그룹 정보 가져오기
            groupInfo = memberMapper.getGroupIdByUserId(userId);
            if (groupInfo == null || groupInfo.isEmpty()) {
                msg = "그룹 정보가 없습니다.";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1009, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            // 2. 여러 그룹 ID에 대해 각 기기 정보를 조회
            for (AuthServerDTO group : groupInfo) {
                controlAuthKeyByUserIdResult = deviceMapper.getControlAuthKeyByUserId(group.getGroupId());
                if (controlAuthKeyByUserIdResult == null || controlAuthKeyByUserIdResult.isEmpty()) {
                    msg = "기기정보가 없습니다.";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1009, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                groupIdxListByUserIdResult = memberMapper.getGroupIdxByUserId(userId);

                deviceNicknameAndDeviceLocNicknameResult = deviceMapper.getDeviceNicknameAndDeviceLocNickname(controlAuthKeyByUserIdResult);
                multiSerialNumberBydeviceIdResult = deviceMapper.getMultiSerialNumberBydeviceId(controlAuthKeyByUserIdResult);

                if (deviceNicknameAndDeviceLocNicknameResult == null || multiSerialNumberBydeviceIdResult == null) {
                    msg = "기기정보가 없습니다.";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1009, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                log.info("deviceNicknameAndDeviceLocNicknameResult: " + deviceNicknameAndDeviceLocNicknameResult);
                log.info("multiSerialNumberBydeviceIdResult: " + multiSerialNumberBydeviceIdResult);

                // 3. 각 데이터 리스트로 변환
                rKeyList = Common.extractJson(controlAuthKeyByUserIdResult.toString(), "controlAuthKey");
                deviceIdList = Common.extractJson(controlAuthKeyByUserIdResult.toString(), "deviceId");
                deviceNicknameList = Common.extractJson(deviceNicknameAndDeviceLocNicknameResult.toString(), "deviceNickname");
                groupIdxList = Common.extractJson(groupIdxListByUserIdResult.toString(), "groupIdx");
                serialNumberList = Common.extractJson(multiSerialNumberBydeviceIdResult.toString(), "serialNumber");
                regSortList = Common.extractJson(deviceNicknameAndDeviceLocNicknameResult.toString(), "regSort");
                latitudeList = Common.extractJson(deviceNicknameAndDeviceLocNicknameResult.toString(), "latitude");
                longitudeList = Common.extractJson(deviceNicknameAndDeviceLocNicknameResult.toString(), "longitude");
                modelCodeList = Common.extractJson(multiSerialNumberBydeviceIdResult.toString(), "modelCode");
                tmpKeyListList = Common.extractJson(deviceNicknameAndDeviceLocNicknameResult.toString(), "tmpRegistKey");

                log.info("rKeyList: " + rKeyList);
                log.info("deviceIdList: " + deviceIdList);
                log.info("deviceNicknameList: " + deviceNicknameList);
                log.info("groupIdxList: " + groupIdxList);
                log.info("serialNumberList: " + serialNumberList);
                log.info("regSortList: " + regSortList);
                log.info("latitudeList: " + latitudeList);
                log.info("longitudeList: " + longitudeList);
                log.info("modelCodeList: " + modelCodeList);
                log.info("tmpKeyListList: " + tmpKeyListList);

                // 4. 기기 상태 정보 및 활성화 상태 조회
                devicesStatusInfo = deviceMapper.getDeviceStauts(serialNumberList);
                activeStatusInfo = deviceMapper.getActiveStauts(serialNumberList);

                if (devicesStatusInfo == null || devicesStatusInfo.isEmpty()) {
                    msg = "등록된 R/C가 없습니다";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1009, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                log.info("devicesStatusInfo: " + devicesStatusInfo);
                log.info("activeStatusInfo: " + activeStatusInfo);

                if(deviceNicknameList != null &&
                        groupIdxList != null &&
                        regSortList != null &&
                        serialNumberList != null &&
                        latitudeList != null &&
                        longitudeList != null &&
                        modelCodeList != null &&
                        tmpKeyListList != null &&
                        rKeyList != null &&
                        deviceIdList != null){

                    // 5. 데이터 매핑
                    for (int i = 0; i < rKeyList.size(); ++i) {
                        Map<String, String> data = new HashMap<>();
                        data.put("rKey", rKeyList.get(i));
                        data.put("deviceNickname", deviceNicknameList.get(i));
                        data.put("groupIdx", groupIdxList.get(i));
                        data.put("regSort", regSortList.get(i));
                        data.put("deviceId", deviceIdList.get(i));
                        data.put("latitude", latitudeList.get(i));
                        data.put("longitude", longitudeList.get(i));
                        data.put("controlAuthKey", rKeyList.get(i));
                        data.put("tmpRegistKey", tmpKeyListList.get(i));
                        data.put("deviceStatus", "1");

                        // 기기 상태 정보 추가
                        DeviceStatusInfo.Device statusInfo = devicesStatusInfo.get(i);
                        data.put("powr", statusInfo.getPowr());
                        data.put("opMd", statusInfo.getOpMd());
                        data.put("htTp", statusInfo.getHtTp());
                        data.put("wtTp", statusInfo.getWtTp());
                        data.put("hwTp", statusInfo.getHwTp());
                        data.put("ftMd", statusInfo.getFtMd());
                        data.put("chTp", statusInfo.getChTp());
                        data.put("mfDt", statusInfo.getMfDt());
                        data.put("hwSt", statusInfo.getHwSt());
                        data.put("fcLc", statusInfo.getFcLc());
                        data.put("blCf", statusInfo.getBlCf());
                        data.put("type24h", common.readCon(statusInfo.getH24(), "serviceMd"));
                        data.put("slCd", statusInfo.getSlCd());
                        data.put("vtSp", statusInfo.getVtSp());
                        data.put("inAq", statusInfo.getInAq());

                        // 활성화 상태 정보 추가 (모델에 따라 분기 처리)
                        if (!activeStatusInfo.isEmpty() && modelCodeList.get(i).equals("DCR-91/WF")) {
                            DeviceStatusInfo.Device activeInfo = activeStatusInfo.get(i);
                            data.put("ftMdAcTv", activeInfo.getFtMd());
                            data.put("fcLcAcTv", activeInfo.getFcLc());
                        } else if (!activeStatusInfo.isEmpty() && modelCodeList.get(i).equals("DCR-47/WF")) {
                            DeviceStatusInfo.Device activeInfo = activeStatusInfo.get(i);
                            data.put("pastAcTv", activeInfo.getPast());
                            data.put("inDrAcTv", activeInfo.getInDr());
                            data.put("inClAcTv", activeInfo.getInCl());
                            data.put("ecStAcTv", activeInfo.getEcSt());
                        }
                        appResponse.add(data);
                    }
                    stringObject = "Y";
                }
            }

            if(stringObject.equals("Y")) {
                msg = "홈 IoT 컨트롤러 상태 정보 조회 – 홈 화면 성공";
                result.setHomeViewValue(appResponse);
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            }

            if(stringObject.equals("N")) {
                msg = "홈 IoT 컨트롤러 상태 정보 조회 – 홈 화면 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
            }
            redisCommand.deleteValues(uuId);
            log.info("result: " + result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /** 홈 IoT 컨트롤러 정보 조회-단건 */
    @Override
    public HashMap<String, Object> doDeviceInfoSearch(AuthServerDTO params) throws CustomException {

        String rtCode;
        String msg;
        AuthServerDTO resultDto;
        HashMap<String, Object> result = new HashMap<>();

        try {

        resultDto = deviceMapper.getDeviceInfoSearch(params);

        if(resultDto == null){
            rtCode = "400";
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

            log.info("result: " + result);
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
                result.setResult(ApiResponse.ResponseType.CUSTOM_1009, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
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
                result.setResult(ApiResponse.ResponseType.CUSTOM_1009, msg);
            }

            log.info("result: " + result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /**	홈 IoT 정보 조회 - 리스트  */
    @Override
    public ResponseEntity<?> doDeviceInfoSearchList(AuthServerDTO params) throws CustomException {

        /*
        * 1. UserId 로 DeviceId 취득 getUserByDeviceId (등록된 모든 Device ID)
        * 2. DeviceId로 필요한 Data를 쿼리 (TBT_OPR_DEVICE_REGIST, TBR_IOT_DEVICE)
        * */

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject = "N";
        String msg = null;

        List<Map<String, String>> appResponse = new ArrayList<>();
        List<AuthServerDTO> deviceInfoList;

        AuthServerDTO householdStatus;

        try {

            // TODO: 만약 Household 여부가 N인 경우에는 세대주의 USERID 사용
            householdStatus = memberMapper.getHouseholdByUserId(params.getUserId());
            if(householdStatus.getHouseholder().equals("N")) params.setUserId(householdStatus.getGroupId());

            deviceInfoList = deviceMapper.getDeviceInfoSearchList(params);

            if(!deviceInfoList.isEmpty()){
                for (AuthServerDTO authServerDTO : deviceInfoList) {
                    Map<String, String> data = new HashMap<>();
                    data.put("modelCode", authServerDTO.getModelCode());
                    data.put("deviceNickname", authServerDTO.getDeviceNickname());
                    data.put("addrNickname", authServerDTO.getAddrNickname());
                    data.put("zipCode", authServerDTO.getZipCode());
                    data.put("oldAddr", authServerDTO.getOldAddr());
                    data.put("newAddr", authServerDTO.getNewAddr());
                    data.put("addrDetail", authServerDTO.getAddrDetail());
                    data.put("latitude", authServerDTO.getLatitude());
                    data.put("longitude", authServerDTO.getLongitude());
                    data.put("regSort", authServerDTO.getRegSort());
                    data.put("deviceId", authServerDTO.getDeviceId());
                    data.put("controlAuthKey", authServerDTO.getControlAuthKey());
                    data.put("tempKey", authServerDTO.getTmpRegistKey());
                    appResponse.add(data);
                }
                stringObject = "Y";
            }

            if(stringObject.equals("Y")) {
                msg = "홈 IoT 정보 조회 리스트 - 조회 성공";
                result.setHomeViewValue(appResponse);
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            }

            if(stringObject.equals("N")) {
                msg = "홈 IoT 정보 조회 리스트 - 조회 결과 없음";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1009, msg);
            }
            result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            log.info("result: " + result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /**	홈 IoT 컨트롤러 풍량 단수 설정  */
    @Override
    public ResponseEntity<?> doVentilationFanSpeedSet(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        VentilationFanSpeedSet fanSpeedSet = new VentilationFanSpeedSet();
        String stringObject;
        String msg;

        String userId;
        String deviceId = params.getDeviceId();
        String controlAuthKey = params.getControlAuthKey();
        String fanSpeed = params.getFanSpeed();
        String modelCode = params.getModelCode();

        String responseMessage = null;
        AuthServerDTO userNickname;
        AuthServerDTO household;
        AuthServerDTO firstDeviceUser;

        MobiusResponse response;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();

        DeviceStatusInfo.Device deviceInfo = new DeviceStatusInfo.Device();

        String redisValue;
        String serialNumber;

        try{

            firstDeviceUser = memberMapper.getFirstDeviceUser(deviceId);
            userId = firstDeviceUser.getUserId();

            fanSpeedSet.setUserId(params.getUserId());
            fanSpeedSet.setDeviceId(deviceId);
            fanSpeedSet.setControlAuthKey(controlAuthKey);
            fanSpeedSet.setModelCode(common.stringToHex(modelCode));
            fanSpeedSet.setFanSpeed(fanSpeed);
            fanSpeedSet.setFunctionId("vtSp");
            fanSpeedSet.setUuId(common.getTransactionId());

            redisValue = params.getUserId() + "," + "VentilationFanSpeedSet";
            redisCommand.setValues(fanSpeedSet.getUuId(), redisValue);

            AuthServerDTO device = deviceMapper.getSingleSerialNumberBydeviceId(deviceId);

            if (device == null) {
                msg = "기기정보가 없습니다.";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1009, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else serialNumber = device.getSerialNumber();

            if(serialNumber == null) {
                msg = "풍량 단수 설정 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1009, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else {
                stringObject = "Y";
                response = mobiusService.createCin(common.stringToHex("    " + serialNumber), userId, JSON.toJson(fanSpeedSet));
                if(!response.getResponseCode().equals("201")){
                    msg = "중계서버 오류";
                    result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                try {
                    // 메시징 시스템을 통해 응답 메시지 대기
                    gwMessagingSystem.printMessageQueues();
                    log.info("responseMessage: VentilationFanSpeedSet" + fanSpeedSet.getUuId());
                    responseMessage = gwMessagingSystem.waitForResponse("VentilationFanSpeedSet" + fanSpeedSet.getUuId(), TIME_OUT, TimeUnit.SECONDS);
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
            }

            gwMessagingSystem.removeMessageQueue("VentilationFanSpeedSet" + fanSpeedSet.getUuId());
            redisCommand.deleteValues(fanSpeedSet.getUuId());

            if(memberMapper.updatePushToken(params) <= 0) log.info("구글 FCM TOKEN 갱신 실패.");

            if(responseMessage != null && responseMessage.equals("2")){
                conMap.put("body", "RemoteController WIFI ERROR");
                msg = "RC WIFI 오류";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1014, msg);
            } else {
                if(stringObject.equals("Y")) {
                    conMap.put("body", "Device ON/OFF OK");
                    msg = "풍량 단수 설정 성공";
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                } else {
                    conMap.put("body", "Service TIME-OUT");
                    msg = "응답이 없거나 시간 초과";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1015, msg);
                }

                redisCommand.deleteValues(fanSpeedSet.getUuId());

                deviceInfo.setVtSp(fanSpeed);
                deviceInfo.setDeviceId(deviceId);
                deviceMapper.updateDeviceStatusFromApplication(deviceInfo);

                params.setCodeType("1");
                params.setCommandId("VentilationFanSpeedSet");
                params.setControlCode("vtSp");
                params.setControlCodeName("풍량 단수 설정");
                params.setCommandFlow("0");
                params.setDeviceId(deviceId);
                params.setUserId(params.getUserId());

                if(memberMapper.insertCommandHistory(params) <= 0) log.info("DB_ERROR 잠시 후 다시 시도 해주십시오.");

                params.setPushTitle("기기제어");
                params.setPushContent("풍량 단수 설정");
                params.setDeviceId(deviceId);
                params.setDeviceType("07");
                if(memberMapper.insertPushHistory(params) <= 0) log.info("PUSH HISTORY INSERT ERROR");

                household = memberMapper.getHouseholdByUserId(params.getUserId());
                params.setGroupId(household.getGroupId());
                List<AuthServerDTO> userIds = memberMapper.getUserIdsByDeviceId(params);
                List<AuthServerDTO> pushYnList = memberMapper.getPushYnStatusByUserIds(userIds);
                userNickname = memberMapper.getUserNickname(params.getUserId());
                userNickname.setUserNickname(common.stringToHex(userNickname.getUserNickname()));

                for(int i = 0; i < userIds.size(); ++i){
                    log.info("쿼리한 UserId: " + userIds.get(i).getUserId());

                    conMap.put("targetToken", memberMapper.getPushTokenByUserId(userIds.get(i).getUserId()).getPushToken());
                    conMap.put("title", "VentilationFanSpeedSet");
                    conMap.put("powr", params.getPowerStatus());
                    conMap.put("isEnd", "false");
                    conMap.put("userNickname", userNickname.getUserNickname());
                    conMap.put("deviceNick", common.returnDeviceNickname(deviceId));
                    conMap.put("pushYn", pushYnList.get(i).getFPushYn());
                    conMap.put("modelCode", modelCode);

                    String jsonString = objectMapper.writeValueAsString(conMap);
                    log.info("doPowerOnOff jsonString: " + jsonString);

                    if(!mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString).getResponseCode().equals("201")) log.info("PUSH 메세지 전송 오류");
                }
            }

            log.info("result: " + result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        }catch (Exception e){
            log.error("", e);
        }
        return null;
    }

    /**	홈 IoT 컨트롤러 활성/비활성 정보 요청  */
    @Override
    public ResponseEntity<?> doActiveStatus(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        ActiveStatus activeStatus = new ActiveStatus();
        String stringObject;
        String msg;
        String userId;
        String deviceId = params.getDeviceId();
        String controlAuthKey = params.getControlAuthKey();
        String serialNumber;
        String modelCode;
        String redisValue;
        String functionId = null;
        String responseMessage = null;

        AuthServerDTO device;
        AuthServerDTO firstDeviceUser;

        MobiusResponse response;

        try {
            modelCode = common.getModelCodeFromDeviceId(deviceId);

            firstDeviceUser = memberMapper.getFirstDeviceUser(deviceId);
            userId = firstDeviceUser.getUserId();

            activeStatus.setAccessToken(common.getTransactionId());
            activeStatus.setUserId(params.getUserId());
            activeStatus.setDeviceId(deviceId);
            activeStatus.setControlAuthKey(controlAuthKey);
            activeStatus.setUuId(activeStatus.getAccessToken());

            if(modelCode.equals(modelCodeMap.get("newModel")) || modelCode.equals(modelCodeMap.get("oldModel"))) functionId = "bAcTv";
            else if(modelCode.equals(modelCodeMap.get("ventilation"))) functionId = "vAcTv";

            activeStatus.setFunctionId(functionId);
            redisValue = params.getUserId() + "," + activeStatus.getFunctionId();
            redisCommand.setValues(activeStatus.getUuId(), redisValue);

            device = deviceMapper.getSingleSerialNumberBydeviceId(deviceId);

            if (device == null) {
                msg = "기기정보가 없습니다.";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1009, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else {
                serialNumber = device.getSerialNumber();
                stringObject = "Y";
                response = mobiusService.createCin(common.stringToHex("    " + serialNumber), userId, JSON.toJson(activeStatus));
                if (!response.getResponseCode().equals("201")) {
                    msg = "중계서버 오류";
                    result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }
            }

            try {
                // 메시징 시스템을 통해 응답 메시지 대기
                gwMessagingSystem.printMessageQueues();
                responseMessage = gwMessagingSystem.waitForResponse(activeStatus.getFunctionId() + activeStatus.getUuId(), TIME_OUT, TimeUnit.SECONDS);
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

            gwMessagingSystem.removeMessageQueue(activeStatus.getFunctionId() + activeStatus.getUuId());
            redisCommand.deleteValues(activeStatus.getUuId());

            if(responseMessage != null && responseMessage.equals("2")){
                msg = "RC WIFI 오류";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1014, msg);
            } else {
                if (stringObject.equals("Y")) {
                    msg = "홈 IoT 컨트롤러 활성/비활성 정보 요청 성공";
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                    result.setTestVariable(responseMessage);
                } else {
                    msg = "응답이 없거나 시간 초과";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1015, msg);
                }
            }
            log.info("result: " + result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
        }
        return null;
    }
}
