package com.oauth.service.mapper;

import com.oauth.dto.AuthServerDTO;
import com.oauth.utils.CustomException;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;

public interface StatisticService {

    /* 홈 IoT 가동시간 통계조회 */
    HashMap<String, Object> doInfoDaily(AuthServerDTO params) throws CustomException;
    /* 환기 공기질 주기보고 통계조회 */
    HashMap<String, Object> doVentilationAirStats(AuthServerDTO params) throws CustomException;

}
