package com.oauth.config;

import com.oauth.dto.AuthServerDTO;
import com.oauth.mapper.MemberMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class ScheduledSafeAlarm {

    @Autowired
    private MemberMapper memberMapper;

    public void checkUserSafeAlarm(){
        List<AuthServerDTO> userInfo;
        List<AuthServerDTO> pushInfo;

        userInfo = memberMapper.getSafeAlarmSet();

        if(userInfo != null && !userInfo.isEmpty()){
            pushInfo = memberMapper.getPushTokenByUserIds(userInfo);
            for(AuthServerDTO authServerDTO : userInfo){
                System.out.println(authServerDTO.getUserId());
            }
        } else System.out.println("userInfo is NULL");
    }

}
