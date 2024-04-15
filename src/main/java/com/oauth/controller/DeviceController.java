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
import java.sql.SQLException;
import java.util.HashMap;

@RequestMapping("/devices/v1")
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class DeviceController {

    @Autowired
    private Common common;
    @Autowired
    private DeviceServiceImpl deviceService;


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

    // 아래 실제 기기 제어 호출 부분
    /** 전원 On/Off */
    @PostMapping(value = "/powerOnOff")
    @ResponseBody
    public ResponseEntity<?> doPowerOnOff(HttpSession session, HttpServletRequest request, @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        String logStep = "[전원 On/Off]";

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getDeviceType()) ||
                Validator.isNullOrEmpty(params.getModelCode()) ||
                Validator.isNullOrEmpty(params.getPowerStatus()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }
        return deviceService.doPowerOnOff(params);
    }

    /** 홈 IoT 컨트롤러 정보 등록/수정 */
    @PostMapping(value = "/deviceInfoUpsert")
    @ResponseBody
    public ResponseEntity<?> doDeviceInfoUpsert(HttpSession session, HttpServletRequest request, @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        String logStep = "[홈 IoT 컨트롤러 정보 등록/수정]";

        if(Validator.isNullOrEmpty(params.getUserId()) ||
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

    /** 홈 IoT 컨트롤러 정보 조회-단건 */
    @PostMapping(value = "/deviceInfoSearch")
    @ResponseBody
    public HashMap<String, Object> doDeviceInfoSearch(HttpSession session, HttpServletRequest request, @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws Exception {

        String logStep = "[홈 IoT 컨트롤러 정보 조회-단건]";

        if(Validator.isNullOrEmpty(params.getUserId()) ||
           Validator.isNullOrEmpty(params.getDeviceId()) ||
           Validator.isNullOrEmpty(params.getControlAuthKey())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }
        return deviceService.doDeviceInfoSearch(params);
    }

    /** 모드변경 */
    @PostMapping(value = "/modeChange")
    @ResponseBody
    public ResponseEntity<?> doModeChange(HttpSession session, HttpServletRequest request, @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        String logStep = "[모드변경]";

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getModelCode()) ||
                Validator.isNullOrEmpty(params.getModeCode()) ||
                Validator.isNullOrEmpty(params.getSleepCode())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }
        return deviceService.doModeChange(params);
    }

    /** 실내온도 설정 */
    @PostMapping(value = "/tempertureSet")
    @ResponseBody
    public ResponseEntity<?> doTemperatureSet(HttpSession session, HttpServletRequest request, @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        String logStep = "[실내온도 설정]";

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getTemperture())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }
        return deviceService.doTemperatureSet(params);
    }

    /** 난방수온도 설정  */
    @PostMapping(value = "/boiledWaterTempertureSet")
    @ResponseBody
    public ResponseEntity<?> doBoiledWaterTempertureSet(HttpSession session, HttpServletRequest request, @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        String logStep = "[난방수온도 설정]";

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getTemperture())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }
        return deviceService.doBoiledWaterTempertureSet(params);
    }

    /** 온수온도 설정 */
    @PostMapping(value = "/waterTempertureSet")
    @ResponseBody
    public ResponseEntity<?> doWaterTempertureSet(HttpSession session, HttpServletRequest request, @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException{

        String logStep = "[온수온도 설정]";

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getTemperture())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }
        return deviceService.doWaterTempertureSet(params);
    }

    /** 빠른온수 설정 */
    @PostMapping(value = "/fastHotWaterSet")
    @ResponseBody
    public ResponseEntity<?> doFastHotWaterSet(HttpSession session, HttpServletRequest request, @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException{

        String logStep = "[빠른온수 설정]";

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getModeCode())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }
        return deviceService.doFastHotWaterSet(params);
    }

    /** 잠금 모드 설정  */
    @PostMapping(value = "/lockSet")
    @ResponseBody
    public ResponseEntity<?>doLockSet(HttpSession session, HttpServletRequest request, @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException{

        String logStep = "[잠금 모드 설정]";

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getLockSet())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }
        return deviceService.doLockSet(params);
    }

    /** 홈 IoT 컨트롤러 상태 정보 조회 – 홈 화면  */
    @PostMapping(value = "/basicDeviceStatusInfo")
    @ResponseBody
    public ResponseEntity<?> doBasicDeviceStatusInfo(HttpSession session, HttpServletRequest request, @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws Exception {

        String logStep = "[홈 IoT 컨트롤러 상태 정보 조회 – 홈 화면]";

        if(Validator.isNullOrEmpty(params.getUserId()) || Validator.isNullOrEmpty(params.getControlAuthKey())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }
        return deviceService.doBasicDeviceStatusInfo(params);
    }

    /**	홈 IoT 컨트롤러 에러 정보 조회  */
    @PostMapping(value = "/deviceErrorInfo")
    @ResponseBody
    public ResponseEntity<?> doDeviceErrorInfo(HttpSession session, HttpServletRequest request, @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws Exception {

        String logStep = "[홈 IoT 컨트롤러 에러 정보 조회]";

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getDeviceType()) ||
                Validator.isNullOrEmpty(params.getModeCode())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }
        return deviceService.doDeviceErrorInfo(params);
    }

}
