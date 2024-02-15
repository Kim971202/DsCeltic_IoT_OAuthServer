package com.oauth.controller;

import com.oauth.dto.AuthServerDTO;
import com.oauth.service.mapper.ReservationService;
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

        if(Validator.isNullOrEmpty(params.getAccessToken()) ||
                Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getType24h()) ||
                Validator.isNullOrEmpty(params.getOnOffFlag())) {
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }

        return reservationService.doSet24(params);
    }

    /** 반복(12시간) 예약  */
    @PostMapping(value = "/set12")
    @ResponseBody
    public ResponseEntity<?> doSet12(HttpSession session, HttpServletRequest request, @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        String logStep = "[반복(12시간) 예약]";

        if(Validator.isNullOrEmpty(params.getAccessToken()) ||
                Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getWorkPeriod()) ||
                Validator.isNullOrEmpty(params.getWorkTime()) ||
                Validator.isNullOrEmpty(params.getOnOffFlag())) {
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }

        return reservationService.doSet12(params);
    }

}
