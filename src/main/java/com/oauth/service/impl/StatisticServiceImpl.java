package com.oauth.service.impl;

import com.oauth.dto.AuthServerDTO;
import com.oauth.response.ApiResponse;
import com.oauth.service.mapper.StatisticService;
import com.oauth.utils.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StatisticServiceImpl implements StatisticService {

    @Override
    public ResponseEntity<?> doInfoDaily(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject;
        String msg;

        String userId = params.getUserId();
        String deviceId = params.getDeviceId();
        String controlAuthKey = params.getControlAuthKey();
        String startDate = params.getStartDate();
        String endDate = params.getEndDate();

        try {

        } catch (Exception e){
            log.error("", e);
        }
        return null;
    }

}
