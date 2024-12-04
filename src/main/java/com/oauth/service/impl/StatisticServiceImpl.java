package com.oauth.service.impl;

import com.oauth.dto.AuthServerDTO;
import com.oauth.mapper.MemberMapper;
import com.oauth.response.ApiResponse;
import com.oauth.service.mapper.StatisticService;
import com.oauth.utils.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class StatisticServiceImpl implements StatisticService {

    @Autowired
    MemberMapper memberMapper;

    /** 일별 가동시간 통계조회 */
    @Override
    public HashMap<String, Object> doInfoDaily(AuthServerDTO params) throws CustomException {

        List<AuthServerDTO> deviceWorkTimeInfo;

        HashMap<String, Object> resultMap = new LinkedHashMap<String, Object>();

        // "stats" 부분을 표현하는 List 생성
        List<Map<String, Object>> statsList = new ArrayList<>();
        try {
            deviceWorkTimeInfo = memberMapper.getWorkTime(params);

            if(deviceWorkTimeInfo == null){
                resultMap.put("resultCode", "1018");
                resultMap.put("resultMsg", "일별 가동시간 통계조회 조회 실패");
                return resultMap;
            }

            for (AuthServerDTO authServerDTO : deviceWorkTimeInfo) {
                Map<String, Object> stats = new LinkedHashMap<>();
                stats.put("workDate", authServerDTO.getWorkDate());
                stats.put("workTime", authServerDTO.getWorkTime());
                statsList.add(stats);
            }

            resultMap.put("resultCode", "200");
            resultMap.put("resultMsg", "일별 가동시간 통계조회 조회 성공");

            resultMap.put("stats", statsList);
            log.info("resultMap: " + resultMap);
            return resultMap;
        } catch (Exception e){
            log.error("", e);
        }
        return null;
    }

}
