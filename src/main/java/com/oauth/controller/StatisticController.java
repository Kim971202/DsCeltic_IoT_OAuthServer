package com.oauth.controller;

import com.oauth.dto.AuthServerDTO;
import com.oauth.service.mapper.StatisticService;
import com.oauth.utils.CustomException;
import com.oauth.utils.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    /* 홈 IoT 가동시간 통계조회 */
    @RequestMapping("/infoDaily")
    public HashMap<String, Object> doInfoDaily(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException {

        String logStep = "[홈 IoT가동시간 통계조회]";
        log.info("[홈 IoT가동시간 통계조회]");

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getStartDatetime()) ||
                Validator.isNullOrEmpty(params.getEndDatetime())) {
            throw new CustomException("404", "홈 IoT가동시간 통계조회 값 오류");
        }

        return statisticService.doInfoDaily(params);
    }

}
