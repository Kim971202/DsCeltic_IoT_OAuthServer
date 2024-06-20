package com.oauth.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class GoogleController {

    @PostMapping(value = "/GoogleToAppServer")
    @ResponseBody
    public String receiveCin(@RequestBody String jsonBody) throws Exception {

        System.out.println(jsonBody);

        return "OK";

    }

}
