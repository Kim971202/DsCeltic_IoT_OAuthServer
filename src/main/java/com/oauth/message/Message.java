package com.oauth.message;

public class Message {
    private final String destination;
    private final String message;

    public Message(String destination, String message) {
        this.destination = destination;
        this.message = message;
    }

    public String getDestination() {
        return destination;
    }

    public String getMessage() {
        return message;
    }
}