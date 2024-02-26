package com.oauth.jwt;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class ServerUtils {

    public LocalDateTime getTimeAsiaSeoulNow(){
        return getTimeNow();
    }

    private LocalDateTime getTimeNow(){
        return LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }
}
