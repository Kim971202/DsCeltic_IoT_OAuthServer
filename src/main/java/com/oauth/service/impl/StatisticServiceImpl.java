package com.oauth.service.impl;

import com.oauth.dto.AuthServerDTO;
import com.oauth.mapper.DeviceMapper;
import com.oauth.mapper.MemberMapper;
import com.oauth.service.mapper.StatisticService;
import com.oauth.utils.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class StatisticServiceImpl implements StatisticService {

    @Autowired
    MemberMapper memberMapper;
    @Autowired
    DeviceMapper deviceMapper;

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

    /** 환기 공기질 주기보고 통계조회 */
    @Override
    public HashMap<String, Object> doVentilationAirStats(AuthServerDTO params) throws CustomException {
        List<AuthServerDTO> deviceWorkTimeInfo;

        HashMap<String, Object> resultMap = new LinkedHashMap<String, Object>();

        String deviceId = params.getDeviceId();
        String startDate = params.getStartDate();
        String endDate = params.getEndDate();

        // "stats" 부분을 표현하는 List 생성
        List<Map<String, Object>> statsList = new ArrayList<>();
        try {

            // 환기 공기질 정보 테이블에 해당 DeviceId가 있는지 확인 없으면 1018 Return
            if(deviceMapper.getDeviceIdFromVentilationAirInfo(deviceId).getDeviceCount().equals("0")){
                resultMap.put("resultCode", "1018");
                resultMap.put("resultMsg", "일별 가동시간 통계조회 조회 실패");
                return resultMap;
            }

            // StartDate 와 EndDate가 같은 경우 일 단위 조회
            if(startDate.equals(endDate)){
                deviceWorkTimeInfo = deviceMapper.getVentilationAirInfoDaily(params);
            } else {
                deviceWorkTimeInfo = deviceMapper.getVentilationAirInfoMonthly(params);
            }

            if(deviceWorkTimeInfo == null){
                resultMap.put("resultCode", "1018");
                resultMap.put("resultMsg", "일별 가동시간 통계조회 조회 실패");
                return resultMap;
            }

            for (AuthServerDTO authServerDTO : deviceWorkTimeInfo) {
                Map<String, Object> stats = new LinkedHashMap<>();
                stats.put("workDate", authServerDTO.getWorkDate());
                stats.put("indoorTemp", authServerDTO.getIndoorTemp());
                stats.put("indoorHumi", authServerDTO.getIndoorHumi());
                stats.put("pm10", authServerDTO.getPm10());
                stats.put("pm25", authServerDTO.getPm25());
                stats.put("co2", authServerDTO.getCo2());
                if (startDate.equals(endDate)) {
                    stats.put("timeInfo", authServerDTO.getTimeInfo());
                }
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

    /** 각방 보일러 사용 통계조회 */
    @Override
    public HashMap<String, Object> doEachRoomStatInfo(AuthServerDTO params) throws CustomException {
        
        

        try {
            
        } catch (Exception e) {
            log.error("", e);
        }
        return null;
    }

}
