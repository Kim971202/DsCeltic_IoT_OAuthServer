package com.oauth.jwt;

import org.springframework.stereotype.Component;

import java.util.UUID;

//@Component
public class ApiTokenUtils {

    public String getTransactionId(){
        return UUID.randomUUID().toString();
    }



}
