package com.oauth.utils;

public class CustomException extends RuntimeException{

    CustomException(){}

    public CustomException(String message) {
        super(message); // RuntimeException 클래스 생성자 호출
    }

}
