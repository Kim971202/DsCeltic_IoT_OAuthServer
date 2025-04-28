package com.oauth.controller;

import com.oauth.service.impl.InfluxService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InfluxController {

    private final InfluxService svc;

    public InfluxController(InfluxService svc) {
        this.svc = svc;
    }

    @GetMapping("/influx/write")
    public String write() {
        svc.writeMeasurement();
        return "written";
    }
}