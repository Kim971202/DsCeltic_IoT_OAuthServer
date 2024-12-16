package com.oauth.config;

import com.oauth.dto.AuthServerDTO;
import com.oauth.dto.gw.MfAr;
import com.oauth.mapper.DeviceMapper;
import com.oauth.mapper.MemberMapper;
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
public class MfArCall {

    @Autowired
    private DeviceMapper deviceMapper;
    @Autowired
    private MemberMapper memberMapper;
    @Autowired
    private Common common;
    @Autowired
    MobiusService mobiusService;

    public void callMfAr() throws Exception{

        List<String> deviceIdList = deviceMapper.getDeviceIdByDeviceModelCode();

        for(String deviceId : deviceIdList){
            MfAr mfAr = new MfAr();

            mfAr.setAccessToken("AccessToken");
            mfAr.setUserId(deviceId);
            mfAr.setDeviceId(deviceId);
            mfAr.setControlAuthKey("0000");

            mfAr.setFunctionId("mfAr");
            mfAr.setUuId(common.getTransactionId());

            AuthServerDTO device = deviceMapper.getSingleSerialNumberBydeviceId(deviceId);
            AuthServerDTO userId = memberMapper.getFirstDeviceUser(deviceId);

            mobiusService.createCin(common.stringToHex("    " + device.getSerialNumber()), userId.getUserId(), JSON.toJson(mfAr));
        }

    }

}
