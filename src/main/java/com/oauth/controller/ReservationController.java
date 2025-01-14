package com.oauth.controller;

import com.oauth.dto.AuthServerDTO;
import com.oauth.service.mapper.ReservationService;
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

@Slf4j
@RequestMapping("/reservation/v1")
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class ReservationController {

    @Autowired
    private Common common;
    @Autowired
    ReservationService reservationService;

    /** 24시간 예약 */
    @PostMapping(value = "/set24")
    @ResponseBody
    public ResponseEntity<?> doSet24(HttpSession session, HttpServletRequest request, @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        log.info("[24시간 예약]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId()) ||
            Validator.isNullOrEmpty(params.getDeviceId()) ||
            Validator.isNullOrEmpty(params.getControlAuthKey()) ||
            Validator.isNullOrEmpty(params.getType24h()) ||
            Validator.isNullOrEmpty(params.getHours()) ||
//            Validator.isNullOrEmpty(params.getOnOffFlag()) ||
            Validator.isNullOrEmpty(params.getPushToken())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("24시간 예약 값 오류");
        }

        return reservationService.doSet24(params);
    }

    /** 반복(12시간) 예약  */
    @PostMapping(value = "/set12")
    @ResponseBody
    public ResponseEntity<?> doSet12(HttpSession session, HttpServletRequest request, @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        log.info("[반복(12시간) 예약]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getWorkPeriod()) ||
                Validator.isNullOrEmpty(params.getWorkTime()) ||
//                Validator.isNullOrEmpty(params.getOnOffFlag()) ||
                Validator.isNullOrEmpty(params.getPushToken())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("반복(12시간) 예약 값 오류");
        }

        return reservationService.doSet12(params);
    }

    /** 빠른 온수 예약  */
    @PostMapping(value = "/awakeAlarmSet")
    @ResponseBody
    public ResponseEntity<?> doAwakeAlarmSet(HttpSession session, HttpServletRequest request, @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        log.info("[빠른 온수 예약]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getAwakeList()) ||
                Validator.isNullOrEmpty(params.getPushToken())){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("빠른 온수 예약 값 오류");

        }

        return reservationService.doAwakeAlarmSet(params);
    }

    /** 주간 예약  */
    @PostMapping(value = "/setWeek")
    @ResponseBody
    public ResponseEntity<?> doSetWeek(HttpSession session, HttpServletRequest request, @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        log.info("[주간 예약]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getWeekList()) ||
                Validator.isNullOrEmpty(params.getModelCode()) ||
                Validator.isNullOrEmpty(params.getMn()) ||
//                Validator.isNullOrEmpty(params.getOnOffFlag()) ||
                Validator.isNullOrEmpty(params.getPushToken())){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("주간 예약 값 오류");
        }

        return reservationService.doSetWeek(params);
    }

    /** 환기 취침 모드  */
    @PostMapping(value = "/setSleepMode")
    @ResponseBody
    public ResponseEntity<?> doSetSleepMode(HttpSession session, HttpServletRequest request, @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        log.info("[환기 취침 모드]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getOnHour()) ||
                Validator.isNullOrEmpty(params.getOnMinute()) ||
                Validator.isNullOrEmpty(params.getOffHour()) ||
                Validator.isNullOrEmpty(params.getOffMinute()) ||
                Validator.isNullOrEmpty(params.getOnOffFlag())){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("환기 취침 모드 값 오류");

        }

        return reservationService.doSetSleepMode(params);
    }

    /** 환기 꺼짐/켜짐 예약 */
    @PostMapping(value = "/setOnOffPower")
    @ResponseBody
    public ResponseEntity<?> doSetOnOffPower(HttpSession session, HttpServletRequest request, @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        log.info("[환기 꺼짐/켜짐 예약]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getPowerStatus()) ||
                Validator.isNullOrEmpty(params.getWaitHour()) ||
                Validator.isNullOrEmpty(params.getWaitMinute())){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("환기 꺼짐/켜짐 예약 값 오류");
        }

        return reservationService.doSetOnOffPower(params);
    }

}
