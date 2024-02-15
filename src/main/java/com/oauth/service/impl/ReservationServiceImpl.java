package com.oauth.service.impl;

import com.oauth.constants.MobiusResponse;
import com.oauth.dto.AuthServerDTO;
import com.oauth.dto.gw.DeviceStatusInfoDR910W;
import com.oauth.dto.gw.Set12;
import com.oauth.dto.gw.Set24;
import com.oauth.response.ApiResponse;
import com.oauth.service.mapper.ReservationService;
import com.oauth.utils.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

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

    /** 24시간 예약 */
    @Override
    public ResponseEntity<?> doSet24(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        Set24 set24 = new Set24();
        String stringObject = null;
        String msg = null;

        String userId = params.getUserId();

        try {

            set24.setAccessToken(params.getAccessToken());
            set24.setUserId(params.getUserId());
            set24.setDeviceId(params.getDeviceId());
            set24.setControlAuthKey(params.getControlAuthKey());
            set24.setHours(params.getHours());
            set24.setType24h(params.getType24h());
            set24.setOnOffFlag(params.getOnOffFlag());

            set24.setFunctionId("24h");
            set24.setUuId(common.getTransactionId());

            redisCommand.setValues(set24.getUuId(), userId);
            mobiusResponse = mobiusService.createCin("gwSever", "gwSeverCnt", JSON.toJson(set24));

            if(mobiusResponse.getResponseCode().equals("201")) stringObject = "Y";
            else stringObject = "N";

            if(stringObject.equals("Y")) msg = "24시간 예약 성공";
            else msg = "24시간 예약 실패";

            result.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);

            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    /** 반복(12시간) 예약 */
    @Override
    public ResponseEntity<?> doSet12(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        Set12 set12 = new Set12();
        String stringObject = null;
        String msg = null;

        String userId = params.getUserId();

        try {

            set12.setAccessToken(params.getAccessToken());
            set12.setUserId(userId);
            set12.setDeviceId(params.getDeviceId());
            set12.setControlAuthKey(params.getControlAuthKey());
            set12.setWorkPeriod(params.getWorkPeriod());
            set12.setWorkTime(params.getWorkTime());
            set12.setOnOffFlag(params.getOnOffFlag());

            set12.setFunctionId("12h");
            set12.setUuId(common.getTransactionId());

            redisCommand.setValues(set12.getUuId(), userId);
            mobiusResponse = mobiusService.createCin("gwSever", "gwSeverCnt", JSON.toJson(set12));

            if(mobiusResponse.getResponseCode().equals("201")) stringObject = "Y";
            else stringObject = "N";

            if(stringObject.equals("Y")) msg = "12시간 예약 성공";
            else msg = "12시간 예약 실패";

            result.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);

            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }
}
