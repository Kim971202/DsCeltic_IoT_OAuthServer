package com.oauth.message;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;


@Slf4j
@Component
public class GwMessagingSystem implements MessagingSystem {
    private final Map<String, BlockingQueue<String>> messageQueues = new ConcurrentHashMap<>();

    @Override
    public void sendMessage(String destination, String message) {

        log.info("destination: " + destination);
        log.info("message: " + message);

        // 해당 destination에 대한 큐를 가져오거나 생성
        BlockingQueue<String> messageQueue = messageQueues.computeIfAbsent(destination, k -> new LinkedBlockingQueue<>());
        log.info("destination: " + destination);
        // 메시지 큐에 메시지 추가
        messageQueue.offer(message);
    }

    @Override
    public String waitForResponse(String destination, long timeout, TimeUnit unit) throws InterruptedException {
        printMessageQueues();
        // 해당 destination에 대한 큐를 가져오거나 생성
        BlockingQueue<String> messageQueue = messageQueues.computeIfAbsent(destination, k -> new LinkedBlockingQueue<>());

        // 홈 화면 View일 경우 InputNum만큼 반복문을 돌려 배열을 만들고 Service에 보낸다.
        // 메시지 큐에서 응답 메시지를 대기
        return messageQueue.poll(timeout, unit);
    }

    // 현재 큐 목록을 출력하는 함수
    public void printMessageQueues() {
        log.info("Current Message Queues:");
        for (Map.Entry<String, BlockingQueue<String>> entry : messageQueues.entrySet()) {
            log.info("Destination: " + entry.getKey() + ", Queue Size: " + entry.getValue().size());
        }
    }

    // 넣으거를 지우는 함수
    public void removeMessageQueue(String destination) {
        messageQueues.remove(destination);
    }

}
