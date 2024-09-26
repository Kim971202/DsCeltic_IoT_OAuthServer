package com.oauth.controller;

import com.oauth.dto.AuthServerDTO;
import com.oauth.service.mapper.ReservationService;
import com.oauth.utils.Common;
import com.oauth.utils.CustomException;
import com.oauth.utils.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

        String logStep = "[24시간 예약]";
        log.info("[24시간 예약]");

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getType24h()) ||
                Validator.isNullOrEmpty(params.getOnOffFlag())) {
            throw new CustomException("404", "24시간 예약 값 오류");
        }

        log.info("params.getUserId(): " + params.getUserId());
        log.info("params.getDeviceId(): " + params.getDeviceId());
        log.info("params.getControlAuthKey(): " + params.getControlAuthKey());
        log.info("params.getType24h(): " + params.getType24h());
        log.info("params.getOnOffFlag(): " + params.getOnOffFlag());
        log.info("params.getHours(): " + params.getHours());

        return reservationService.doSet24(params);
    }

    /** 반복(12시간) 예약  */
    @PostMapping(value = "/set12")
    @ResponseBody
    public ResponseEntity<?> doSet12(HttpSession session, HttpServletRequest request, @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        String logStep = "[반복(12시간) 예약]";
        log.info("[반복(12시간) 예약]");

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getWorkPeriod()) ||
                Validator.isNullOrEmpty(params.getWorkTime()) ||
                Validator.isNullOrEmpty(params.getOnOffFlag())) {
            throw new CustomException("404", "반복(12시간) 예약 값 오류");
        }

        return reservationService.doSet12(params);
    }

    /** 빠른 온수 예약  */
    @PostMapping(value = "/awakeAlarmSet")
    @ResponseBody
    public ResponseEntity<?> doAwakeAlarmSet(HttpSession session, HttpServletRequest request, @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        String logStep = "[빠른 온수 예약]";
        log.info("[빠른 온수 예약]");

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey())){
            throw new CustomException("404", "빠른 온수 예약 값 오류");
        }

        return reservationService.doAwakeAlarmSet(params);
    }

    /** 주간 예약  */
    @PostMapping(value = "/setWeek")
    @ResponseBody
    public ResponseEntity<?> doSetWeek(HttpSession session, HttpServletRequest request, @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        String logStep = "[주간 예약]";
        log.info("[주간 예약]");

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getOnOffFlag())){
            throw new CustomException("404", "주간 예약 값 오류");
        }

        return reservationService.doSetWeek(params);
    }

    /** 환기 취침 모드  */
    @PostMapping(value = "/setSleepMode")
    @ResponseBody
    public ResponseEntity<?> doSetSleepMode(HttpSession session, HttpServletRequest request, @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        String logStep = "[환기 취침 모드]";
        log.info("[환기 취침 모드]");

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getOnHour()) ||
                Validator.isNullOrEmpty(params.getOnMinute()) ||
                Validator.isNullOrEmpty(params.getOffHour()) ||
                Validator.isNullOrEmpty(params.getOffMinute()) ||
                Validator.isNullOrEmpty(params.getOnOffFlag())){
            throw new CustomException("404", "환기 취침 모드 값 오류");
        }

        return reservationService.doSetSleepMode(params);
    }

    /** 환기 꺼짐/켜짐 예약 */
    @PostMapping(value = "/setOnOffPower")
    @ResponseBody
    public ResponseEntity<?> doSetOnOffPower(HttpSession session, HttpServletRequest request, @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        String logStep = "[환기 꺼짐/켜짐 예약]";
        log.info("[환기 꺼짐/켜짐 예약]");

        System.out.println(params);

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getPowerStatus()) ||
                Validator.isNullOrEmpty(params.getWaitHour()) ||
                Validator.isNullOrEmpty(params.getWaitMinute()) ||
                Validator.isNullOrEmpty(params.getOnOffFlag())){
            throw new CustomException("404", "환기 꺼짐/켜짐 예약 값 오류");
        }

        return reservationService.doSetOnOffPower(params);
    }

}
