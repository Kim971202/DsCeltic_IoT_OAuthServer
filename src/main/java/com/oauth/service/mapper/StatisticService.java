package com.oauth.service.mapper;

import com.oauth.dto.AuthServerDTO;
import com.oauth.utils.CustomException;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;

public interface StatisticService {

    /* 홈 IoT 가동시간 통계조회 */
    HashMap<String, Object> doInfoDaily(AuthServerDTO params) throws CustomException;


}
