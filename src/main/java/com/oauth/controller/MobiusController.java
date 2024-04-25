package com.oauth.controller;

import com.oauth.dto.gw.DeviceStatusInfo;
import com.oauth.mapper.DeviceMapper;
import com.oauth.message.GwMessagingSystem;
import com.oauth.service.impl.MobiusService;
import com.oauth.service.impl.PushService;
import com.oauth.utils.Common;
import com.oauth.utils.JSON;
import com.oauth.utils.RedisCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
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
    @Autowired
    PushService pushService;
    @PostMapping(value = "/GatewayToAppServer")
    @ResponseBody
    public String receiveCin(@RequestBody String jsonBody) throws Exception {

        System.out.println("GW Received JSON: " + JSON.toJson(jsonBody, true));

        DeviceStatusInfo dr910W = new DeviceStatusInfo();
        DeviceStatusInfo.Device dr910WDevice = new DeviceStatusInfo.Device();

        /* *
         * 1. Redis에서 받은 uuId로 Redis에 저장된 Value값을 검색한다.
         * 2. 해당 Value는 userId,functionId 형태로 저장됨
         * 3. common에 함수를 사용하여 userId와 functionId를 추출
         * 4. 추출한 functionId를 기반으로 해당 functionId에 맞는 if문으로 분기
         * */

        String uuId = common.readCon(jsonBody, "uuId");
        log.info("uuId: " + uuId);

        String errorCode = common.readCon(jsonBody, "erCd");
        String errorDateTime = common.readCon(jsonBody, "erDt");
        String controlAuthKey = common.readCon(jsonBody, "rKey");
        String[] serialNumber = common.readCon(jsonBody, "sur").split("/");

        String mfStFunctionId = common.readCon(jsonBody, "functionId");
        String rtStFunctionId = common.readCon(jsonBody, "functionId");

        String userId;
        String resultCode;
        String functionId = "null";
        String redisValue = redisCommand.getValues(uuId);
        log.info("redisValue: " + redisValue);
        List<String> redisValueList;
        if(!redisValue.equals("false")){
            redisValueList = common.getUserIdAndFunctionId(redisCommand.getValues(uuId));
            userId = redisValueList.get(0);
            functionId = redisValueList.get(1);
            log.info("userId: " + userId);
            log.info("functionId: " + functionId);
        } else if(!errorCode.equals("null") && !errorDateTime.equals("null")){
            mobiusService.errorHandler(serialNumber[0], controlAuthKey, errorCode, errorDateTime);
            return "DB Added";
        } else if(mfStFunctionId.equals("mfSt")){
            // 변경실시간상태

            pushService.sendPushMessage(jsonBody);

            if(common.readCon(jsonBody, "htTp") != null){ // 실내 온도 설정 - 실내 난방

            } else if(common.readCon(jsonBody, "wtTp") != null){ // 난방수 온도 설정 - 온돌 난방

            } else if(common.readCon(jsonBody, "hwTp") != null){ // 온수 온도 설정 - 온수 전용

            }
        } else if(rtStFunctionId.equals("rtSt")){
            // 주기상태보고
            dr910WDevice.setDeviceId(common.readCon(jsonBody, "deviceId"));
            dr910WDevice.setRKey(common.readCon(jsonBody, "rKey"));
            dr910WDevice.setSerialNumber(common.readCon(jsonBody, "srNo"));
            dr910WDevice.setPowr(common.readCon(jsonBody, "powr"));
            dr910WDevice.setOpMd(common.readCon(jsonBody, "opMd"));
            dr910WDevice.setHtTp(common.readCon(jsonBody, "htTp"));
            dr910WDevice.setWtTp(common.readCon(jsonBody, "wtTp"));
            dr910WDevice.setHwTp(common.readCon(jsonBody, "hwTp"));
            dr910WDevice.setStringRsCf(common.readCon(jsonBody, "rsCf"));
            dr910WDevice.setFtMd(common.readCon(jsonBody, "ftMd"));
            dr910WDevice.setBCdt(common.readCon(jsonBody, "bCdt"));
            dr910WDevice.setChTp(common.readCon(jsonBody, "chTp"));
            dr910WDevice.setCwTp(common.readCon(jsonBody, "cwTp"));
            dr910WDevice.setHwSt(common.readCon(jsonBody, "hwSt"));
            dr910WDevice.setSlCd(common.readCon(jsonBody, "slCd"));
            dr910WDevice.setMfDt(common.readCon(jsonBody, "mfDt"));
            mobiusService.rtstHandler(dr910WDevice);

        } else {
            return "0x0106-Devices 상태 보고 요청";
        }

        // 홈 IoT 컨트롤러 상태 정보 조회 - 홈화면
        if(functionId.equals("fcnt-homeView")){
            // TODO: GW서버에서 넘겨주는 format을 알아야 함 배열로 주는 건지 여러번 한개씩 주는건지

            dr910WDevice.setRKey(common.readCon(jsonBody, "rKey"));
            dr910WDevice.setPowr(common.readCon(jsonBody, "powr"));
            dr910WDevice.setOpMd(common.readCon(jsonBody, "opMd"));
            dr910WDevice.setHtTp(common.readCon(jsonBody, "htTp"));
            dr910WDevice.setWtTp(common.readCon(jsonBody, "wtTp"));
            dr910WDevice.setHwTp(common.readCon(jsonBody, "hwTp"));
            dr910WDevice.setFtMd(common.readCon(jsonBody, "ftMd"));
            dr910WDevice.setChTp(common.readCon(jsonBody, "chTp"));
            dr910WDevice.setMfDt(common.readCon(jsonBody, "mfDt"));
            dr910WDevice.setSlCd(common.readCon(jsonBody, "slCd"));
            dr910WDevice.setHwSt(common.readCon(jsonBody, "hwSt"));
            dr910WDevice.setCwTp(common.readCon(jsonBody, "fcLc"));
            dr910W.setDevice(dr910WDevice);
            log.info(JSON.toJson(dr910W));
            gwMessagingSystem.sendMessage(functionId + uuId, JSON.toJson(dr910W));

        }else if(functionId.equals("fcnt")){

            // 홈 IoT 컨트롤러 상태 정보 조회 - 상세조회
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
            log.info(JSON.toJson(dr910W));
            gwMessagingSystem.sendMessage(functionId + uuId, JSON.toJson(dr910W));
        } else {
            resultCode = common.readCon(jsonBody, "rtCd");
            gwMessagingSystem.sendMessage(functionId + uuId, JSON.toJson(resultCode));
            log.info("resultCode: " + resultCode);
        }
        return null;
    }
}