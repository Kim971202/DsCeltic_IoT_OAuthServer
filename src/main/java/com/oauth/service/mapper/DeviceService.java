package com.oauth.service.mapper;

import com.oauth.dto.AuthServerDTO;
import com.oauth.utils.CustomException;
import org.springframework.http.ResponseEntity;

import java.sql.SQLException;
import java.util.HashMap;

public interface DeviceService {

    /** 전원 On/Off */
    ResponseEntity<?> doPowerOnOff(AuthServerDTO params) throws CustomException;

    /** 홈 IoT 컨트롤러 정보 등록/수정 */
    ResponseEntity<?> doDeviceInfoUpsert(AuthServerDTO params) throws Exception;

    /** 홈 IoT 컨트롤러 상태 정보 조회  */
    ResponseEntity<?> doDeviceStatusInfo(AuthServerDTO params) throws CustomException;

    /** 모드변경  */
    ResponseEntity<?> doModeChange(AuthServerDTO params) throws CustomException;

    /** 실내온도 설정  */
    public ResponseEntity<?> doTemperatureSet(AuthServerDTO params) throws CustomException;

    /** 난방수온도 설정  */
    public ResponseEntity<?> doBoiledWaterTempertureSet(AuthServerDTO params) throws CustomException;

    /** 온수온도 설정 */
    public ResponseEntity<?> doWaterTempertureSet(AuthServerDTO params) throws CustomException;

    /** 빠른온수 설정 */
    public ResponseEntity<?> doFastHotWaterSet(AuthServerDTO params) throws CustomException;

    /** 잠금 모드 설정  */
    public ResponseEntity<?> doLockSet(AuthServerDTO params) throws CustomException;

    /** 홈 IoT 컨트롤러 상태 정보 조회 – 홈 화면  */
    public ResponseEntity<?> doBasicDeviceStatusInfo(AuthServerDTO params) throws Exception;

    /** 홈 IoT 컨트롤러 정보 조회-단건 */
    public HashMap<String, Object> doDeviceInfoSearch(AuthServerDTO params) throws Exception;

    /**	홈 IoT 컨트롤러 에러 정보 조회  */
    public ResponseEntity<?> doDeviceErrorInfo(AuthServerDTO params) throws Exception;

    /**	홈 IoT 정보 조회 - 리스트  */
    ResponseEntity<?> doDeviceInfoSearchList(AuthServerDTO params) throws CustomException;

    /**	홈 IoT 컨트롤러 풍량 단수 설정  */
    ResponseEntity<?> doVentilationFanSpeedSet(AuthServerDTO params) throws CustomException;

}
