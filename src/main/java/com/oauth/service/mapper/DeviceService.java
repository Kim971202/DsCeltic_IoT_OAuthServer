package com.oauth.service.mapper;

import com.oauth.dto.AuthServerDTO;
import com.oauth.utils.CustomException;
import org.springframework.http.ResponseEntity;

public interface DeviceService {

    /** 전원 On/Off */
    ResponseEntity<?> doPowerOnOff(AuthServerDTO params) throws CustomException;

    /** 홈 IoT 컨트롤러 상태 정보 조회  */
    ResponseEntity<?> doDeviceStatusInfo(AuthServerDTO params) throws CustomException;

}
