package com.oauth.config;

import com.oauth.dto.AuthServerDTO;
import com.oauth.dto.gw.Fcnt;
import com.oauth.mapper.DeviceMapper;
import com.oauth.service.impl.MobiusService;
import com.oauth.utils.Common;
import com.oauth.utils.JSON;
import com.oauth.utils.RedisCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class FcntCall {

    @Autowired
    private DeviceMapper deviceMapper;
    @Autowired
    private Common common;
    @Autowired
    RedisCommand redisCommand;
    @Autowired
    MobiusService mobiusService;

    public void callFcnt() throws Exception{

        List<String> deviceIdList = deviceMapper.getDeviceIdByDeviceModelCode();

        for(String deviceId : deviceIdList){
            Fcnt fcnt = new Fcnt();

            fcnt.setAccessToken("AccessToken");
            fcnt.setUserId(deviceId);
            fcnt.setDeviceId(deviceId);
            fcnt.setControlAuthKey("0000");

            fcnt.setFunctionId("fcnt");
            fcnt.setUuId(common.getTransactionId());

            String redisValue = fcnt.getUserId() + "," + fcnt.getFunctionId();
            redisCommand.setValues(fcnt.getUuId(), redisValue);

            AuthServerDTO device = deviceMapper.getSingleSerialNumberBydeviceId(deviceId);

            mobiusService.createCin(common.stringToHex("    " + device.getSerialNumber()), deviceId, JSON.toJson(fcnt));
        }

    }

}
