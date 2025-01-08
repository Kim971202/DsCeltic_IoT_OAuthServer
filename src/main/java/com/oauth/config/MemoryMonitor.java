package com.oauth.config;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MemoryMonitor {
    public static void logMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();

        log.info("Used Memory: " + usedMemory / (1024 * 1024) + " MB");
        log.info("Free Memory: " + freeMemory / (1024 * 1024) + " MB");
        log.info("Total Memory: " + totalMemory / (1024 * 1024) + " MB");
        log.info("Max Memory: " + maxMemory / (1024 * 1024) + " MB");
    }
}
