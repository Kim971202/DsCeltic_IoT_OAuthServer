package com.oauth.utils;

public class CustomException extends RuntimeException{

    private String resultCode;
    private String msg;

    public CustomException(String resultCode, String msg){
        this.resultCode = resultCode;
        this.msg = msg;
    }

    public String getCode() {
        return resultCode;
    }

    public String getMsg() {
        return msg;
    }
}
