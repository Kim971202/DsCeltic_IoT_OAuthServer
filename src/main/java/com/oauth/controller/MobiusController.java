package com.oauth.controller;

import com.oauth.dto.gw.DeviceStatusInfo;
import com.oauth.mapper.DeviceMapper;
import com.oauth.message.GwMessagingSystem;
import com.oauth.service.impl.MobiusService;
import com.oauth.utils.Common;
import com.oauth.utils.JSON;
import com.oauth.utils.RedisCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class MobiusController {

    @Autowired
    Common common;
    @Autowired
    RedisCommand redisCommand;
    @Autowired
    MobiusService mobiusService;
    @Autowired
    DeviceMapper deviceMapper;
    @Autowired
    GwMessagingSystem gwMessagingSystem;

    @PostMapping(value = "/GatewayToAppServer")
    @ResponseBody
    public String receiveCin(@RequestBody String jsonBody) throws Exception {

        DeviceStatusInfo dr910W = new DeviceStatusInfo();
        DeviceStatusInfo.Device dr910WDevice = new DeviceStatusInfo.Device();



        /**
         * 1. Redis에서 받은 uuId로 Redis에 저장된 Value값을 검색한다.
         * 2. 해당 Value는 userId,functionId 형태로 저장됨
         * 3. common에 함수를 사용하여 userId와 functionId를 추출
         * 4. 추출한 functionId를 기반으로 해당 functionId에 맞는 if문으로 분기
         * */

        String uuId = common.readCon(jsonBody, "uuId");
        String userId = null;
        String resultCode = null;
        String functionId = null;
        List<String> redisValue = common.getUserIdAndFunctionId(redisCommand.getValues(uuId));
        if(redisValue != null){
            userId = redisValue.get(0);
            functionId = redisValue.get(1);
            System.out.println("userId: " + userId);
            System.out.println("functionId: " + functionId);
        }



        // 전원 On/Off
        if(functionId.equals("powr")){
            resultCode = common.readCon(jsonBody, "uuId");
            gwMessagingSystem.sendMessage(functionId + uuId, JSON.toJson(resultCode));

        }

        // 밝기 조절
        if(functionId.equals("blCf")){
            resultCode = common.readCon(jsonBody, "rtCd");
            gwMessagingSystem.sendMessage(functionId + uuId, JSON.toJson(resultCode));
        }

        // 홈 IoT 컨트롤러 정보 등록/수정 (주소 변경 시)
        if(functionId.equals("mfAr")){
            resultCode = common.readCon(jsonBody, "rtCd");
            gwMessagingSystem.sendMessage(functionId + uuId, JSON.toJson(resultCode));
        }

        // 모드 변경
        if(functionId.equals("opMd")){
            resultCode = common.readCon(jsonBody, "rtCd");
            gwMessagingSystem.sendMessage(functionId + uuId, JSON.toJson(resultCode));
        }

        // 실내온도 설정
        if(functionId.equals("htTp")){
            resultCode = common.readCon(jsonBody, "rtCd");
            gwMessagingSystem.sendMessage(functionId + uuId, JSON.toJson(resultCode));
        }

        // 난방수온도 설정
        if(functionId.equals("wtTp")){
            resultCode = common.readCon(jsonBody, "rtCd");
            gwMessagingSystem.sendMessage(functionId + uuId, JSON.toJson(resultCode));
        }

        // 온수온도 설정
        if(functionId.equals("hwTp")){
            resultCode = common.readCon(jsonBody, "rtCd");
            gwMessagingSystem.sendMessage(functionId + uuId, JSON.toJson(resultCode));
        }

        // 빠른온수 설정
        if(functionId.equals("fwh")){
            resultCode = common.readCon(jsonBody, "rtCd");
            gwMessagingSystem.sendMessage(functionId + uuId, JSON.toJson(resultCode));
        }

        // 잠금모드 설정
        if(functionId.equals("fcLc")){
            resultCode = common.readCon(jsonBody, "rtCd");
            gwMessagingSystem.sendMessage(functionId + uuId, JSON.toJson(resultCode));
        }

        // 24시간 예약
        if(functionId.equals("24h")){
            resultCode = common.readCon(jsonBody, "rtCd");
            gwMessagingSystem.sendMessage(functionId + uuId, JSON.toJson(resultCode));
        }
        // 반복(12시간) 예약
        if(functionId.equals("12h")){
            resultCode = common.readCon(jsonBody, "rtCd");
            gwMessagingSystem.sendMessage(functionId + uuId, JSON.toJson(resultCode));
        }
        // 빠른 온수 예약
        if(functionId.equals("ftMd")){
            resultCode = common.readCon(jsonBody, "rtCd");
            gwMessagingSystem.sendMessage(functionId + uuId, JSON.toJson(resultCode));
        }
        // 주간 예약
        if(functionId.equals("7wk")){
            resultCode = common.readCon(jsonBody, "rtCd");
            gwMessagingSystem.sendMessage(functionId + uuId, JSON.toJson(resultCode));
        }
        // 홈 IoT 컨트롤러 상태 정보 조회 - 홈화면
        if(functionId.equals("fcnt-homeView")){

        }

        // 홈 IoT 컨트롤러 상태 정보 조회 - 상세조회
        if(functionId.equals("fcnt")){

            dr910WDevice.setPowr(common.readCon(jsonBody, "powr"));
            dr910WDevice.setOpMd(common.readCon(jsonBody, "opMd"));
            dr910WDevice.setHtTp(common.readCon(jsonBody, "htTp"));
            dr910WDevice.setWtTp(common.readCon(jsonBody, "wtTp"));
            dr910WDevice.setHwTp(common.readCon(jsonBody, "hwTp"));
            dr910WDevice.setRsCf(common.changeStringToJson(common.readCon(jsonBody, "rsCf")));
            dr910WDevice.setFtMd(common.readCon(jsonBody, "ftMd"));
            dr910WDevice.setBCdt(common.readCon(jsonBody, "bCdt"));
            dr910WDevice.setChTp(common.readCon(jsonBody, "chTp"));
            dr910WDevice.setCwTp(common.readCon(jsonBody, "cwTp"));
            dr910WDevice.setHwSt(common.readCon(jsonBody, "hwSt"));
            dr910WDevice.setSlCd(common.readCon(jsonBody, "slCd"));
            dr910WDevice.setMfDt(common.readCon(jsonBody, "mfDt"));

            if(!common.readCon(jsonBody, "ecOp").isEmpty() &&
                    !common.readCon(jsonBody, "fcLc").isEmpty() &&
                    !common.readCon(jsonBody, "blCf").isEmpty()){
                dr910WDevice.setEcOp(common.readCon(jsonBody, "ecOp"));
                dr910WDevice.setFcLc(common.readCon(jsonBody, "fcLc"));
                dr910WDevice.setBlCf(common.readCon(jsonBody, "blCf"));
            }

            dr910W.setModelCategoryCode("01");
            dr910W.setDeviceStatus("01");
            dr910W.setDevice(dr910WDevice);
            System.out.println(JSON.toJson(dr910W));
            gwMessagingSystem.sendMessage(functionId + uuId, JSON.toJson(dr910W));
        }
        return uuId;
    }
}
//        List<String> ufId = common.getUserIdAndFunctionId(redisCommand.getValues(common.readCon(reqBody, "uuId")));
//
//        String userId = ufId.get(0);
//        String functionId = ufId.get(0);
//
//
//
//
//        DeviceStatusInfoDR910W dr910W = DeviceStatusInfoDR910W.getInstance();
//        DeviceStatusInfoDR910W.Device dr910WDevice = new DeviceStatusInfoDR910W.Device();
//
//        dr910WDevice.setRKey(common.readCon(reqBody, "rKey"));
//        dr910WDevice.setMfcd(common.readCon(reqBody, "mfcd"));
//        dr910WDevice.setPowr(common.readCon(reqBody, "powr"));
//        dr910WDevice.setOpMd(common.readCon(reqBody, "opMd"));
//        dr910WDevice.setHtTp(common.readCon(reqBody, "htTp"));
//        dr910WDevice.setWtTp(common.readCon(reqBody, "wtTp"));
//        dr910WDevice.setHwTp(common.readCon(reqBody, "hwTp"));
//        dr910WDevice.setRsCf(common.readCon(reqBody, "rsCf"));
//        dr910WDevice.setFtMd(common.readCon(reqBody, "ftMd"));
//        dr910WDevice.setBCdt(common.readCon(reqBody, "bCdt"));
//        dr910WDevice.setChTp(common.readCon(reqBody, "chTp"));
//        dr910WDevice.setCwTp(common.readCon(reqBody, "cwTp"));
//        dr910WDevice.setHwSt(common.readCon(reqBody, "hwSt"));
//        dr910WDevice.setSlCd(common.readCon(reqBody, "slCd"));
//        dr910WDevice.setMfDt(common.readCon(reqBody, "mfDt"));
//
//        dr910WDevice.setModelCategoryCode("01");

        // TBR_OPR_USER_DEVICE - 사용자 단말 정보 Table에서 해당 DATA 취득
//        dr910WDevice.setDeviceNickName();
//        dr910WDevice.setAddrNickname();
//        dr910WDevice.setRegSort();
//        dr910WDevice.setDeviceId();
//        dr910WDevice.setControlAuthKey();
//        dr910WDevice.setDeviceStatus("1");

//        dr910W.addDr910W(dr910WDevice);

        //dr910W.setRsCf(common.changeStringToJson(common.readCon(reqBody, "rsCf")));