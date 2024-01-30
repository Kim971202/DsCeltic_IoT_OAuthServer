package com.oauth.service;

import com.oauth.dto.authServerDTO;
import com.oauth.dto.gw.PowerOnOff;
import com.oauth.mapper.DeviceMapper;
import com.oauth.response.ApiResponse;
import com.oauth.utils.Common;
import com.oauth.utils.CustomException;
import com.oauth.utils.JSON;
import com.oauth.utils.RedisCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class DeviceService {

    @Autowired
    Common common;
    @Autowired
    MobiusService mobiusService;
    @Autowired
    DeviceMapper deviceMapper;

    @Autowired
    RedisCommand redisCommand;

    /** 전원 On/Off */
    public ResponseEntity<?> doPowerOnOff(authServerDTO params) throws CustomException{

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject = null;
        String msg = null;
        PowerOnOff powerOnOff = new PowerOnOff();
        String serialNumber = null;
        String userId = params.getUserId();
        String deviceId = params.getDeviceId();
        try {
            powerOnOff.setUserId(params.getUserId());
            powerOnOff.setDeviceId(params.getDeviceId());
            powerOnOff.setControlAuthKey(params.getControlAuthKey());
            powerOnOff.setDeviceType(params.getDeviceType());
            powerOnOff.setModelCode(params.getModelCode());
            powerOnOff.setPowerStatus(params.getPowerStatus());
            powerOnOff.setFunctionId("powerOnOff");
            powerOnOff.setUuId(common.getTransactionId());

            redisCommand.setValues(powerOnOff.getUuId(), userId);

            authServerDTO device = deviceMapper.getSerialNumberBydeviceId(deviceId);
            serialNumber = device.getSerialNumber();

            if(!serialNumber.isEmpty()) {
                stringObject = "Y";
                mobiusService.createCin(serialNumber, userId, JSON.toJson(powerOnOff));
            } else stringObject = "N";

            if(stringObject.equals("Y")) msg = "전원 On/Off 성공";
            else msg = "전원 On/Off 실패";

            result.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);

            return new ResponseEntity<>(result, HttpStatus.OK);
        }catch (Exception e){
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
}
