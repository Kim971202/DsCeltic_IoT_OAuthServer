package com.oauth.config;

import com.oauth.dto.AuthServerDTO;
import com.oauth.mapper.MemberMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ScheduledSafeAlarm {

    private static MemberMapper memberMapper;

    public static void checkUserSafeAlarm(){
        AuthServerDTO userInfo = new AuthServerDTO();

        userInfo = memberMapper.getSafeAlarmSet();

        if(userInfo != null) System.out.println(userInfo.getUserId());
        else System.out.println("userInfo is NULL");
    }

}
