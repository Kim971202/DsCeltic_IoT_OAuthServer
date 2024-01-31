package com.oauth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oauth.dto.gw.DeviceStatusInfoDR910W;
import com.oauth.service.MobiusService;
import com.oauth.utils.Common;
import com.oauth.utils.CustomException;
import com.oauth.utils.JSON;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class MobiusController {

    @Autowired
    Common common;
    @Autowired
    MobiusService mobiusService;

    @PostMapping(value = "/GatewayToAppServer")
    @ResponseBody
    public void receiveCin(HttpSession session, HttpServletRequest request,@RequestBody String reqBody, HttpServletResponse response) throws Exception{
        DeviceStatusInfoDR910W dr910W = DeviceStatusInfoDR910W.getInstance();

        dr910W.setRKey(common.readCon(reqBody, "rKey"));
        dr910W.setPowr(common.readCon(reqBody, "powr"));
        dr910W.setOpMd(common.readCon(reqBody, "opMd"));
        dr910W.setHtTp(common.readCon(reqBody, "htTp"));
        dr910W.setWtTp(common.readCon(reqBody, "wtTp"));
        dr910W.setHwTp(common.readCon(reqBody, "hwTp"));
        dr910W.setRsCf(common.changeStringToJson(common.readCon(reqBody, "rsCf")));
        dr910W.setFtMd(common.readCon(reqBody, "ftMd"));
        dr910W.setBCdt(common.readCon(reqBody, "bCdt"));
        dr910W.setChTp(common.readCon(reqBody, "chTp"));
        dr910W.setCwTp(common.readCon(reqBody, "cwTp"));
        dr910W.setHwSt(common.readCon(reqBody, "hwSt"));
        dr910W.setFcLc(common.readCon(reqBody, "fcLc"));
        dr910W.setSlCd(common.readCon(reqBody, "slCd"));
        dr910W.setBlCf(common.readCon(reqBody, "blCf"));
        dr910W.setMfDt(common.readCon(reqBody, "mfDt"));
    }
}
