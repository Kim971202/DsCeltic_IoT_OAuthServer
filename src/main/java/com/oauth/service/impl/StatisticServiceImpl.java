package com.oauth.service.impl;

import com.oauth.dto.AuthServerDTO;
import com.oauth.dto.gw.DeviceStatusInfo;
import com.oauth.mapper.DeviceMapper;
import com.oauth.mapper.MemberMapper;
import com.oauth.response.ApiResponse;
import com.oauth.service.mapper.StatisticService;
import com.oauth.utils.Common;
import com.oauth.utils.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.lang.reflect.Method;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class StatisticServiceImpl implements StatisticService {

    @Autowired
    MemberMapper memberMapper;
    @Autowired
    DeviceMapper deviceMapper;
    @Autowired
    Common common;

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
            log.info("resultMap: {} ", resultMap);
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
            log.info("resultMap: {} ", resultMap);
            return resultMap;
        } catch (Exception e){
            log.error("", e);
        }
        return null;
    }

    /** 각방 보일러 사용 통계조회 */
    @Override
    public HashMap<String, Object> doEachRoomStatInfo(AuthServerDTO params) throws CustomException {
        HashMap<String, Object> resultMap = new LinkedHashMap<String, Object>();

        String deviceId = params.getDeviceId();
        String regDatetime = params.getStartDate();
        String registrationDatetime;
        DeviceStatusInfo.Device device = new DeviceStatusInfo.Device();
        List<Map<String, Object>> statsList = new ArrayList<>();
        List<DeviceStatusInfo.Device> deviceList;

        try {

            /*
            * 1. 받은 DeviceId로 기기 등록 시간 쿼리
            * 2. 등록일 +1일 이후 부터 조회 가능
            * */

            registrationDatetime = deviceMapper.getRegistrationtimeByDeviceId(deviceId).getRegistrationDatetime();

            DateTimeFormatter datetimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy.M.d");

            LocalDate baseDate  = LocalDateTime.parse(registrationDatetime, datetimeFormatter).toLocalDate();
            LocalDate compareDate  = LocalDate.parse(regDatetime, dateFormatter);

            if (compareDate.isAfter(baseDate)) {
                // 조건 통과: 더 이후 날짜
                device.setDeviceId(deviceId);
                device.setRegDatetime(regDatetime);

                deviceList = deviceMapper.selectEachRoomModeInfo(device);

                for (DeviceStatusInfo.Device singleDevice : deviceList) {
                    HashMap<String, Object> dataMap = new LinkedHashMap<>();
                    HashMap<String, Object> timeInfoMap = new LinkedHashMap<>();
                    dataMap.put("subDevice", singleDevice.getDeviceId());
                    dataMap.put("subDeviceNickname", deviceMapper.getSubDeviceNickname(singleDevice.getDeviceId()).getDeviceNickname());
                    dataMap.put("opMd", singleDevice.getOpMd());
                    dataMap.put("chTp", singleDevice.getChTp());

                    for (int i = 1; i <= 24; i++) {
                        String key = String.format("%02d", i); // "01" ~ "24"
                        String methodName = "getT" + key;
                        try {
                            Method method = singleDevice.getClass().getMethod(methodName);
                            String value = (String) method.invoke(singleDevice);
                            Object output = common.tValue(value); // 하나의 tValue 메서드만 사용
                            timeInfoMap.put(key, output);
                        } catch (Exception e) {
                            log.error("", e);
                            timeInfoMap.put(key, "0");
                        }
                        dataMap.put("timeInfo", timeInfoMap);
                    }
                    statsList.add(dataMap);
                }
            }

            resultMap.put("stats", statsList);
            resultMap.put("resultCode", "200");
            resultMap.put("resultMsg", "일별 가동시간 통계조회 조회 성공");

            log.info("resultMap: {} ", resultMap);
            return resultMap;
        } catch (Exception e) {
            log.error("", e);
        }
        return null;
    }

}
