package com.oauth.service.mapper;

import com.oauth.dto.AuthServerDTO;
import com.oauth.utils.CustomException;
import org.springframework.http.ResponseEntity;

public interface ReservationService {

    /** 24시간 예약 */
    ResponseEntity<?> doSet24(AuthServerDTO params) throws CustomException;

    /** 반복(12시간) 예약  */
    ResponseEntity<?> doSet12(AuthServerDTO params) throws CustomException;

    /** 빠른 온수 예약  */
    ResponseEntity<?> doAwakeAlarmSet(AuthServerDTO params) throws CustomException;

}
