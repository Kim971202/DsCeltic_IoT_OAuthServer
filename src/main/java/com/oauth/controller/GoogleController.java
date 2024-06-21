package com.oauth.controller;

import com.oauth.mapper.DeviceMapper;
import com.oauth.service.impl.MobiusService;
import com.oauth.utils.Common;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class GoogleController {

    @Autowired
    DeviceMapper deviceMapper;
    @Autowired
    MobiusService mobiusService;
    @Autowired
    Common common;

    @PostMapping(value = "/GoogleToAppServer")
    @ResponseBody
    public String receiveCin(@RequestBody String jsonBody) throws Exception {

        log.info("GOOGLE Received JSON: " + jsonBody);

        String[] serialNumber = common.readCon(jsonBody, "sur").split("/");
        String userId = common.readCon(jsonBody, "userId");

        System.out.println("common.stringToHex(\"    \" + serialNumber[2]): " + common.stringToHex("    " + serialNumber[2]));
        System.out.println("userId: " + userId);

//        mobiusService.createCin(common.stringToHex("    " + serialNumber[2]), userId, "");

        return "OK";
    }

}
