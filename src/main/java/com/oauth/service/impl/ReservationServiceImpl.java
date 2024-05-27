package com.oauth.service.impl;

import com.oauth.constants.MobiusResponse;
import com.oauth.dto.AuthServerDTO;
import com.oauth.dto.gw.*;
import com.oauth.mapper.DeviceMapper;
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
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.*;
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
        String redisValue;
        MobiusResponse response;
        String responseMessage;
        AuthServerDTO device;
        try {

            device = deviceMapper.getSingleSerialNumberBydeviceId(params.getDeviceId());

            set24.setAccessToken(params.getAccessToken());
            set24.setUserId(params.getUserId());
            set24.setDeviceId(params.getDeviceId());
            set24.setControlAuthKey(params.getControlAuthKey());
            set24.setHours(params.getHours());
            set24.setType24h(params.getType24h());
            set24.setOnOffFlag(params.getOnOffFlag());

            set24.setFunctionId("24h");
            set24.setUuId(common.getTransactionId());

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
        String responseMessage;
        String redisValue;
        MobiusResponse response;
        AuthServerDTO device;
        try {

            device = deviceMapper.getSingleSerialNumberBydeviceId(params.getDeviceId());

            set12.setAccessToken(params.getAccessToken());
            set12.setUserId(userId);
            set12.setDeviceId(params.getDeviceId());
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

        AwakeAlarmSet awakeAlarmSet = new AwakeAlarmSet();
        List<HashMap<String, Object>> awakeList = new ArrayList<HashMap<String, Object>>();
        HashMap<String, Object> map = new HashMap<>();

        String redisValue;
        MobiusResponse response;
        String responseMessage;
        AuthServerDTO device;
        try {

            device = deviceMapper.getSingleSerialNumberBydeviceId(params.getDeviceId());

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
            awakeAlarmSet.setDeviceId(params.getDeviceId());
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

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject = null;
        String msg;
        String userId = params.getUserId();

        SetWeek setWeek = new SetWeek();
        List<HashMap<String, Object>> weekList = new ArrayList<HashMap<String, Object>>();
        HashMap<String, Object> map = new HashMap<>();

        String responseMessage;
        String redisValue;
        MobiusResponse response;
        AuthServerDTO device;
        try {

            device = deviceMapper.getSingleSerialNumberBydeviceId(params.getDeviceId());

            setWeek.setUserId(params.getUserId());
            setWeek.setDeviceId(params.getDeviceId());
            setWeek.setControlAuthKey(params.getControlAuthKey());
            setWeek.setFunctionId("7wk");
            setWeek.setUuId(common.getTransactionId());
            setWeek.setOnOffFlag(params.getOnOffFlag());
            log.info("params.getTimeWeek(): " + Arrays.deepToString(params.getTimeWeek()));
            for(int i = 0 ; i < params.getTimeWeek().length; ++i){
                map.put("wk", params.getDayWeek()[i]);
                map.put("hs", Arrays.asList(params.getTimeWeek()[i]));
                weekList.add(map);
                map = new HashMap<>();
            }

            setWeek.setWeekList(weekList);

            redisValue = userId + "," + setWeek.getFunctionId();
            redisCommand.setValues(setWeek.getUuId(), redisValue);

            System.out.println(JSON.toJson(setWeek, true));

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

            redisCommand.deleteValues(setWeek.getUuId());
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
        }
        return null;
    }
}
