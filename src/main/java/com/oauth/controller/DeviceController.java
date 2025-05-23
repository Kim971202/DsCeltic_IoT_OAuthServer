package com.oauth.controller;

import com.oauth.dto.AuthServerDTO;
import com.oauth.service.impl.DeviceServiceImpl;
import com.oauth.utils.Common;
import com.oauth.utils.CustomException;
import com.oauth.utils.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.HashMap;

@Slf4j
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
    public ResponseEntity<?> doDeviceStatusInfo(HttpSession session, HttpServletRequest request,
            @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        log.info("[홈 IoT 컨트롤러 상태 정보 조회]");
        common.logParams(params);

        if (Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getModelCode())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("홈 IoT 컨트롤러 상태 정보 조회 값 오류");

        }

        return deviceService.doDeviceStatusInfo(params);
    }

    /** 전원 On/Off */
    @PostMapping(value = "/powerOnOff")
    @ResponseBody
    public ResponseEntity<?> doPowerOnOff(HttpSession session, HttpServletRequest request,
            @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        log.info("[전원 On/Off]");
        common.logParams(params);

        if (Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getDeviceType()) ||
                Validator.isNullOrEmpty(params.getModelCode()) ||
                Validator.isNullOrEmpty(params.getPowerStatus()) ||
                Validator.isNullOrEmpty(params.getOnOffFlag()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("전원 On/Off 값 오류");
        }
        return deviceService.doPowerOnOff(params);
    }

    /** 각방 전체 전원 On/Off */
    @PostMapping(value = "/roomAllPowerOnOff")
    @ResponseBody
    public ResponseEntity<?> doRoomAllPowerOnOff(HttpSession session, HttpServletRequest request,
            @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        log.info("[각방 전체 전원 On/Off]");
        common.logParams(params);

        if (Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getModelCode()) ||
                Validator.isNullOrEmpty(params.getPowerStatus())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("각방 전체 전원 On/Off 값 오류");
        }
        return deviceService.doRoomAllPowerOnOff(params);
    }

    /** 홈 IoT 컨트롤러 정보 등록/수정 */
    @PostMapping(value = "/deviceInfoUpsert")
    @ResponseBody
    public ResponseEntity<?> doDeviceInfoUpsert(HttpSession session, HttpServletRequest request,
            @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws Exception {

        log.info("[홈 IoT 컨트롤러 정보 등록/수정]");
        common.logParams(params);

        if (Validator.isNullOrEmpty(params.getUserId())             ||
                Validator.isNullOrEmpty(params.getHp())             ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getRegistYn())       ||
                Validator.isNullOrEmpty(params.getDeviceType())     ||
                Validator.isNullOrEmpty(params.getModelCode())      ||
                Validator.isNullOrEmpty(params.getSerialNumber())   ||
                Validator.isNullOrEmpty(params.getZipCode())        ||
                Validator.isNullOrEmpty(params.getOldAddr())        ||
                Validator.isNullOrEmpty(params.getNewAddr())        ||
                Validator.isNullOrEmpty(params.getAddrDetail())     ||
                Validator.isNullOrEmpty(params.getLatitude())       ||
                Validator.isNullOrEmpty(params.getLongitude())      ||
                Validator.isNullOrEmpty(params.getDeviceNickname())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("홈 IoT 컨트롤러 정보 등록/수정 값 오류");
        }
        return deviceService.doDeviceInfoUpsert(params);
    }

    /** 홈 IoT 컨트롤러 정보 조회-단건 */
    @PostMapping(value = "/deviceInfoSearch")
    @ResponseBody
    public HashMap<String, Object> doDeviceInfoSearch(HttpSession session, HttpServletRequest request,
            @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws Exception {

        log.info("[홈 IoT 컨트롤러 정보 조회-단건]");
        common.logParams(params);
        HashMap<String, Object> result = new HashMap<>();

        if (Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey())) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND); // 404 상태 코드 설정
            result.put("message", "홈 IoT 컨트롤러 정보 조회-단건 값 오류");
            return result; // 404 Bad Request
        }
        return deviceService.doDeviceInfoSearch(params);
    }

    /** 모드변경 */
    @PostMapping(value = "/modeChange")
    @ResponseBody
    public ResponseEntity<?> doModeChange(HttpSession session, HttpServletRequest request,
            @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        log.info("[모드변경]");
        common.logParams(params);

        if (Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getModelCode()) ||
                Validator.isNullOrEmpty(params.getOnOffFlag()) ||
                Validator.isNullOrEmpty(params.getModeCode())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("모드변경 값 오류");
        }
        return deviceService.doModeChange(params);
    }

    /** 각방 전체 모드변경 */
    @PostMapping(value = "/roomAllModeChange")
    @ResponseBody
    public ResponseEntity<?> doRoomAllModeChange(HttpSession session, HttpServletRequest request,
            @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        log.info("[각방 전체 모드변경]");
        common.logParams(params);

        if (Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getModelCode()) ||
                Validator.isNullOrEmpty(params.getModeCode())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("각방 전체 모드변경 값 오류");
        }
        return deviceService.doRoomAllModeChange(params);
    }

    /** 실내온도 설정 */
    @PostMapping(value = "/tempertureSet")
    @ResponseBody
    public ResponseEntity<?> doTemperatureSet(HttpSession session, HttpServletRequest request,
            @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        log.info("[실내온도 설정]");
        common.logParams(params);

        if (Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getOnOffFlag()) ||
                Validator.isNullOrEmpty(params.getTemperture())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("실내온도 설정 값 오류");
        }
        return deviceService.doTemperatureSet(params);
    }

    /** 냉방-실내온도 설정 */
    @PostMapping(value = "/coldTempertureSet")
    @ResponseBody
    public ResponseEntity<?> doColdTempertureSet(HttpSession session, HttpServletRequest request,
            @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        log.info("[냉방-실내온도 설정]");
        common.logParams(params);

        if (Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getTemperture())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("냉방-실내온도 설정 값 오류");
        }
        return deviceService.doColdTempertureSet(params);
    }

    /** 강제 제상 모드 설정 */
    @PostMapping(value = "/forcedDefrost")
    @ResponseBody
    public ResponseEntity<?> doForcedDefrost(HttpSession session, HttpServletRequest request, @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        log.info("[강제 제상 모드 설정]");
        common.logParams(params);

        if (Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getForcedDefrost())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("강제 제상 모드 설정 값 오류");
        }
        return deviceService.doForcedDefrost(params);
    }

    /** 난방수온도 설정 */
    @PostMapping(value = "/boiledWaterTempertureSet")
    @ResponseBody
    public ResponseEntity<?> doBoiledWaterTempertureSet(HttpSession session, HttpServletRequest request,
            @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        String logStep = "[난방수온도 설정]";
        log.info("[난방수온도 설정]");

        if (Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getOnOffFlag()) ||
                Validator.isNullOrEmpty(params.getTemperture())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("난방수온도 설정 값 오류");
        }
        return deviceService.doBoiledWaterTempertureSet(params);
    }

    /** 온수온도 설정 */
    @PostMapping(value = "/waterTempertureSet")
    @ResponseBody
    public ResponseEntity<?> doWaterTempertureSet(HttpSession session, HttpServletRequest request,
            @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        log.info("[온수온도 설정]");
        common.logParams(params);

        if (Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getTemperture())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("온수온도 설정 값 오류");

        }
        return deviceService.doWaterTempertureSet(params);
    }

    /** 빠른온수 설정 */
    @PostMapping(value = "/fastHotWaterSet")
    @ResponseBody
    public ResponseEntity<?> doFastHotWaterSet(HttpSession session, HttpServletRequest request,
            @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        log.info("[빠른온수 설정]");
        common.logParams(params);

        if (Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getModeCode())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("빠른온수 설정 값 오류");
        }
        return deviceService.doFastHotWaterSet(params);
    }

    /** 잠금 모드 설정 */
    @PostMapping(value = "/lockSet")
    @ResponseBody
    public ResponseEntity<?> doLockSet(HttpSession session, HttpServletRequest request,
            @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        log.info("[잠금 모드 설정]");
        common.logParams(params);

        if (Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getLockSet())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("잠금 모드 설정 값 오류");
        }
        return deviceService.doLockSet(params);
    }

    /** 홈 IoT 컨트롤러 상태 정보 조회 – 홈 화면 */
    @PostMapping(value = "/basicDeviceStatusInfo")
    @ResponseBody
    public ResponseEntity<?> doBasicDeviceStatusInfo(HttpSession session, HttpServletRequest request,
            @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws Exception {

        log.info("[홈 IoT 컨트롤러 상태 정보 조회 – 홈 화면]");
        common.logParams(params);

        if (Validator.isNullOrEmpty(params.getUserId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("홈 IoT 컨트롤러 상태 정보 조회 - 홈 화면 값 오류");
        }
        return deviceService.doBasicDeviceStatusInfo(params);
    }

    /** 홈 IoT 컨트롤러 에러 정보 조회 */
    @PostMapping(value = "/deviceErrorInfo")
    @ResponseBody
    public ResponseEntity<?> doDeviceErrorInfo(HttpSession session, HttpServletRequest request,
            @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws Exception {

        log.info("[홈 IoT 컨트롤러 에러 정보 조회]");
        common.logParams(params);

        if (Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getDeviceType()) ||
                Validator.isNullOrEmpty(params.getModelCode())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("홈 IoT 컨트롤러 에러 정보 조회 값 오류");
        }
        return deviceService.doDeviceErrorInfo(params);
    }

    /** 홈 IoT 정보 조회 - 리스트 */
    @PostMapping(value = "/deviceInfoSearchList")
    @ResponseBody
    public ResponseEntity<?> doDeviceInfoSearchList(HttpSession session, HttpServletRequest request,
            @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws Exception {

        log.info("[홈 IoT 정보 조회 - 리스트]");
        common.logParams(params);

        if (Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getGroupIdxList())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("홈 IoT 정보 조회 - 리스트 값 오류");

        }
        return deviceService.doDeviceInfoSearchList(params);
    }

    /** 홈 IoT 컨트롤러 풍량 단수 설정 */
    @PostMapping(value = "/ventilationFanSpeedSet")
    @ResponseBody
    public ResponseEntity<?> doVentilationFanSpeedSet(HttpSession session, HttpServletRequest request,
            @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws Exception {

        log.info("[홈 IoT 컨트롤러 풍량 단수 설정]");
        common.logParams(params);

        if (Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getFanSpeed()) ||
                Validator.isNullOrEmpty(params.getModelCode())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("홈 IoT 컨트롤러 풍량 단수 설정 - 리스트 값 오류");
        }
        return deviceService.doVentilationFanSpeedSet(params);
    }

    /** 홈 IoT 컨트롤러 활성/비활성 정보 요청 */
    @PostMapping(value = "/activeStatus")
    @ResponseBody
    public ResponseEntity<?> doActiveStatus(HttpSession session, HttpServletRequest request,
            @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws Exception {

        log.info("[홈 IoT 컨트롤러 활성/비활성 정보 요청]");
        common.logParams(params);

        if (Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("홈 IoT 컨트롤러 활성/비활성 정보 요청 오류");
        }
        return deviceService.doActiveStatus(params);
    }

    /** 홈 IoT 컨트롤러 상태 정보 조회 (각방) – 홈 화면 */
    @PostMapping(value = "/basicRoomDeviceStatusInfo")
    @ResponseBody
    public ResponseEntity<?> doBasicRoomDeviceStatusInfo(HttpSession session, HttpServletRequest request,
            @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws Exception {

        log.info("[홈 IoT 컨트롤러 상태 정보 조회 (각방) - 홈 화면 ");
        common.logParams(params);

        if (Validator.isNullOrEmpty(params.getUserId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("홈 IoT 컨트롤러 상태 정보 조회 (각방) - 홈 화면  오류");
        }
        return deviceService.doBasicRoomDeviceStatusInfo(params);
    }

    /** 홈 IoT 컨트롤러 상태 정보 조회 (각방) */
    @PostMapping(value = "/roomDeviceStatusInfo")
    @ResponseBody
    public ResponseEntity<?> doRoomDeviceStatusInfo(HttpSession session, HttpServletRequest request,
            @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws Exception {

        log.info("[홈 IoT 컨트롤러 상태 정보 조회 (각방)");
        common.logParams(params);

        if (Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getParentDevice())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("홈 IoT 컨트롤러 상태 정보 조회 (각방) 오류");
        }
        return deviceService.doRoomDeviceStatusInfo(params);
    }

    /** FCNT 요청 호출 */
    @PostMapping(value = "/callFcNt")
    @ResponseBody
    public ResponseEntity<?> doCallFcNt(HttpSession session, HttpServletRequest request,
            @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws Exception {

        log.info("[FCNT 요청 호출");
        common.logParams(params);

        if (Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getParentDevice()) ||
                Validator.isNullOrEmpty(params.getDeviceId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("FCNT 요청 호출 오류");
        }
        return deviceService.doCallFcNt(params);
    }

    /** 기기 그룹 변경 */
    @PostMapping(value = "/changeDeviceGroupInfo")
    @ResponseBody
    public ResponseEntity<?> doChangeDeviceGroupInfo(HttpSession session, HttpServletRequest request,
                                        @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws Exception {

        log.info("[기기 그룹 변경");
        common.logParams(params);

        if (Validator.isNullOrEmpty(params.getUserId())            ||
                Validator.isNullOrEmpty(params.getDeviceId())      ||
                Validator.isNullOrEmpty(params.getGroupIdx())      ||
                Validator.isNullOrEmpty(params.getGroupName())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("기기 그룹 변경 오류");
        }
        return deviceService.doChangeDeviceGroupInfo(params);
    }

}
