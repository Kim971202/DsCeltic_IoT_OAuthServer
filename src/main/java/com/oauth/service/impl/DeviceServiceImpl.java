package com.oauth.service.impl;

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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@Transactional(rollbackFor = { Exception.class, CustomException.class })
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
    public ResponseEntity<?> doPowerOnOff(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        PowerOnOff powerOnOff = new PowerOnOff();

        String stringObject;
        String msg;
        String userId;
        String deviceId = params.getDeviceId();
        String deviceType = params.getDeviceType();
        String modelCode = params.getModelCode();
        String onOffFlag = params.getOnOffFlag();
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

            // 각방의 경우 서브 ID를 받아서 메인 ID로 최초등록자 ID 검색
            if (deviceType.equals("05")) {
                String parentId = deviceMapper.getParentIdBySubId(deviceId).getParentDevice();
                firstDeviceUser = memberMapper.getFirstDeviceUser(parentId);
                params.setDeviceId(parentId);
            } else {
                firstDeviceUser = memberMapper.getFirstDeviceUser(deviceId);
            }

            userId = firstDeviceUser.getUserId();

            stringObject = "Y";
            serialNumber = common.getHexSerialNumberFromDeviceId(deviceId);
            response = mobiusService.createCin(serialNumber, userId, JSON.toJson(powerOnOff));
            if (!response.getResponseCode().equals("201")) {
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
                    if (responseMessage.equals("0")){
                        stringObject = "Y";
                    } else {
                        stringObject = "N";
                    }
                } else {
                    // 타임아웃이나 응답 없음 처리
                    stringObject = "T";
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                log.error("", e);
            }

            gwMessagingSystem.removeMessageQueue("powr" + powerOnOff.getUuId());
            redisCommand.deleteValues(powerOnOff.getUuId());

            if (responseMessage != null && responseMessage.equals("2")) {
                conMap.put("body", "RemoteController WIFI ERROR");
                msg = "기기 응답이 없습니다. 잠시후 다시 시도하십시오";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1014, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else {
                if (stringObject.equals("Y")) {
                    conMap.put("body", "Device ON/OFF OK");
                    msg = "전원 On/Off 성공";
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                    result.setTestVariable(responseMessage);
                } else {
                    conMap.put("body", "Service TIME-OUT");
                    msg = "기기 네트워크 연결 오류. 잠시후 다시 시도하십시오";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1015, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                deviceInfo.setPowr(params.getPowerStatus());
                deviceInfo.setDeviceId(deviceId);
                if (deviceType.equals("05")) {
                    deviceMapper.updateEachRoomControlStatus(deviceInfo);
                } else {
                    deviceMapper.updateDeviceStatusFromApplication(deviceInfo);
                }

                household = memberMapper.getHouseholdByUserId(params.getUserId());
                params.setGroupId(household.getGroupId());
                List<AuthServerDTO> userIds = memberMapper.getUserIdsByDeviceId(params);
                List<AuthServerDTO> pushYnList = memberMapper.getPushYnStatusByUserIds(userIds);
                userNickname = memberMapper.getUserNickname(params.getUserId());
                userNickname.setUserNickname(common.stringToHex(userNickname.getUserNickname()));

                if (onOffFlag.equals("on")) {
                    if (memberMapper.getUserLoginoutStatus(userId).getLoginoutStatus().equals("Y")){
                        for (int i = 0; i < userIds.size(); ++i) {
                            if (memberMapper.getUserLoginoutStatus(userIds.get(i).getUserId()).getLoginoutStatus().equals("Y")) {
                                conMap.put("targetToken", memberMapper.getPushTokenByUserId(userIds.get(i).getUserId()).getPushToken());
                                conMap.put("title", "powr");
                                conMap.put("powr", params.getPowerStatus());
                                conMap.put("userNickname", userNickname.getUserNickname());
                                conMap.put("pushYn", pushYnList.get(i).getFPushYn());
                                conMap.put("modelCode", modelCode);
                                conMap.put("deviceNick", common.returnDeviceNickname(deviceId, deviceType));
                                conMap.put("deviceId", deviceId);
                                String jsonString = objectMapper.writeValueAsString(conMap);
                                mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);
                            }
                        }
                    }

                    common.insertHistory(
                            "1",
                            "PowerOnOff",
                            "powr",
                            "전원 ON/OFF",
                            "0",
                            deviceId, // 각방의 Sub DeviceId로 수정
                            params.getUserId(),
                            "전원 변경",
                            "전원 " + params.getPowerStatus(),
                            deviceType);
                }
            }
            log.info("result: {} ", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /** 각방 전체 전원 On/Off */
    @Override
    @Async
    public ResponseEntity<?> doRoomAllPowerOnOff(AuthServerDTO params) throws CustomException {
        ApiResponse.Data result = new ApiResponse.Data();
        PowerOnOff powerOnOff = new PowerOnOff();

        String stringObject;
        String msg;
        String userId;
        String deviceId = params.getDeviceId();
        String deviceType = params.getDeviceType();
        String modelCode = params.getModelCode();
        String onOffFlag = params.getOnOffFlag();
        String redisValue;
        String serialNumber;
        String responseMessage = null;

        AuthServerDTO userNickname;
        AuthServerDTO household;
        AuthServerDTO firstDeviceUser;
        MobiusResponse response;
        AuthServerDTO device;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();

        DeviceStatusInfo.Device deviceInfo = new DeviceStatusInfo.Device();

        try {

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

            firstDeviceUser = memberMapper.getFirstDeviceUser(deviceId);
            userId = firstDeviceUser.getUserId();

            serialNumber = common.getHexSerialNumberFromDeviceId(deviceId);

            if (serialNumber == null) {
                msg = "기기정보가 없습니다";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1009, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else {
                stringObject = "Y";
                serialNumber += "31";
                response = mobiusService.createCin(serialNumber, userId,
                        JSON.toJson(powerOnOff));
                if (!response.getResponseCode().equals("201")) {
                    msg = "중계서버 오류";
                    result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }
                try {
                    // 메시징 시스템을 통해 응답 메시지 대기
                    gwMessagingSystem.printMessageQueues();
                    responseMessage = gwMessagingSystem.waitForResponse("powr" + powerOnOff.getUuId(), TIME_OUT,
                            TimeUnit.SECONDS);
                    if (responseMessage != null) {
                        // 응답 처리
                        if (responseMessage.equals("0"))
                            stringObject = "Y";
                        else
                            stringObject = "N";
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

            if (responseMessage != null && responseMessage.equals("2")) {
                conMap.put("body", "RemoteController WIFI ERROR");
                msg = "기기 응답이 없습니다. 잠시후 다시 시도하십시오";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1014, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else {
                if (stringObject.equals("Y")) {
                    conMap.put("body", "Device ON/OFF OK");
                    msg = "전원 On/Off 성공";
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                    result.setTestVariable(responseMessage);
                } else {
                    conMap.put("body", "Service TIME-OUT");
                    msg = "기기 네트워크 연결 오류. 잠시후 다시 시도하십시오";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1015, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                deviceInfo.setPowr(params.getPowerStatus());
                deviceInfo.setDeviceId(deviceId);
                if (deviceType.equals("05")) {
                    deviceMapper.updateEachRoomControlStatus(deviceInfo);
                } else {
                    deviceMapper.updateDeviceStatusFromApplication(deviceInfo);
                }

                household = memberMapper.getHouseholdByUserId(params.getUserId());
                params.setGroupId(household.getGroupId());
                List<AuthServerDTO> userIds = memberMapper.getUserIdsByDeviceId(params);
                List<AuthServerDTO> pushYnList = memberMapper.getPushYnStatusByUserIds(userIds);
                userNickname = memberMapper.getUserNickname(params.getUserId());
                userNickname.setUserNickname(common.stringToHex(userNickname.getUserNickname()));

                if (onOffFlag.equals("on")) {
                    if (memberMapper.getUserLoginoutStatus(userId).getLoginoutStatus().equals("Y")){
                        for (int i = 0; i < userIds.size(); ++i) {
                            if (memberMapper.getUserLoginoutStatus(userIds.get(i).getUserId()).getLoginoutStatus().equals("Y")) {
                                conMap.put("targetToken", memberMapper.getPushTokenByUserId(userIds.get(i).getUserId()).getPushToken());
                                conMap.put("title", "powr");
                                conMap.put("powr", params.getPowerStatus());
                                conMap.put("userNickname", userNickname.getUserNickname());
                                conMap.put("pushYn", pushYnList.get(i).getFPushYn());
                                conMap.put("modelCode", modelCode);
                                conMap.put("deviceNick", common.returnDeviceNickname(deviceId, deviceType));
                                conMap.put("deviceId", deviceId);
                                String jsonString = objectMapper.writeValueAsString(conMap);
                                mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);
                            }
                        }
                    }

                    common.insertHistory(
                            "1",
                            "PowerOnOff",
                            "powr",
                            "전원 ON/OFF",
                            "0",
                            params.getDeviceId(),
                            params.getUserId(),
                            "전원 변경",
                            "전원 " + params.getPowerStatus(),
                            deviceType);
                }
            }

            log.info("result: {} ", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
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
        String modelCode = params.getModelCode();
        String controlAuthKey = params.getControlAuthKey();
        String deviceType = params.getDeviceType();
        String redisValue;

        AuthServerDTO checkDeviceExist;
        AuthServerDTO checkDeviceUser;
        AuthServerDTO groupLeaderId;
        AuthServerDTO groupLeaderIdByGroupIdx;

        List<AuthServerDTO> familyMemberList;

        try {
            // 수정
            if (registYn.equals("N")) {

                if (params.getTmpRegistKey() == null || params.getDeviceId() == null) {
                    msg = "TEMP-KEY-MISGSING";
                    result.setResult(ApiResponse.ResponseType.HTTP_400, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                /*
                 * *
                 * IoT 디바이스 UPDATE 순서
                 * 1. TBT_OPR_DEVICE_REGIST - 임시 단말 등록 정보
                 * 2. TBR_OPR_DEVICE_DETAIL - 단말정보상세
                 */
                if (deviceMapper.updateDeviceDetailLocation(params) <= 0) {
                    msg = "기기 정보 수정 실패.";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                if (deviceMapper.updateGroupName(params) <= 0) {
                    msg = "기기 정보 수정 실패.";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                if (deviceMapper.updateDeviceRegistGroupName(params) <= 0) {
                    msg = "기기 정보 수정 실패.";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                if (deviceMapper.updateDeviceRegistLocation(params) <= 0) {
                    msg = "기기 정보 수정 실패.";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                } else
                    stringObject = "Y";

                // 등록
            } else if (registYn.equals("Y")) {

                /*
                 * *
                 * IoT 디바이스 등록 INSERT 순서
                 * 1. TBR_IOT_DEVICE - 디바이스
                 * 2. TBT_OPR_DEVICE_REGIST - 임시 단말 등록 정보
                 * 3. TBR_OPR_DEVICE_DETAIL - 단말정보상세
                 * 4. TBR_OPR_USER_DEVICE - 사용자 단말 정보
                 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                 * 1. Push 설정 관련 기본 DB 추가 (기본값: Y)
                 * 2. IOT_DEVICE 테이블 등록 시 최초 기기 등록자 ID도 같이 등록
                 */

                params.setModelCode(" " + params.getModelCode());
                params.setSerialNumber("    " + params.getSerialNumber());

                params.setDeviceId(params.getDeviceId());
                params.setTmpRegistKey(params.getTmpRegistKey());

                params.setModelCode(params.getModelCode().replaceAll(" ", ""));
                params.setSerialNumber(params.getSerialNumber().replaceAll(" ", ""));

                // 해당 기기가 기존에 등록된 기기 인지 확인
                if (!deviceType.equals("05")) {
                    checkDeviceExist = deviceMapper.checkDeviceExist(deviceId);
                    if (!checkDeviceExist.getDeviceCount().equals("0")) {
                        // 해당 기기를 등록하는 사람이 요청자와 동일한 ID인지 확인 (동일할 경우 24시간, 빠른온수 예약 초기화 X)
                        // 2025-02-03 DCR-91/WF만 초기화 하게 수정
                        checkDeviceUser = deviceMapper.checkDeviceUserId(params);
                        if (checkDeviceUser.getDeviceCount().equals("0") && modelCode.equals("DCR-91/WF")) {
                            log.info("신규 사용자의 경우 주간 예약과 빠른온수 예약을 초기화 한다");
                            // 신규 사용자의 경우 주간 예약과 빠른온수 예약을 초기화 한다
                            // [{"wk":"","hs":[]}] - 24시간
                            // [{"tf":"","ws":[""],"hr":"","mn":"","i":""}] - 빠른온수

                            SetWeek setWeek = new SetWeek();
                            setWeek.setUserId(userId);
                            setWeek.setDeviceId(deviceId);
                            setWeek.setControlAuthKey(controlAuthKey);
                            setWeek.setFunctionId("7wk");
                            setWeek.setUuId(common.getTransactionId());
                            setWeek.setWeekListInit("[{\"wk\":\"\",\"hs\":[]}]");

                            redisValue = params.getUserId() + "," + setWeek.getFunctionId();
                            redisCommand.setValues(setWeek.getUuId(), redisValue);

                            mobiusService.createCin(common.getHexSerialNumberFromDeviceId(deviceId), userId, JSON.toJson(setWeek));

                            AwakeAlarmSet awakeAlarmSet = new AwakeAlarmSet();
                            awakeAlarmSet.setUserId(userId);
                            awakeAlarmSet.setAccessToken(common.getTransactionId());
                            awakeAlarmSet.setDeviceId(deviceId);
                            awakeAlarmSet.setControlAuthKey(controlAuthKey);
                            awakeAlarmSet.setFunctionId("fwh");
                            awakeAlarmSet.setUuId(common.getTransactionId());

                            List<HashMap<String, Object>> awakeList = new ArrayList<HashMap<String, Object>>();
                            HashMap<String, Object> map = new LinkedHashMap<>();
                            map.put("tf", "");
                            map.put("ws", Collections.singletonList(""));
                            map.put("hr", "");
                            map.put("mn", "");
                            map.put("i", "");
                            awakeList.add(map);
                            awakeAlarmSet.setAwakeList(awakeList);

                            mobiusService.createCin(common.getHexSerialNumberFromDeviceId(deviceId), userId, JSON.toJson(awakeAlarmSet));

                            DeviceStatusInfo.Device device = new DeviceStatusInfo.Device();
                            device.setDeviceId(deviceId);
                            device.setWk7(setWeek.getWeekListInit());
                            device.setFwh(JSON.toJson(awakeList));
                            deviceMapper.updateDeviceStatusFromApplication(device);
                        }
                        // 같은 기기를 이전에 등록한 사람이 있다면, 해당 기기를 삭제후 등록 진행 한다.
                        List<AuthServerDTO> authServerDTOList = deviceMapper.getCheckedDeviceExist(deviceId);
                        for (AuthServerDTO authServerDTO : authServerDTOList) {
                            memberMapper.deleteControllerMapping(authServerDTO);
                        }
                    }

                    if (!checkDeviceExist.getDeviceCount().equals("0")) {
                        List<AuthServerDTO> authServerDTOList = deviceMapper.getCheckedDeviceExist(deviceId);
                        for (AuthServerDTO authServerDTO : authServerDTOList) {
                            memberMapper.deleteControllerMapping(authServerDTO);
                        }
                    }
                    params.setValveStatus("N");
                } else {
                    params.setValveStatus("Y");
                    // 이전에 등록한 동일한 각방 기기가 있을 경우 삭제한다.
                    AuthServerDTO authServerDTO = new AuthServerDTO();
                    // 예시값: 20202020303833413844434146353237
                    authServerDTO.setDeviceId(common.getHexSerialNumberFromDeviceId(deviceId));
                    authServerDTO.setUserId(userId);
                    memberMapper.deleteEachRoomrMapping(authServerDTO);
                }

                params.setGroupId(userId);
                // getGroupIdx가 null이 아니고 비어있지 않은 경우의 처리
                if (params.getGroupIdx() != null && !params.getGroupIdx().isEmpty()) {
                    params.setIdx(Long.parseLong(params.getGroupIdx()));
                    groupLeaderIdByGroupIdx = memberMapper.getGroupLeaderIdByGroupIdx(params.getGroupIdx());
                    params.setGroupId(groupLeaderIdByGroupIdx.getGroupId());
                    familyMemberList = memberMapper.getFailyMemberByUserId(params);
                } else {
                    // getGroupIdx가 null이거나 비어있는 경우의 처리
                    memberMapper.insertInviteGroup(params);
                    memberMapper.updateInviteGroup(params);
                    // 신규 등록 시 등록한 Idx를 기반으로 사용자 ID 쿼리
                    groupLeaderId = memberMapper.getGroupLeaderId(params.getIdx());
                    params.setGroupId(groupLeaderId.getGroupId());
                    params.setGroupIdx(Long.toString(params.getIdx()));
                    familyMemberList = memberMapper.getFailyMemberByUserId(params);
                }

                // Push 설정 관련 기본 DB 추가
                List<AuthServerDTO> inputList = new ArrayList<>();
                for (AuthServerDTO authServerDTO : familyMemberList) {
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

                // TBR_IOT_DEVICE_GRP_INFO 테이블에 본인 포함 세대원 정보 추가
                if (deviceMapper.insertDeviceGrpInfoByList(inputList) <= 0) {
                    msg = "사용자, 세대원 기기 정보 등록 실패.";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                if (memberMapper.insertUserDevicePushByList(inputList) <= 0) {
                    msg = "사용자 PUSH 정보 등록 실패.";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                if (deviceMapper.insertDevice(params) <= 0) {
                    msg = "기기 정보 등록 실패.";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                if (deviceMapper.insertFristDeviceUser(params) <= 0) {
                    msg = "기기 정보 등록 실패.";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                if (deviceMapper.insertDeviceRegist(params) <= 0) {
                    msg = "기기 정보 등록 실패.";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                if (deviceMapper.insertDeviceDetail(params) <= 0) {
                    msg = "기기 정보 등록 실패.";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                if (deviceMapper.insertUserDevice(params) <= 0) {
                    msg = "기기 정보 등록 실패.";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                // 기기 홈화면 조회 시 필수적으로 필요한 TBR_OPR_ACTIVE_DEVICE_STATUS 테이블 값 추가 (기본값: of)
                if (modelCode.equals("ESCeco13S")) {
                    // ESCeco13S
                    deviceMapper.deleteActiveOldDevice(params);
                    deviceMapper.insertActiveOldDevice(params);
                }
                stringObject = "Y";
            }

            if (stringObject.equals("Y") && registYn.equals("Y")) {
                msg = "기기 정보 등록 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                result.setLatitude(params.getLatitude());
                result.setLongitude(params.getLongitude());
                result.setTmpRegistKey(params.getTmpRegistKey());

            } else if (stringObject.equals("Y")) {
                msg = "기기 정보 수정 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                result.setLatitude(params.getLatitude());
                result.setLongitude(params.getLongitude());
                result.setTmpRegistKey("NULL");
            }

            params.setUserId(userId);
            params.setPushTitle("기기제어");
            if (deviceId == null)
                deviceId = "EMPTY";
            params.setDeviceId(deviceId);

            if (registYn.equals("N"))
                params.setPushContent("기기정보 수정");
            else if (registYn.equals("Y"))
                params.setPushContent("신규기기 등록");

            log.info("result: {} ", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (CustomException e) {
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 홈 IoT 컨트롤러 상태 정보 조회 */
    @Override
    public ResponseEntity<?> doDeviceStatusInfo(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        String msg;
        String modelCode = params.getModelCode();
        String uuId = common.getTransactionId();
        AuthServerDTO serialNumber;
        Map<String, String> resultMap = new HashMap<>();
        List<DeviceStatusInfo.Device> device;

        try {

            serialNumber = deviceMapper.getSingleSerialNumberBydeviceId(params.getDeviceId());
            if (serialNumber == null) {
                msg = "기기 상태 정보 조회 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else {
                device = deviceMapper.getDeviceStauts(Collections.singletonList(serialNumber.getSerialNumber()));
                if (device == null) {
                    msg = "기기 상태 정보 조회 실패";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                } else if (modelCode.equals("ESCeco13S") || modelCode.equals("DCR-91/WF")) {
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
                        String type24h = common.readCon(value.getH24(), "serviceMd");
                        if (type24h == null || type24h.isEmpty()) {
                            resultMap.put("type24h", "");
                        } else {
                            resultMap.put("type24h", type24h);
                        }

                        resultMap.put("slCd", value.getSlCd());
                        resultMap.put("hwSt", value.getHwSt());
                        resultMap.put("fcLc", value.getFcLc());
                        resultMap.put("mn", value.getMn());

                        ConcurrentHashMap<String, String> eleMap = new ConcurrentHashMap<>();

                        eleMap.put("24h", value.getH24());
                        eleMap.put("12h", value.getH12());
                        eleMap.put("7wk", value.getWk7());

                        if (value.getFwh() == null) {
                            eleMap.put("fwh", "null");
                        } else {
                            eleMap.put("fwh", value.getFwh());
                        }

                        resultMap.put("rsCf", JSON.toJson(eleMap));
                    }
                } else if (modelCode.equals("DCR-47/WF")) {
                    resultMap.put("deviceStatus", "01");
                    resultMap.put("modelCategoryCode", "07");
                    for (DeviceStatusInfo.Device value : device) {
                        resultMap.put("rKey", value.getRKey());
                        resultMap.put("powr", value.getPowr());
                        resultMap.put("opMd", value.getOpMd());
                        resultMap.put("vtSp", value.getVtSp());
                        resultMap.put("inAq", value.getInAq());
                        resultMap.put("mfDt", value.getMfDt());
                        resultMap.put("odHm", value.getOdHm());

                        ConcurrentHashMap<String, String> eleMap = new ConcurrentHashMap<>();
                        eleMap.put("rsPw", value.getRsPw());
                        resultMap.put("rsCf", JSON.toJson(eleMap));
                    }
                } else if (modelCode.equals("DHR-160") || modelCode.equals("DHR-166") || modelCode.equals("DHR-260") || modelCode.equals("DHR-260A")) {
                    resultMap.put("deviceStatus", "01");
                    resultMap.put("modelCategoryCode", "02");
                    for (DeviceStatusInfo.Device value : device) {
                        resultMap.put("rKey", value.getRKey());
                        resultMap.put("powr", value.getPowr());
                        resultMap.put("opMd", value.getOpMd());
                        resultMap.put("htTp", value.getHtTp());
                        resultMap.put("wtTp", value.getWtTp());
                        resultMap.put("hwTp", value.getHwTp());
                        resultMap.put("rsMd", "01");
                        resultMap.put("otTp", "10.0");
                        resultMap.put("bCdt", value.getBCdt());
                        resultMap.put("chTp", value.getChTp());
                        resultMap.put("hwSt", value.getHwSt());
                        resultMap.put("fcDf", "on");
                        resultMap.put("mfDt", value.getMfDt());

                        ConcurrentHashMap<String, String> eleMap = new ConcurrentHashMap<>();
                        eleMap.put("12h", "{ “om\" : \"03\", “sm\" : \"30\"}");
                        eleMap.put("7wk", "[" + "{ \"wk\":\"0\", \"hs\" : [\"01\", \"18\"] }" + "]");
                        resultMap.put("rsCf", String.valueOf(eleMap));

                    }
                }
            }

            msg = "기기 상태 정보 조회 성공";
            result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            redisCommand.deleteValues(uuId);
            result.setDeviceStatusInfo(resultMap);

            log.info("result: {} ", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /** 모드변경 */
    @Override
    public ResponseEntity<?> doModeChange(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        ModeChange modeChange = new ModeChange();
        String stringObject = "N";
        String msg;
        String serialNumber;
        String modeCode = params.getModeCode();
        String onOffFlag = params.getOnOffFlag();
        String userId;
        String deviceId = params.getDeviceId();
        String modelCode = params.getModelCode();
        String sleepCode = null;
        String deviceType = "01";

        AuthServerDTO userNickname;
        AuthServerDTO household;
        AuthServerDTO firstDeviceUser;

        if (params.getModeCode().equals("06"))
            sleepCode = params.getSleepCode();

        String responseMessage = null;
        String redisValue;
        MobiusResponse response;
        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        DeviceStatusInfo.Device deviceInfo = new DeviceStatusInfo.Device();

        try {

            modeChange.setUserId(params.getUserId());
            modeChange.setDeviceId(params.getDeviceId());
            modeChange.setControlAuthKey(params.getControlAuthKey());
            modeChange.setModelCode(params.getModelCode());
            modeChange.setModeCode(modeCode);
            if (modeCode.equals("06")){
                modeChange.setSleepCode(sleepCode);
            }
            modeChange.setFunctionId("opMd");
            modeChange.setUuId(common.getTransactionId());
            redisValue = params.getUserId() + "," + modeChange.getFunctionId();
            redisCommand.setValues(modeChange.getUuId(), redisValue);

            // 각방의 경우 서브 ID를 받아서 메인 ID로 최초등록자 ID 검색
            if (modelCode.contains("MC2600") || modelCode.contains("DR-300W")) {
                String parentId = deviceMapper.getParentIdBySubId(deviceId).getParentDevice();
                firstDeviceUser = memberMapper.getFirstDeviceUser(parentId);
                params.setDeviceId(parentId);
            } else {
                firstDeviceUser = memberMapper.getFirstDeviceUser(deviceId);
            }

            userId = firstDeviceUser.getUserId();
            serialNumber = common.getHexSerialNumberFromDeviceId(deviceId);
            response = mobiusService.createCin(serialNumber, userId, JSON.toJson(modeChange));

            if (!response.getResponseCode().equals("201")) {
                msg = "중계서버 오류";
                result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            try {
                // 메시징 시스템을 통해 응답 메시지 대기
                responseMessage = gwMessagingSystem.waitForResponse("opMd" + modeChange.getUuId(), TIME_OUT, TimeUnit.SECONDS);
                if (responseMessage == null)
                    stringObject = "T";
                else {
                    if (responseMessage.equals("0")){
                        stringObject = "Y";
                    } else {
                        stringObject = "N";
                    }
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                log.error("", e);
            }

            gwMessagingSystem.removeMessageQueue("opMd" + modeChange.getUuId());
            redisCommand.deleteValues(modeChange.getUuId());

            if (responseMessage != null && responseMessage.equals("2")) {
                conMap.put("body", "RemoteController WIFI ERROR");
                msg = "기기 응답이 없습니다. 잠시후 다시 시도하십시오";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1014, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else {
                if (stringObject.equals("Y")) {
                    conMap.put("body", "Mode Change OK");
                    msg = "모드변경 성공";
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                } else if (stringObject.equals("N")) {
                    conMap.put("body", "Mode Change FAIL");
                    msg = "모드변경 실패";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                } else {
                    conMap.put("body", "Service TIME-OUT");
                    msg = "기기 네트워크 연결 오류. 잠시후 다시 시도하십시오";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1015, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                deviceInfo.setOpMd(modeCode);
                deviceInfo.setDeviceId(deviceId);
                if (params.getModeCode().equals("06")) {
                    deviceInfo.setSlCd(sleepCode);
                }

                if (modelCode.contains("MC2600") || modelCode.contains("DR-300W")) {
                    deviceType = "05";
                    deviceMapper.updateEachRoomControlStatus(deviceInfo);
                } else {
                    deviceMapper.updateDeviceStatusFromApplication(deviceInfo);
                }

                household = memberMapper.getHouseholdByUserId(params.getUserId());
                params.setGroupId(household.getGroupId());
                List<AuthServerDTO> userIds = memberMapper.getUserIdsByDeviceId(params);
                List<AuthServerDTO> pushYnList = memberMapper.getPushYnStatusByUserIds(userIds);
                userNickname = memberMapper.getUserNickname(params.getUserId());
                userNickname.setUserNickname(common.stringToHex(userNickname.getUserNickname()));

                if (onOffFlag.equals("on")) {
                    if (memberMapper.getUserLoginoutStatus(userId).getLoginoutStatus().equals("Y")){
                        for (int i = 0; i < userIds.size(); ++i) {
                            if (memberMapper.getUserLoginoutStatus(userIds.get(i).getUserId()).getLoginoutStatus().equals("Y")) {
                                conMap.put("pushYn", pushYnList.get(i).getFPushYn());
                                conMap.put("modelCode", modelCode);
                                conMap.put("targetToken", memberMapper.getPushTokenByUserId(userIds.get(i).getUserId()).getPushToken());
                                conMap.put("userNickname", userNickname.getUserNickname());
                                conMap.put("deviceNick", common.returnDeviceNickname(deviceId, deviceType));
                                conMap.put("title", "opMd");
                                conMap.put("deviceId", deviceId);
                                conMap.put("id", "Mode Change ID");

                                String jsonString = objectMapper.writeValueAsString(conMap);
                                mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);
                            }
                        }
                    }

                    switch (modeCode) {
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
                            params.setControlCodeName("예약난방-반복예약");
                            break;
                        case "12":
                            if (modelCode.equals("DCR-91/WF"))
                                params.setControlCodeName("예약난방-24시간주간");
                            else
                                params.setControlCodeName("예약난방-주간");
                            break;
                        case "14":
                            params.setControlCodeName("냉밥모드");
                            break;
                        case "21":
                            params.setControlCodeName("수동-전열환기");
                            break;
                        case "22":
                            params.setControlCodeName("수동-청정환기");
                            break;
                        case "23":
                            params.setControlCodeName("수동-실내청정");
                            break;
                        case "24":
                            params.setControlCodeName("수동-취침운전");
                            break;
                        case "25":
                            params.setControlCodeName("자동-예약설정");
                            break;
                        case "26":
                            params.setControlCodeName("자동-외출설정");
                            break;
                        case "27":
                            params.setControlCodeName("자동-전열환기");
                            break;
                        case "29":
                            params.setControlCodeName("자동-실내청정");
                            break;
                        default:
                            params.setControlCodeName("NONE_MODE");
                            break;
                    }

                    common.insertHistory(
                            "0",
                            "ModeChange",
                            "modeCode",
                            params.getControlCodeName(),
                            "0",
                            deviceId, // 각방의 Sub DeviceId로 수정
                            params.getUserId(),
                            "모드 변경",
                            params.getControlCodeName(),
                            common.getModelCode(modelCode));
                }
            }

            log.info("result: {} ", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /** 각방 전체 모드변경 */
    @Override
    public ResponseEntity<?> doRoomAllModeChange(AuthServerDTO params) throws CustomException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'doRoomAllModeChange'");
    }

    /** 실내온도 설정 */
    @Override
    public ResponseEntity<?> doTemperatureSet(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        TemperatureSet temperatureSet = new TemperatureSet();
        String stringObject = "N";
        String msg;
        String responseMessage = null;
        String userId;
        String deviceId = params.getDeviceId();
        String redisValue;
        String onOffFlag = params.getOnOffFlag();
        MobiusResponse response;
        String serialNumber;
        AuthServerDTO userNickname;
        AuthServerDTO household;
        AuthServerDTO firstDeviceUser;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        DeviceStatusInfo.Device deviceInfo = new DeviceStatusInfo.Device();
        try {

            temperatureSet.setUserId(params.getUserId());
            temperatureSet.setDeviceId(params.getDeviceId());
            temperatureSet.setControlAuthKey(params.getControlAuthKey());
            temperatureSet.setTemperature(params.getTemperture());
            temperatureSet.setFunctionId("htTp");
            temperatureSet.setUuId(common.getTransactionId());

            redisValue = params.getUserId() + "," + temperatureSet.getFunctionId();
            redisCommand.setValues(temperatureSet.getUuId(), redisValue);

            // True면 각방 False면 타기기
            if (common.checkDeviceType(deviceId)) {
                String parentId = deviceMapper.getParentIdBySubId(deviceId).getParentDevice();
                firstDeviceUser = memberMapper.getFirstDeviceUser(parentId);
                params.setDeviceId(parentId);
            } else {
                firstDeviceUser = memberMapper.getFirstDeviceUser(deviceId);
            }

            userId = firstDeviceUser.getUserId();
            serialNumber = common.getHexSerialNumberFromDeviceId(deviceId);

            response = mobiusService.createCin(serialNumber, userId, JSON.toJson(temperatureSet));

            if (!response.getResponseCode().equals("201")) {
                msg = "중계서버 오류";
                result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            try {
                // 메시징 시스템을 통해 응답 메시지 대기
                responseMessage = gwMessagingSystem.waitForResponse("htTp" + temperatureSet.getUuId(), TIME_OUT,
                        TimeUnit.SECONDS);
                if (responseMessage == null)
                    stringObject = "T";
                else {
                    if (responseMessage.equals("0")){
                        stringObject = "Y";
                    } else {
                        stringObject = "N";
                    }
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                log.error("", e);
            }

            gwMessagingSystem.removeMessageQueue("htTp" + temperatureSet.getUuId());
            redisCommand.deleteValues(temperatureSet.getUuId());

            if (responseMessage != null && responseMessage.equals("2")) {
                conMap.put("body", "RemoteController WIFI ERROR");
                msg = "기기 응답이 없습니다. 잠시후 다시 시도하십시오";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1014, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else {
                if (stringObject.equals("Y")) {
                    conMap.put("body", "TemperatureSet OK");
                    msg = "실내온도 설정 성공";
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                } else if (stringObject.equals("N")) {
                    conMap.put("body", "TemperatureSet OK");
                    msg = "실내온도 설정 실패";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                } else {
                    conMap.put("body", "Service TIME-OUT");
                    msg = "기기 네트워크 연결 오류. 잠시후 다시 시도하십시오";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1015, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                household = memberMapper.getHouseholdByUserId(params.getUserId());
                params.setGroupId(household.getGroupId());
                List<AuthServerDTO> userIds = memberMapper.getUserIdsByDeviceId(params);
                List<AuthServerDTO> pushYnList = memberMapper.getPushYnStatusByUserIds(userIds);
                userNickname = memberMapper.getUserNickname(params.getUserId());
                userNickname.setUserNickname(common.stringToHex(userNickname.getUserNickname()));

                deviceInfo.setHtTp(params.getTemperture());
                deviceInfo.setDeviceId(params.getDeviceId());
                if (common.checkDeviceType(deviceId)) {
                    deviceMapper.updateEachRoomControlStatus(deviceInfo);
                } else {
                    deviceMapper.updateDeviceStatusFromApplication(deviceInfo);
                }

                if (onOffFlag.equals("on")) {
                    for (int i = 0; i < userIds.size(); ++i) {
                        if (memberMapper.getUserLoginoutStatus(userIds.get(i).getUserId()).getLoginoutStatus().equals("Y")) {
                            conMap.put("pushYn", pushYnList.get(i).getFPushYn());
                            conMap.put("modelCode", common.getModelCodeFromDeviceId(deviceId).replaceAll(" ", ""));
                            conMap.put("targetToken", memberMapper.getPushTokenByUserId(userIds.get(i).getUserId()).getPushToken());
                            conMap.put("userNickname", userNickname.getUserNickname());
                            conMap.put("deviceNick", common.returnDeviceNickname(params.getDeviceId()));
                            conMap.put("title", "htTp");
                            conMap.put("deviceId", deviceId);
                            conMap.put("id", "TemperatureSet ID");
                            String jsonString = objectMapper.writeValueAsString(conMap);
                            mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);
                        }
                    }

                    common.insertHistory(
                            "1",
                            "TemperatureSet",
                            "modeCode",
                            "실내 온도 설정",
                            "0",
                            params.getDeviceId(),
                            params.getUserId(),
                            "htTp",
                            params.getTemperture(),
                            "01");
                }

            }
            log.info("result: {} ", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /** 냉방-실내온도 설정 */
    @Override
    public ResponseEntity<?> doColdTempertureSet(AuthServerDTO params) throws CustomException {
        ApiResponse.Data result = new ApiResponse.Data();
        TemperatureSet temperatureSet = new TemperatureSet();
        String stringObject = "N";
        String msg;
        String responseMessage = null;
        String userId;
        String deviceId = params.getDeviceId();
        String redisValue;
        String onOffFlag = params.getOnOffFlag();
        MobiusResponse response;
        String serialNumber;
        AuthServerDTO userNickname;
        AuthServerDTO household;
        AuthServerDTO firstDeviceUser;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        DeviceStatusInfo.Device deviceInfo = new DeviceStatusInfo.Device();
        try {

            temperatureSet.setUserId(params.getUserId());
            temperatureSet.setDeviceId(params.getDeviceId());
            temperatureSet.setControlAuthKey(params.getControlAuthKey());
            temperatureSet.setTemperature(params.getTemperture());
            temperatureSet.setFunctionId("clTp");
            temperatureSet.setUuId(common.getTransactionId());

            redisValue = params.getUserId() + "," + temperatureSet.getFunctionId();
            redisCommand.setValues(temperatureSet.getUuId(), redisValue);

            firstDeviceUser = memberMapper.getFirstDeviceUser(deviceId);
            userId = firstDeviceUser.getUserId();
            serialNumber = common.getHexSerialNumberFromDeviceId(deviceId);
            response = mobiusService.createCin(serialNumber, userId, JSON.toJson(temperatureSet));

            if (!response.getResponseCode().equals("201")) {
                msg = "중계서버 오류";
                result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            try {
                // 메시징 시스템을 통해 응답 메시지 대기
                responseMessage = gwMessagingSystem.waitForResponse("clTp" + temperatureSet.getUuId(), TIME_OUT,
                        TimeUnit.SECONDS);
                if (responseMessage == null)
                    stringObject = "T";
                else {
                    if (responseMessage.equals("0")){
                        stringObject = "Y";
                    } else {
                        stringObject = "N";
                    }
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                log.error("", e);
            }

            gwMessagingSystem.removeMessageQueue("clTp" + temperatureSet.getUuId());
            redisCommand.deleteValues(temperatureSet.getUuId());

            if (responseMessage != null && responseMessage.equals("2")) {
                conMap.put("body", "RemoteController WIFI ERROR");
                msg = "기기 응답이 없습니다. 잠시후 다시 시도하십시오";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1014, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else {
                if (stringObject.equals("Y")) {
                    conMap.put("body", "TemperatureSet OK");
                    msg = "냉방-실내 온도 설정 성공";
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                } else if (stringObject.equals("N")) {
                    conMap.put("body", "TemperatureSet OK");
                    msg = "냉방-실내 온도 설정 실패";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                } else {
                    conMap.put("body", "Service TIME-OUT");
                    msg = "기기 네트워크 연결 오류. 잠시후 다시 시도하십시오";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1015, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                household = memberMapper.getHouseholdByUserId(params.getUserId());
                params.setGroupId(household.getGroupId());
                List<AuthServerDTO> userIds = memberMapper.getUserIdsByDeviceId(params);
                List<AuthServerDTO> pushYnList = memberMapper.getPushYnStatusByUserIds(userIds);
                userNickname = memberMapper.getUserNickname(params.getUserId());
                userNickname.setUserNickname(common.stringToHex(userNickname.getUserNickname()));

                deviceInfo.setHtTp(params.getTemperture());
                deviceInfo.setDeviceId(params.getDeviceId());
                if (common.checkDeviceType(deviceId)) {
                    deviceMapper.updateEachRoomControlStatus(deviceInfo);
                } else {
                    deviceMapper.updateDeviceStatusFromApplication(deviceInfo);
                }

                if (onOffFlag.equals("on")) {
                    for (int i = 0; i < userIds.size(); ++i) {
                        if (memberMapper.getUserLoginoutStatus(userIds.get(i).getUserId()).getLoginoutStatus().equals("Y")) {
                            conMap.put("pushYn", pushYnList.get(i).getFPushYn());
                            conMap.put("modelCode", common.getModelCodeFromDeviceId(deviceId).replaceAll(" ", ""));
                            conMap.put("targetToken", memberMapper.getPushTokenByUserId(userIds.get(i).getUserId()).getPushToken());
                            conMap.put("userNickname", userNickname.getUserNickname());
                            conMap.put("deviceNick", common.returnDeviceNickname(params.getDeviceId()));
                            conMap.put("title", "clTp");
                            conMap.put("deviceId", deviceId);
                            conMap.put("id", "TemperatureSet ID");
                            String jsonString = objectMapper.writeValueAsString(conMap);
                            mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);
                        }
                    }

                    common.insertHistory(
                            "1",
                            "TemperatureSet",
                            "modeCode",
                            "냉방-실내 온도 설정",
                            "0",
                            params.getDeviceId(),
                            params.getUserId(),
                            "clTp",
                            params.getTemperture(),
                            "01");
                }

            }
            log.info("result: {} ", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /** 강제 제상 모드 설정 */
    @Override
    public ResponseEntity<?> doForcedDefrost(AuthServerDTO params) throws CustomException {
        ApiResponse.Data result = new ApiResponse.Data();
        ForcedDefrost forcedDefrost = new ForcedDefrost();
        String stringObject = "N";
        String msg;
        String responseMessage = null;
        String userId;
        String deviceId = params.getDeviceId();
        String redisValue;
        String onOffFlag = params.getOnOffFlag();
        MobiusResponse response;
        String serialNumber;
        AuthServerDTO userNickname;
        AuthServerDTO household;
        AuthServerDTO firstDeviceUser;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        DeviceStatusInfo.Device deviceInfo = new DeviceStatusInfo.Device();
        try {

            forcedDefrost.setUserId(params.getUserId());
            forcedDefrost.setDeviceId(params.getDeviceId());
            forcedDefrost.setControlAuthKey(params.getControlAuthKey());
            forcedDefrost.setForcedDefrost(params.getForcedDefrost());
            forcedDefrost.setFunctionId("fcDf");
            forcedDefrost.setUuId(common.getTransactionId());

            redisValue = params.getUserId() + "," + forcedDefrost.getFunctionId();
            redisCommand.setValues(forcedDefrost.getUuId(), redisValue);

            firstDeviceUser = memberMapper.getFirstDeviceUser(deviceId);

            userId = firstDeviceUser.getUserId();
            serialNumber = common.getHexSerialNumberFromDeviceId(deviceId);
            response = mobiusService.createCin(serialNumber, userId, JSON.toJson(forcedDefrost));

            if (!response.getResponseCode().equals("201")) {
                msg = "중계서버 오류";
                result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            try {
                // 메시징 시스템을 통해 응답 메시지 대기
                responseMessage = gwMessagingSystem.waitForResponse("fcDf" + forcedDefrost.getUuId(), TIME_OUT,
                        TimeUnit.SECONDS);
                if (responseMessage == null)
                    stringObject = "T";
                else {
                    if (responseMessage.equals("0"))
                        stringObject = "Y";
                    else
                        stringObject = "N";
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                log.error("", e);
            }

            gwMessagingSystem.removeMessageQueue("fcDf" + forcedDefrost.getUuId());
            redisCommand.deleteValues(forcedDefrost.getUuId());

            if (responseMessage != null && responseMessage.equals("2")) {
                conMap.put("body", "RemoteController WIFI ERROR");
                msg = "기기 응답이 없습니다. 잠시후 다시 시도하십시오";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1014, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else {
                if (stringObject.equals("Y")) {
                    conMap.put("body", "TemperatureSet OK");
                    msg = "강제 제상 모드 설정 성공";
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                } else if (stringObject.equals("N")) {
                    conMap.put("body", "TemperatureSet OK");
                    msg = "강제 제상 모드 설정 실패";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                } else {
                    conMap.put("body", "Service TIME-OUT");
                    msg = "기기 네트워크 연결 오류. 잠시후 다시 시도하십시오";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1015, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                household = memberMapper.getHouseholdByUserId(params.getUserId());
                params.setGroupId(household.getGroupId());
                List<AuthServerDTO> userIds = memberMapper.getUserIdsByDeviceId(params);
                List<AuthServerDTO> pushYnList = memberMapper.getPushYnStatusByUserIds(userIds);
                userNickname = memberMapper.getUserNickname(params.getUserId());
                userNickname.setUserNickname(common.stringToHex(userNickname.getUserNickname()));

                deviceInfo.setHtTp(params.getTemperture());
                deviceInfo.setDeviceId(params.getDeviceId());
                if (common.checkDeviceType(deviceId)) {
                    deviceMapper.updateEachRoomControlStatus(deviceInfo);
                } else {
                    deviceMapper.updateDeviceStatusFromApplication(deviceInfo);
                }

                if (onOffFlag.equals("on")) {
                    for (int i = 0; i < userIds.size(); ++i) {
                        if (memberMapper.getUserLoginoutStatus(userIds.get(i).getUserId()).getLoginoutStatus().equals("Y")) {
                            conMap.put("pushYn", pushYnList.get(i).getFPushYn());
                            conMap.put("modelCode", common.getModelCodeFromDeviceId(deviceId).replaceAll(" ", ""));
                            conMap.put("targetToken", memberMapper.getPushTokenByUserId(userIds.get(i).getUserId()).getPushToken());
                            conMap.put("userNickname", userNickname.getUserNickname());
                            conMap.put("deviceNick", common.returnDeviceNickname(params.getDeviceId()));
                            conMap.put("title", "fcDf");
                            conMap.put("deviceId", deviceId);
                            conMap.put("id", "ForcedDefrost ID");
                            String jsonString = objectMapper.writeValueAsString(conMap);
                            mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);
                        }
                    }

                    common.insertHistory(
                            "1",
                            "ForcedDeFrost",
                            "modeCode",
                            "강제 제상 모드 설정",
                            "0",
                            params.getDeviceId(),
                            params.getUserId(),
                            "fcDf",
                            params.getTemperture(),
                            "01");
                }

            }
            log.info("result: {} ", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /** 난방수온도 설정 */
    @Override
    public ResponseEntity<?> doBoiledWaterTempertureSet(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        BoiledWaterTempertureSet boiledWaterTempertureSet = new BoiledWaterTempertureSet();
        String stringObject = "N";
        String msg;
        String userId;
        String deviceId = params.getDeviceId();
        String redisValue;
        String onOffFlag = params.getOnOffFlag();
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
            serialNumber = common.getHexSerialNumberFromDeviceId(deviceId);

            boiledWaterTempertureSet.setUserId(params.getUserId());
            boiledWaterTempertureSet.setDeviceId(params.getDeviceId());
            boiledWaterTempertureSet.setControlAuthKey(params.getControlAuthKey());
            boiledWaterTempertureSet.setTemperature(params.getTemperture());
            boiledWaterTempertureSet.setFunctionId("wtTp");
            boiledWaterTempertureSet.setUuId(common.getTransactionId());

            redisValue = params.getUserId() + "," + boiledWaterTempertureSet.getFunctionId();
            redisCommand.setValues(boiledWaterTempertureSet.getUuId(), redisValue);
            response = mobiusService.createCin(serialNumber, userId, JSON.toJson(boiledWaterTempertureSet));

            if (!response.getResponseCode().equals("201")) {
                msg = "중계서버 오류";
                result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            try {
                // 메시징 시스템을 통해 응답 메시지 대기
                responseMessage = gwMessagingSystem.waitForResponse("wtTp" + boiledWaterTempertureSet.getUuId(),
                        TIME_OUT, TimeUnit.SECONDS);
                if (responseMessage == null)
                    stringObject = "T";
                else {
                    if (responseMessage.equals("0"))
                        stringObject = "Y";
                    else
                        stringObject = "N";
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                log.error("", e);
            }

            gwMessagingSystem.removeMessageQueue("wtTp" + boiledWaterTempertureSet.getUuId());
            redisCommand.deleteValues(boiledWaterTempertureSet.getUuId());

            if (responseMessage != null && responseMessage.equals("2")) {
                conMap.put("body", "RemoteController WIFI ERROR");
                msg = "기기 네트워크 연결 오류. 잠시후 다시 시도하십시오";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1014, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else {
                if (stringObject.equals("Y")) {
                    conMap.put("body", "BoiledWaterTempertureSet OK");
                    msg = "난방수온도 설정 성공";
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                } else if (stringObject.equals("N")) {
                    conMap.put("body", "BoiledWaterTempertureSet FAIL");
                    msg = "난방수온도 설정 실패";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                } else {
                    conMap.put("body", "Service TIME-OUT ");
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

                deviceInfo.setWtTp(params.getTemperture());
                deviceInfo.setDeviceId(deviceId);
                deviceMapper.updateDeviceStatusFromApplication(deviceInfo);

                if (onOffFlag.equals("on")) {
                    for (int i = 0; i < userIds.size(); ++i) {
                        if (memberMapper.getUserLoginoutStatus(userIds.get(i).getUserId()).getLoginoutStatus().equals("Y")) {
                            conMap.put("pushYn", pushYnList.get(i).getFPushYn());
                            conMap.put("modelCode", common.getModelCodeFromDeviceId(deviceId).replaceAll(" ", ""));
                            conMap.put("targetToken", memberMapper.getPushTokenByUserId(userIds.get(i).getUserId()).getPushToken());
                            conMap.put("userNickname", userNickname.getUserNickname());
                            conMap.put("deviceNick", common.returnDeviceNickname(deviceId));
                            conMap.put("title", "wtTp");
                            conMap.put("deviceId", deviceId);
                            conMap.put("id", "BoiledWaterTempertureSet ID");
                            String jsonString = objectMapper.writeValueAsString(conMap);
                            mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);
                        }
                    }

                    common.insertHistory(
                            "1",
                            "BoiledWaterTempertureSet",
                            "wtTp",
                            "난방수 온도 설정",
                            "0",
                            deviceId,
                            params.getUserId(),
                            "wtTp",
                            params.getTemperture(),
                            "01");
                }
            }

            log.info("result: {}", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
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
        String serialNumber;
        String deviceType = "01";
        MobiusResponse response;
        AuthServerDTO household;
        AuthServerDTO userNickname;
        AuthServerDTO firstDeviceUser;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        DeviceStatusInfo.Device deviceInfo = new DeviceStatusInfo.Device();
        try {

            waterTempertureSet.setUserId(params.getUserId());
            waterTempertureSet.setDeviceId(params.getDeviceId());
            waterTempertureSet.setControlAuthKey(params.getControlAuthKey());
            waterTempertureSet.setTemperature(params.getTemperture());
            waterTempertureSet.setFunctionId("hwTp");
            waterTempertureSet.setUuId(common.getTransactionId());

            redisValue = params.getUserId() + "," + waterTempertureSet.getFunctionId();
            redisCommand.setValues(waterTempertureSet.getUuId(), redisValue);

            // True면 각방 False면 타기기
            if (common.checkDeviceType(deviceId)) {
                String parentId = deviceMapper.getParentIdBySubId(deviceId).getParentDevice();
                firstDeviceUser = memberMapper.getFirstDeviceUser(parentId);
                params.setDeviceId(parentId);
            } else {
                firstDeviceUser = memberMapper.getFirstDeviceUser(deviceId);
            }

            userId = firstDeviceUser.getUserId();
            serialNumber = common.getHexSerialNumberFromDeviceId(deviceId);

            response = mobiusResponse = mobiusService.createCin(serialNumber, userId, JSON.toJson(waterTempertureSet));

            if (!response.getResponseCode().equals("201")) {
                msg = "중계서버 오류";
                result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            try {
                // 메시징 시스템을 통해 응답 메시지 대기
                responseMessage = gwMessagingSystem.waitForResponse("hwTp" + waterTempertureSet.getUuId(), TIME_OUT,
                        TimeUnit.SECONDS);
                if (responseMessage == null)
                    stringObject = "T";
                else {
                    if (responseMessage.equals("0"))
                        stringObject = "Y";
                    else
                        stringObject = "N";
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                log.error("", e);
            }

            gwMessagingSystem.removeMessageQueue("hwTp" + waterTempertureSet.getUuId());
            redisCommand.deleteValues(waterTempertureSet.getUuId());

            if (responseMessage != null && responseMessage.equals("2")) {
                conMap.put("body", "RemoteController WIFI ERROR");
                msg = "기기 네트워크 연결 오류. 잠시후 다시 시도하십시오";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1014, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else {
                if (stringObject.equals("Y")) {
                    conMap.put("body", "WaterTempertureSet OK");
                    msg = "온수온도 설정 성공";
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                } else if (stringObject.equals("N")) {
                    conMap.put("body", "WaterTempertureSet FAIL");
                    msg = "온수온도 설정 실패";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                } else {
                    conMap.put("body", "Service TIME-OUT");
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

                deviceInfo.setHwTp(params.getTemperture());
                deviceInfo.setDeviceId(deviceId);
                if (common.checkDeviceType(deviceId)) {
                    deviceType = "05";
                    deviceMapper.updateEachRoomControlStatus(deviceInfo);
                } else {
                    deviceMapper.updateDeviceStatusFromApplication(deviceInfo);
                }

                for (int i = 0; i < userIds.size(); ++i) {
                    if (memberMapper.getUserLoginoutStatus(userIds.get(i).getUserId()).getLoginoutStatus().equals("Y")) {
                        conMap.put("pushYn", pushYnList.get(i).getFPushYn());
                        conMap.put("modelCode", common.getModelCodeFromDeviceId(deviceId).replaceAll(" ", ""));
                        conMap.put("targetToken", memberMapper.getPushTokenByUserId(userIds.get(i).getUserId()).getPushToken());
                        conMap.put("userNickname", userNickname.getUserNickname());
                        conMap.put("deviceNick", common.returnDeviceNickname(deviceId, deviceType));
                        conMap.put("title", "hwTp");
                        conMap.put("deviceId", deviceId);
                        conMap.put("id", "WaterTempertureSet ID");
                        String jsonString = objectMapper.writeValueAsString(conMap);
                        mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);
                    }
                }

                common.insertHistory(
                        "1",
                        "WaterTempertureSet",
                        "hwTp",
                        "온수 온도 설정",
                        "0",
                        deviceId, // 각방의 Sub DeviceId로 수정
                        params.getUserId(),
                        "hwTp",
                        params.getTemperture(),
                        deviceType);

            }

            log.info("result: {}", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
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
        String modeCode = params.getModeCode();
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
            serialNumber = common.getHexSerialNumberFromDeviceId(deviceId);

            fastHotWaterSet.setUserId(params.getUserId());
            fastHotWaterSet.setDeviceId(params.getDeviceId());
            fastHotWaterSet.setControlAuthKey(params.getControlAuthKey());
            fastHotWaterSet.setFtMdSet(params.getModeCode());
            fastHotWaterSet.setFunctionId("ftMd");
            fastHotWaterSet.setUuId(common.getTransactionId());

            redisValue = params.getUserId() + "," + fastHotWaterSet.getFunctionId();
            redisCommand.setValues(fastHotWaterSet.getUuId(), redisValue);
            response = mobiusResponse = mobiusService.createCin(serialNumber, userId, JSON.toJson(fastHotWaterSet));

            if (!response.getResponseCode().equals("201")) {
                msg = "중계서버 오류";
                result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            try {
                // 메시징 시스템을 통해 응답 메시지 대기
                responseMessage = gwMessagingSystem.waitForResponse("ftMd" + fastHotWaterSet.getUuId(), TIME_OUT,
                        TimeUnit.SECONDS);
                if (responseMessage == null)
                    stringObject = "T";
                else {
                    if (responseMessage.equals("0"))
                        stringObject = "Y";
                    else
                        stringObject = "N";
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                log.error("", e);
            }

            gwMessagingSystem.removeMessageQueue("ftMd" + fastHotWaterSet.getUuId());
            redisCommand.deleteValues(fastHotWaterSet.getUuId());

            if (responseMessage != null && responseMessage.equals("2")) {
                conMap.put("body", "RemoteController WIFI ERROR");
                msg = "기기 네트워크 연결 오류. 잠시후 다시 시도하십시오";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1014, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else {
                if (stringObject.equals("Y")) {
                    conMap.put("body", "FastHotWaterSet " + modeCode);
                    msg = "빠른온수 설정 성공";
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                } else if (stringObject.equals("N")) {
                    conMap.put("body", "FastHotWaterSet " + modeCode);
                    msg = "빠른온수 설정 실패";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                } else {
                    conMap.put("body", "Service TIME-OUT");
                    msg = "기기 네트워크 연결 오류. 잠시후 다시 시도하십시오";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1015, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                household = memberMapper.getHouseholdByUserId(params.getUserId());
                params.setGroupId(household.getGroupId());
                List<AuthServerDTO> userIds = memberMapper.getUserIdsByDeviceId(params);
                List<AuthServerDTO> pushYnList = memberMapper.getPushYnStatusByUserIds(userIds);
                userNickname = memberMapper.getUserNickname(params.getUserId());
                userNickname.setUserNickname(common.stringToHex(userNickname.getUserNickname()));

                for (int i = 0; i < userIds.size(); ++i) {
                    if (memberMapper.getUserLoginoutStatus(userIds.get(i).getUserId()).getLoginoutStatus().equals("Y")) {
                        conMap.put("pushYn", pushYnList.get(i).getFPushYn());
                        conMap.put("modelCode", common.getModelCodeFromDeviceId(deviceId).replaceAll(" ", ""));
                        conMap.put("targetToken", memberMapper.getPushTokenByUserId(userIds.get(i).getUserId()).getPushToken());
                        conMap.put("userNickname", userNickname.getUserNickname());
                        conMap.put("deviceNick", common.returnDeviceNickname(deviceId));
                        conMap.put("title", "ftMd");
                        conMap.put("deviceId", deviceId);
                        conMap.put("id", "FastHotWaterSet ID");
                        String jsonString = objectMapper.writeValueAsString(conMap);
                        mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);
                    }
                }

                deviceInfo.setFtMd(params.getModeCode());
                deviceInfo.setDeviceId(deviceId);
                deviceMapper.updateDeviceStatusFromApplication(deviceInfo);

                common.insertHistory(
                        "1",
                        "FastHotWaterSet",
                        "ftMd",
                        "빠른 온수 설정",
                        "0",
                        deviceId,
                        params.getUserId(),
                        "빠른온수 설정",
                        "빠른온수 " + params.getModeCode(),
                        "01");

            }
            log.info("result: {}", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /** 잠금 모드 설정 */
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
            serialNumber = common.getHexSerialNumberFromDeviceId(deviceId);

            lockSet.setUserId(params.getUserId());
            lockSet.setDeviceId(params.getDeviceId());
            lockSet.setControlAuthKey(params.getControlAuthKey());
            lockSet.setLockSet(params.getLockSet());
            lockSet.setFunctionId("fcLc");
            lockSet.setUuId(common.getTransactionId());

            redisValue = params.getUserId() + "," + lockSet.getFunctionId();
            redisCommand.setValues(lockSet.getUuId(), redisValue);
            response = mobiusService.createCin(common.stringToHex("    " + serialNumber), userId, JSON.toJson(lockSet));

            if (!response.getResponseCode().equals("201")) {
                msg = "중계서버 오류";
                result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            try {
                // 메시징 시스템을 통해 응답 메시지 대기
                responseMessage = gwMessagingSystem.waitForResponse("fcLc" + lockSet.getUuId(), TIME_OUT, TimeUnit.SECONDS);
                gwMessagingSystem.removeMessageQueue("fcLc" + lockSet.getUuId());
                if (responseMessage == null)
                    stringObject = "T";
                else {
                    if (responseMessage.equals("0"))
                        stringObject = "Y";
                    else
                        stringObject = "N";
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                log.error("", e);
            }

            gwMessagingSystem.removeMessageQueue("fcLc" + lockSet.getUuId());
            redisCommand.deleteValues(lockSet.getUuId());

            if (responseMessage != null && responseMessage.equals("2")) {
                conMap.put("body", "RemoteController WIFI ERROR");
                msg = "기기 네트워크 연결 오류. 잠시후 다시 시도하십시오";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1014, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else {
                if (stringObject.equals("Y")) {
                    conMap.put("body", "LockSet OK");
                    msg = "잠금 모드 설정 성공";
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                } else if (stringObject.equals("N")) {
                    conMap.put("body", "LockSet FAIL");
                    msg = "잠금 모드 설정 실패";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                } else {
                    conMap.put("body", "Service TIME-OUT");
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

                deviceInfo.setFcLc(params.getLockSet());
                deviceInfo.setDeviceId(deviceId);
                deviceMapper.updateDeviceStatusFromApplication(deviceInfo);

                for (int i = 0; i < userIds.size(); ++i) {
                    if (memberMapper.getUserLoginoutStatus(userIds.get(i).getUserId()).getLoginoutStatus().equals("Y")) {
                        conMap.put("pushYn", pushYnList.get(i).getFPushYn());
                        conMap.put("modelCode", common.getModelCodeFromDeviceId(deviceId).replaceAll(" ", ""));
                        conMap.put("targetToken", memberMapper.getPushTokenByUserId(userIds.get(i).getUserId()).getPushToken());
                        conMap.put("userNickname", userNickname.getUserNickname());
                        conMap.put("deviceNick", common.returnDeviceNickname(deviceId));
                        conMap.put("title", "fcLc");
                        conMap.put("deviceId", deviceId);
                        conMap.put("id", "LockSet ID");
                        String jsonString = objectMapper.writeValueAsString(conMap);
                        mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);
                    }
                }
            }

            common.insertHistory(
                    "1",
                    "LockSet",
                    "fcLc",
                    "잠금 모드 설정",
                    "0",
                    deviceId,
                    params.getUserId(),
                    "잠금 변경",
                    "잠금 " + params.getLockSet(),
                    "01");

            log.info("result: {}", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /** 홈 IoT 컨트롤러 상태 정보 조회 – 홈 화면 */
    @Override
    public ResponseEntity<?> doBasicDeviceStatusInfo(AuthServerDTO params) throws CustomException {

        /*
         * 구현전 생각해야 할 것
         * 1. 몇개의 응답을 올지 모름 (사용자가 몇개의 기기를 등록했는지 알아야함)
         * 2. 받은 응답을 어떻게 Passing 할 것인가
         */

        ApiResponse.Data result = new ApiResponse.Data();
        String msg;

        String userId = params.getUserId();
        String uuId = common.getTransactionId();
        List<AuthServerDTO> groupInfo;

        List<Map<String, String>> appResponse = new ArrayList<>();
        List<AuthServerDTO> groupIdList;
        List<DeviceStatusInfo.Device> devicesStatusInfo;
        try {

            // 1. 사용자 그룹 정보 가져오기
            groupInfo = memberMapper.getGroupIdByUserId(userId);
            if (groupInfo == null || groupInfo.isEmpty()) {
                msg = "그룹 정보가 없습니다.";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1016, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            // 1. UserId로 그룹 정보 쿼리
            // 2. groupId로 Regist 테이블과 기기 테이블에서 필요한 정보 모두 쿼리
            groupIdList = deviceMapper.getGroupIdByUserId(userId);
            // devicesStatusInfo = deviceMapper.getDeviceStatusInfo(groupIdList);
            // 5. 데이터 매핑
            for (AuthServerDTO authServerDTO : groupIdList) {
                devicesStatusInfo = deviceMapper.getDeviceStatusInfo(authServerDTO.getGroupIdx()); // Return List
                for (int i = 0; i < devicesStatusInfo.size(); i++) {
                    Map<String, String> data = new HashMap<>();
                    data.put("rKey", devicesStatusInfo.get(i).getRKey());
                    data.put("modelCategoryCode", common.getModelCode(common.getModelCodeFromDeviceId(devicesStatusInfo.get(i).getDeviceId()).trim()));
                    data.put("deviceNickname", devicesStatusInfo.get(i).getDeviceNickName());
                    data.put("groupIdx", devicesStatusInfo.get(i).getGroupIdx());
                    data.put("groupName", devicesStatusInfo.get(i).getGroupName());
                    data.put("regSort", String.valueOf(i + 1));
                    data.put("deviceId", devicesStatusInfo.get(i).getDeviceId());
                    data.put("latitude", devicesStatusInfo.get(i).getLatitude());
                    data.put("longitude", devicesStatusInfo.get(i).getLongitude());
                    data.put("controlAuthKey", devicesStatusInfo.get(i).getRKey());
                    data.put("tmpRegistKey", devicesStatusInfo.get(i).getTmpRegistKey());
                    data.put("deviceStatus", devicesStatusInfo.get(i).getDvSt());
                    data.put("powr", devicesStatusInfo.get(i).getPowr());
                    data.put("opMd", devicesStatusInfo.get(i).getOpMd());
                    data.put("htTp", devicesStatusInfo.get(i).getHtTp());
                    data.put("wtTp", devicesStatusInfo.get(i).getWtTp());
                    data.put("hwTp", devicesStatusInfo.get(i).getHwTp());
                    data.put("ftMd", devicesStatusInfo.get(i).getFtMd());
                    data.put("chTp", devicesStatusInfo.get(i).getChTp());
                    data.put("bCdt", devicesStatusInfo.get(i).getBCdt());
                    data.put("mfDt", devicesStatusInfo.get(i).getMfDt());
                    data.put("hwSt", devicesStatusInfo.get(i).getHwSt());
                    data.put("fcLc", devicesStatusInfo.get(i).getFcLc());
                    data.put("blCf", devicesStatusInfo.get(i).getBlCf());
                    String type24h = common.readCon(devicesStatusInfo.get(i).getH24(), "serviceMd");
                    if (type24h == null || type24h.isEmpty()) {
                        data.put("type24h", "");
                    } else {
                        data.put("type24h", type24h);
                    }
                    data.put("slCd", devicesStatusInfo.get(i).getSlCd());
                    data.put("vtSp", devicesStatusInfo.get(i).getVtSp());
                    data.put("inAq", devicesStatusInfo.get(i).getInAq());
                    data.put("odHm", devicesStatusInfo.get(i).getOdHm());
                    data.put("ftMdAcTv", devicesStatusInfo.get(i).getFtMdActv());
                    data.put("fcLcAcTv", devicesStatusInfo.get(i).getFcLcActv());
                    data.put("ecOpAcTv", devicesStatusInfo.get(i).getEcOp());
                    data.put("pastAcTv", devicesStatusInfo.get(i).getPast());
                    data.put("inDrAcTv", devicesStatusInfo.get(i).getInDr());
                    data.put("inClAcTv", devicesStatusInfo.get(i).getInCl());
                    data.put("ecStAcTv", devicesStatusInfo.get(i).getEcSt());
                    appResponse.add(data);
                    // clTp, otTp <= 히트펌프 value
                }
            }

            if (appResponse.isEmpty()) {
                msg = "기기정보가 없습니다.";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1016, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            msg = "기기 상태 정보 조회 - 홈 화면 성공";
            result.setHomeViewValue(appResponse);
            result.setResult(ApiResponse.ResponseType.HTTP_200, msg);

            redisCommand.deleteValues(uuId);

            log.info("result: {}", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
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

            if (resultDto == null) {
                rtCode = "1018";
                msg = "기기 정보 조회 실패";
            } else {
                rtCode = "200";
                msg = "기기 정보 조회 성공";

                result.put("modelCategoryCode", resultDto.getModelCode());
                result.put("deviceNickname", resultDto.getDeviceNickname());
                result.put("zipCode", resultDto.getZipCode());
                result.put("oldAddr", resultDto.getOldAddr());
                result.put("newAddr", resultDto.getNewAddr());
                result.put("addrDetail", resultDto.getAddrDetail());
                result.put("latitude", resultDto.getLatitude());
                result.put("longitude", resultDto.getLongitude());
                result.put("groupName", resultDto.getGroupName());
                result.put("groupIdx", resultDto.getGroupIdx());
            }
            result.put("resultCode", rtCode);
            result.put("resultMsg", msg);

            log.info("result: {}", result);
            return result;
        } catch (Exception e) {
            log.error("", e);
            return result;
        }
    }

    /** 홈 IoT 컨트롤러 에러 정보 조회 */
    @Override
    public ResponseEntity<?> doDeviceErrorInfo(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        String msg;
        String deviceId = params.getDeviceId();
        AuthServerDTO errorInfo;

        try {

        errorInfo = deviceMapper.getErrorInfoByDeviceId(deviceId);

        if(errorInfo == null){
            msg = "에러 정보가 없습니다.";
            result.setResult(ApiResponse.ResponseType.CUSTOM_1016, msg);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } 

        result.setErrorCode(errorInfo.getErrorCode());
        result.setErrorDatetime(errorInfo.getErrorDateTime());
        result.setErrorVersion(errorInfo.getErrorVersion());
        result.setGroupName(errorInfo.getGroupName());
        result.setDeviceNickname(errorInfo.getDeviceNickname());

        msg = "기기 에러 정보 조회 성공";
    
        result.setResult(ApiResponse.ResponseType.HTTP_200, msg);

        log.info("result: {}", result);
        return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
        log.error("", e);
        return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /** 홈 IoT 정보 조회 - 리스트 */
    @Override
    public ResponseEntity<?> doDeviceInfoSearchList(AuthServerDTO params) throws CustomException {

        /*
         * 1. UserId 로 DeviceId 취득 getUserByDeviceId (등록된 모든 Device ID)
         * 2. DeviceId로 필요한 Data를 쿼리 (TBT_OPR_DEVICE_REGIST, TBR_IOT_DEVICE)
         */

        String groupIdx = params.getGroupIdxList();
        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject = "N";
        String msg = null;
        List<String> groupIdxList;
        List<Map<String, String>> appResponse = new ArrayList<>();
        List<AuthServerDTO> deviceInfoList;

        String valveStatus = params.getValveStatus();

        try {

            groupIdxList = Arrays.asList(groupIdx.split(","));

            // 일시적으로 각방 선택 X
            // Y일 경우 각방 포함 쿼리
            if (valveStatus == null || valveStatus.equals("N")) {
                deviceInfoList = deviceMapper.getDeviceInfoSearchIdx(groupIdxList);
            } else if (valveStatus.equals("Y")) {
                deviceInfoList = deviceMapper.getDeviceInfoSearchIdxTemp(groupIdxList);
            } else {
                deviceInfoList = deviceMapper.getDeviceInfoSearchIdx(groupIdxList);
            }

            int i = 1;
            if (!deviceInfoList.isEmpty()) {
                for (AuthServerDTO authServerDTO : deviceInfoList) {
                    Map<String, String> data = new HashMap<>();
                    data.put("modelCode", authServerDTO.getModelCode());
                    data.put("deviceNickname", authServerDTO.getDeviceNickname());
                    data.put("zipCode", authServerDTO.getZipCode());
                    data.put("oldAddr", authServerDTO.getOldAddr());
                    data.put("newAddr", authServerDTO.getNewAddr());
                    data.put("addrDetail", authServerDTO.getAddrDetail());
                    data.put("latitude", authServerDTO.getLatitude());
                    data.put("longitude", authServerDTO.getLongitude());
                    data.put("regSort", String.valueOf(i));
                    data.put("deviceId", authServerDTO.getDeviceId());
                    data.put("controlAuthKey", authServerDTO.getControlAuthKey());
                    data.put("tempKey", authServerDTO.getTmpRegistKey());
                    data.put("groupIdx", authServerDTO.getGroupIdx());
                    data.put("groupName", authServerDTO.getGroupName());
                    data.put("userId", authServerDTO.getUserId());
                    appResponse.add(data);
                    i++;
                }
                stringObject = "Y";
            }

            if (stringObject.equals("Y")) {
                msg = "기기 조회 리스트 - 조회 성공";
                result.setHomeViewValue(appResponse);
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            }

            if (stringObject.equals("N")) {
                msg = "기기 정보 조회 리스트 - 조회 결과 없음";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1016, msg);
            }
            result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            log.info("result: {}", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /** 홈 IoT 컨트롤러 풍량 단수 설정 */
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

        try {

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
            serialNumber = common.getHexSerialNumberFromDeviceId(deviceId);

            stringObject = "Y";
            response = mobiusService.createCin(serialNumber, userId, JSON.toJson(fanSpeedSet));
            if (!response.getResponseCode().equals("201")) {
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
                    if (responseMessage.equals("0"))
                        stringObject = "Y";
                    else
                        stringObject = "N";
                } else {
                    // 타임아웃이나 응답 없음 처리
                    stringObject = "T";
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                log.error("", e);
            }

            gwMessagingSystem.removeMessageQueue("VentilationFanSpeedSet" + fanSpeedSet.getUuId());
            redisCommand.deleteValues(fanSpeedSet.getUuId());

            if (responseMessage != null && responseMessage.equals("2")) {
                conMap.put("body", "RemoteController WIFI ERROR");
                msg = "기기 네트워크 연결 오류. 잠시후 다시 시도하십시오";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1014, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else {
                if (stringObject.equals("Y")) {
                    conMap.put("body", "ventilationFanSpeedSet OK");
                    msg = "풍량 단수 설정 성공";
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                } else {
                    conMap.put("body", "Service TIME-OUT");
                    msg = "기기 응답이 없습니다. 잠시후 다시 시도하십시오";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1015, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }

                redisCommand.deleteValues(fanSpeedSet.getUuId());

                deviceInfo.setVtSp(fanSpeed);
                deviceInfo.setDeviceId(deviceId);
                deviceMapper.updateDeviceStatusFromApplication(deviceInfo);

                household = memberMapper.getHouseholdByUserId(params.getUserId());
                params.setGroupId(household.getGroupId());
                List<AuthServerDTO> userIds = memberMapper.getUserIdsByDeviceId(params);
                List<AuthServerDTO> pushYnList = memberMapper.getPushYnStatusByUserIds(userIds);
                userNickname = memberMapper.getUserNickname(params.getUserId());
                userNickname.setUserNickname(common.stringToHex(userNickname.getUserNickname()));

                for (int i = 0; i < userIds.size(); ++i) {
                    if (memberMapper.getUserLoginoutStatus(userIds.get(i).getUserId()).getLoginoutStatus().equals("Y")) {
                        conMap.put("targetToken", memberMapper.getPushTokenByUserId(userIds.get(i).getUserId()).getPushToken());
                        conMap.put("title", "vtSp");
                        conMap.put("vtSp", params.getFanSpeed());
                        conMap.put("userNickname", userNickname.getUserNickname());
                        conMap.put("deviceNick", common.returnDeviceNickname(deviceId));
                        conMap.put("pushYn", pushYnList.get(i).getFPushYn());
                        conMap.put("modelCode", modelCode);
                        conMap.put("deviceId", deviceId);
                        String jsonString = objectMapper.writeValueAsString(conMap);
                        mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);
                    }
                }

                common.insertHistory(
                        "1",
                        "VentilationFanSpeedSet",
                        "vtSp",
                        "풍량 단수 설정",
                        "0",
                        deviceId,
                        params.getUserId(),
                        "vtSp",
                        params.getFanSpeed(),
                        "07");
            }

            log.info("result: {}", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
        }
        return null;
    }

    /** 홈 IoT 컨트롤러 활성/비활성 정보 요청 */
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

            if (modelCode.equals(modelCodeMap.get("newModel")) || modelCode.equals(modelCodeMap.get("oldModel")))
                functionId = "acTv";
            else if (modelCode.equals(modelCodeMap.get("ventilation")))
                functionId = "acTv";

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
                response = mobiusService.createCin(common.stringToHex("    " + serialNumber), userId,
                        JSON.toJson(activeStatus));
                if (!response.getResponseCode().equals("201")) {
                    msg = "중계서버 오류";
                    result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }
            }

            try {
                // 메시징 시스템을 통해 응답 메시지 대기
                gwMessagingSystem.printMessageQueues();
                responseMessage = gwMessagingSystem.waitForResponse(
                        activeStatus.getFunctionId() + activeStatus.getUuId(), TIME_OUT, TimeUnit.SECONDS);
                if (responseMessage != null) {
                    // 응답 처리
                    if (responseMessage.equals("0"))
                        stringObject = "Y";
                    else
                        stringObject = "N";
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

            if (responseMessage != null && responseMessage.equals("2")) {
                msg = "기기 네트워크 연결 오류. 잠시후 다시 시도하십시오";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1014, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else {
                if (stringObject.equals("Y")) {
                    msg = "홈 IoT 컨트롤러 활성/비활성 정보 요청 성공";
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                    result.setTestVariable(responseMessage);
                } else {
                    msg = "기기 응답이 없습니다. 잠시후 다시 시도하십시오";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1015, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }
            }
            log.info("result: {}", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
        }
        return null;
    }

    /** 홈 IoT 컨트롤러 상태 정보 조회 (각방) – 홈 화면 */
    @Override
    public ResponseEntity<?> doBasicRoomDeviceStatusInfo(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        String userId = params.getUserId();

        AuthServerDTO valveStatus;
        List<AuthServerDTO> valveStatusList;
        List<DeviceStatusInfo.Device> valveDetailedStatusList;
        List<Map<String, String>> roomList;
        List<Map<String, Object>> appResponse = new ArrayList<>();

        try {
            // 사용자 그룹 정보 가져온후 VALVE_STATUS중 Y가 없는 경우 해당 사용자는 각방 기기가 없다 판단.
            valveStatus = memberMapper.checkValveStatusByUserId(userId);
            // valveStatus가 null이거나 각방 기기 수가 0이면, 각방이 없다고 판단
            if (valveStatus == null || "0".equals(valveStatus.getValveCount())) {
                result.setResult(ApiResponse.ResponseType.CUSTOM_1016, "등록한 각방 없음");
                return ResponseEntity.ok(result);
            }

            // 2) 등록된 각방(밸브) 리스트 조회
            valveStatusList = memberMapper.getValveStatusList(userId);
            appResponse = new ArrayList<>();

            int regSort = 1;

            for (AuthServerDTO authServerDTO : valveStatusList) {
                Map<String, Object> data = new HashMap<>();
                data.put("tmpRegistKey", authServerDTO.getTmpRegistKey());
                data.put("controlAuthKey", authServerDTO.getControlAuthKey());
                data.put("deviceNickname", authServerDTO.getDeviceNickname());
                data.put("modelCategoryCode", "05");
                data.put("groupIdx", authServerDTO.getGroupIdx());
                data.put("groupName", authServerDTO.getGroupName());
                data.put("deviceId", authServerDTO.getDeviceId());
                data.put("latitude", authServerDTO.getLatitude());
                data.put("longitude", authServerDTO.getLongitude());

                // 3) 각 디바이스(각방)의 상세 상태 조회
                valveDetailedStatusList = deviceMapper.getDetailedValveStatus(authServerDTO.getDeviceId());
                roomList = new ArrayList<>();

                for (DeviceStatusInfo.Device deviceInfo : valveDetailedStatusList) {
                    Map<String, String> deviceMap = new HashMap<>();
                    deviceMap.put("deviceNickname", deviceInfo.getDeviceNickName());
                    deviceMap.put("regSort", String.valueOf(regSort));
                    deviceMap.put("deviceId", deviceInfo.getDeviceId());
                    deviceMap.put("deviceStatus", deviceInfo.getDvSt());
                    deviceMap.put("powr", deviceInfo.getPowr());
                    deviceMap.put("opMd", deviceInfo.getOpMd());
                    deviceMap.put("htTp", deviceInfo.getHtTp());
                    deviceMap.put("hwTp", deviceInfo.getHwTp());
                    deviceMap.put("chTp", deviceInfo.getChTp());
                    deviceMap.put("mfDt", deviceInfo.getMfDt());
                    roomList.add(deviceMap);
                    regSort++;
                }
                data.put("roomList", roomList);
                appResponse.add(data);
            }

            // 4) 결과 데이터 설정
            result.setHomeViewValue(appResponse);
            result.setResult(ApiResponse.ResponseType.HTTP_200, "홈 IoT 컨트롤러 상태 정보 조회 성공");

            log.info("result: {}", result);
            return ResponseEntity.ok(result);
        } catch (CustomException ce) {
            // CustomException 처리가 필요하다면 따로 분기 가능
            log.error("Custom exception occurred: ", ce);
            // 예시) 커스텀 예외에 따른 결과 설정
            result.setResult(ApiResponse.ResponseType.CUSTOM_9999, ce.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);

        } catch (Exception e) {
            // 그 외 일반 예외 처리
            log.error("Exception while retrieving room device status info", e);
            result.setResult(ApiResponse.ResponseType.CUSTOM_9999, "기기 상태 조회 중 오류가 발생하였습니다.");
            // 필요하다면 에러 HTTP 상태코드를 다른 것으로 조정 가능 (예: 500)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /** 홈 IoT 컨트롤러 상태 정보 조회 (각방) */
    @Override
    public ResponseEntity<?> doRoomDeviceStatusInfo(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        String parentId = params.getParentDevice();

        List<Map<String, Object>> appResponse = new ArrayList<>();

        List<AuthServerDTO> parentIdList = null;
        List<DeviceStatusInfo.Device> valveBoxStatusList = null;

        try {

            parentIdList = deviceMapper.getParentIdByUserId(parentId);
            valveBoxStatusList = deviceMapper.getValveStatusByParentId(parentId);

            for (int i = 0; i < valveBoxStatusList.size(); i++) {
                Map<String, Object> data = new HashMap<>();
                data.put("controlAuthKey", parentIdList.get(0).getControlAuthKey());
                data.put("deviceId", valveBoxStatusList.get(i).getDeviceId());
                data.put("deviceNickname", valveBoxStatusList.get(i).getDeviceNickName());
                data.put("modelCategoryCode", "05");
                data.put("deviceStatus", "01");
                data.put("powr", valveBoxStatusList.get(i).getPowr());
                data.put("opMd", valveBoxStatusList.get(i).getOpMd());
                data.put("htTp", valveBoxStatusList.get(i).getHtTp());
                data.put("hwTp", valveBoxStatusList.get(i).getHwTp());
                data.put("chTp", valveBoxStatusList.get(i).getChTp());
                data.put("mfDt", valveBoxStatusList.get(i).getMfDt());
                ConcurrentHashMap<String, String> eleMap = new ConcurrentHashMap<>();
                eleMap.put("12h", valveBoxStatusList.get(i).getH12());
                data.put("rsCf", JSON.toJson(eleMap));
                appResponse.add(data);
            }
            result.setRoomList(appResponse);
            result.setResult(ApiResponse.ResponseType.HTTP_200, "홈 IoT 컨트롤러 상태 정보 조회 성공");

            log.info("result: {}", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
        }

        return null;
    }

    /** FCNT 요청 호출 */
    @Override
    public ResponseEntity<?> doCallFcNt(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        FcNtCall fcNtCall = new FcNtCall();
        String stringObject;
        String msg;
        String userId;
        String deviceId = params.getDeviceId();
        String parentDevice = params.getParentDevice();
        String serialNumber;
        String modelCode;

        AuthServerDTO firstDeviceUser;

        MobiusResponse response;

        try {
            modelCode = common.getModelCodeFromDeviceId(deviceId);

            firstDeviceUser = memberMapper.getFirstDeviceUser(parentDevice);
            userId = firstDeviceUser.getUserId();

            fcNtCall.setDeviceId(deviceId);
            fcNtCall.setUuId(common.getTransactionId());
            fcNtCall.setFunctionId("fcNt");
            fcNtCall.setModelCode(modelCode);

            serialNumber = common.getHexSerialNumberFromDeviceId(deviceId);

            stringObject = "Y";
            response = mobiusService.createCin("20" + serialNumber, userId, JSON.toJson(fcNtCall));
            if (!response.getResponseCode().equals("201")) {
                msg = "중계서버 오류";
                result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            msg = "FCNT 요청 호출 성공";
            result.setResult(ApiResponse.ResponseType.HTTP_200, msg);

            log.info("result: {}", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
        }
        return null;
    }
}
