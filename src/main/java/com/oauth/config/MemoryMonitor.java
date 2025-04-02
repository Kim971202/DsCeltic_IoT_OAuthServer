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

        log.info("Used Memory: {} MB", usedMemory / (1024 * 1024));
        log.info("Free Memory: {} MB", freeMemory / (1024 * 1024));
        log.info("Total Memory: {} MB", totalMemory / (1024 * 1024));
        log.info("Max Memory: {} MB", maxMemory / (1024 * 1024));
    }
}
