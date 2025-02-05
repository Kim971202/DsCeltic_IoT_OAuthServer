package com.oauth.controller;

import com.oauth.dto.AuthServerDTO;
import com.oauth.service.mapper.StatisticService;
import com.oauth.utils.Common;
import com.oauth.utils.CustomException;
import com.oauth.utils.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.HashMap;

@Slf4j
@RequestMapping("/stats/v1")
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class StatisticController {

    @Autowired
    private StatisticService statisticService;

    @Autowired
    private Common common;

    /* 홈 IoT 가동시간 통계조회 */
    @RequestMapping("/infoDaily")
    public HashMap<String, Object> doInfoDaily(HttpServletRequest request, HttpServletResponse response, @ModelAttribute AuthServerDTO params)
            throws CustomException {

        log.info("[홈 IoT가동시간 통계조회]");
        common.logParams(params);
        HashMap<String, Object> result  = new HashMap<>();
        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getStartDate()) ||
                Validator.isNullOrEmpty(params.getEndDate()) ||
                Validator.isNullOrEmpty(params.getPushToken())) {

                    response.setStatus(HttpServletResponse.SC_NOT_FOUND); // 404 상태 코드 설정
                    result .put("message", "홈 IoT가동시간 통계조회 값 오류");
                    return result; // 404 Bad Request
        }

        return statisticService.doInfoDaily(params);
    }

    /* 환기 공기질 주기보고 통계조회 */
    @RequestMapping("/ventilationAirStats")
    public HashMap<String, Object> doVentilationAirStats(HttpServletRequest request, HttpServletResponse response, @ModelAttribute AuthServerDTO params)
            throws CustomException {

        log.info("[홈 IoT가동시간 통계조회]");
        common.logParams(params);
        HashMap<String, Object> result  = new HashMap<>();
        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getStartDate()) ||
                Validator.isNullOrEmpty(params.getEndDate()) ||
                Validator.isNullOrEmpty(params.getPushToken())) {

            response.setStatus(HttpServletResponse.SC_NOT_FOUND); // 404 상태 코드 설정
            result .put("message", "홈 IoT가동시간 통계조회 값 오류");
            return result; // 404 Bad Request
        }

        return statisticService.doVentilationAirStats(params);
    }

}
