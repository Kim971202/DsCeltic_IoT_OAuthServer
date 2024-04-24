package com.oauth.service.impl;

import com.oauth.utils.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;

@Slf4j
@Service
public class PushService {

    @Autowired
    MobiusService mobiusService;

    public ResponseEntity<?> sendPushMessage(String... values){

        HashMap<String, String> pushMap = new HashMap<>();

        try {
            pushMap.put("targetToken", "");
            pushMap.put("title", Arrays.toString(values));
            pushMap.put("body", Arrays.toString(values));
            pushMap.put("id", Arrays.toString(values));
            pushMap.put("isEnd", "False");

            mobiusService.createCin("ToPushServer", "ToPushServerCnt", JSON.toJson(pushMap));
        } catch (Exception e){
            log.error("", e);
        }
        return null;
    }

}
