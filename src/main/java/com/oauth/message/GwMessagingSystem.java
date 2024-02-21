package com.oauth.message;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;


@Component
public class GwMessagingSystem implements MessagingSystem {
    private final Map<String, BlockingQueue<String>> messageQueues = new ConcurrentHashMap<>();

    @Override
    public void sendMessage(String destination, String message) {
        // 해당 destination에 대한 큐를 가져오거나 생성
        BlockingQueue<String> messageQueue = messageQueues.computeIfAbsent(destination, k -> new LinkedBlockingQueue<>());
        System.out.println("destination: " + destination);
        // 메시지 큐에 메시지 추가
        messageQueue.offer(message);
    }

    @Override
    public String waitForResponse(String destination, long timeout, TimeUnit unit) throws InterruptedException {
        // 해당 destination에 대한 큐를 가져오거나 생성
        BlockingQueue<String> messageQueue = messageQueues.computeIfAbsent(destination, k -> new LinkedBlockingQueue<>());

        // 메시지 큐에서 응답 메시지를 대기
        return messageQueue.poll(timeout, unit);
    }
}
