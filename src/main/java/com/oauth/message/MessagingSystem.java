package com.oauth.message;

import java.util.concurrent.TimeUnit;

interface MessagingSystem {
    void sendMessage(String destination, String message);
    String waitForResponse(String destination, long timeout, TimeUnit unit) throws InterruptedException;
}