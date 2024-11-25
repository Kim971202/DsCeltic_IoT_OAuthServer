package com.oauth.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledMemoryMonitor {

    @Autowired
    ScheduledSafeAlarm scheduledSafeAlarm;

    // 10분마다 메모리 상태를 출력
    @Scheduled(fixedRate = 600000) // 600,000 밀리초 = 10분
    public void monitorMemory() {
        MemoryMonitor.logMemoryUsage();
    }

    @Scheduled(fixedRate = 5000) // 5,000 밀리초 = 5초
    public void safeAlarmMemory() throws Exception {
        scheduledSafeAlarm.checkUserSafeAlarm();
    }
}