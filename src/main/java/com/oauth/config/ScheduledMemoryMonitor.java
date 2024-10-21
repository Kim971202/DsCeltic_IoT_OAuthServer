package com.oauth.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledMemoryMonitor {

    @Autowired
    ScheduledSafeAlarm scheduledSafeAlarm;

    // 10초마다 메모리 상태를 출력
    @Scheduled(fixedRate = 10000)
    public void monitorMemory() {
        MemoryMonitor.logMemoryUsage();
    }

    // 10초마다 메모리 상태를 출력
    @Scheduled(fixedRate = 10000)
    public void monitorSafeAlarm() {
//        scheduledSafeAlarm.checkUserSafeAlarm();
    }
}