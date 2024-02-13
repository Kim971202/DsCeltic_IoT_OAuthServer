package com.oauth.controller;

import com.oauth.dto.AuthServerDTO;
import com.oauth.service.impl.DeviceServiceImpl;
import com.oauth.utils.Common;
import com.oauth.utils.CustomException;
import com.oauth.utils.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@RequestMapping("/devices/v1")
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class DeviceController {

    @Autowired
    private Common common;

    @Autowired
    private DeviceServiceImpl deviceService;

    /** 전원 On/Off */
    @PostMapping(value = "/powerOnOff")
    @ResponseBody
    public ResponseEntity<?> doPowerOnOff(HttpSession session, HttpServletRequest request, @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        String logStep = "[전원 On/Off]";

        if(Validator.isNullOrEmpty(params.getAccessToken()) ||
                Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getDeviceType()) ||
                Validator.isNullOrEmpty(params.getModelCode()) ||
                Validator.isNullOrEmpty(params.getPowerStatus()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }

        return deviceService.doDeviceInfoUpsert(params);
    }

    /** 홈 IoT 컨트롤러 정보 등록/수정 */
    @PostMapping(value = "/deviceInfoUpsert")
    @ResponseBody
    public ResponseEntity<?> doDeviceInfoUpsert(HttpSession session, HttpServletRequest request, @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        String a = Common.getClientIp(request);
        System.out.println(a);

        String logStep = "[홈 IoT 컨트롤러 정보 등록/수정]";

        if(Validator.isNullOrEmpty(params.getAccessToken()) ||
                Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getHp()) ||
                Validator.isNullOrEmpty(params.getRegistYn()) ||
                Validator.isNullOrEmpty(params.getTmpRegistKey()) ||
                Validator.isNullOrEmpty(params.getDeviceType()) ||
                Validator.isNullOrEmpty(params.getModelCode()) ||
                Validator.isNullOrEmpty(params.getSerialNumber()) ||
                Validator.isNullOrEmpty(params.getZipCode()) ||
                Validator.isNullOrEmpty(params.getOldAddr()) ||
                Validator.isNullOrEmpty(params.getNewAddr()) ||
                Validator.isNullOrEmpty(params.getAddrDetail()) ||
                Validator.isNullOrEmpty(params.getLatitude()) ||
                Validator.isNullOrEmpty(params.getLongitude()) ||
                Validator.isNullOrEmpty(params.getDeviceNickname()) ||
                Validator.isNullOrEmpty(params.getAddrNickname())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }

        return deviceService.doDeviceInfoUpsert(params);
    }

    /** 홈 IoT 컨트롤러 상태 정보 조회 */
    @PostMapping(value = "/deviceStatusInfo")
    @ResponseBody
    public ResponseEntity<?> doDeviceStatusInfo(HttpSession session, HttpServletRequest request, @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        String logStep = "[홈 IoT 컨트롤러 상태 정보 조회]";

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getModelCode())) {
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }

        return deviceService.doDeviceStatusInfo(params);
    }

}
