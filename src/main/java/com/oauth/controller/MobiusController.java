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
        System.out.println("uuId: " + uuId);
        String userId;
        String resultCode;
        String functionId;
        String redisValue = redisCommand.getValues(uuId);
        System.out.println("redisValue: " + redisValue);
        List<String> redisValueList;
        if(!redisValue.equals("false")){
            redisValueList = common.getUserIdAndFunctionId(redisCommand.getValues(uuId));
            userId = redisValueList.get(0);
            functionId = redisValueList.get(1);
            System.out.println("userId: " + userId);
            System.out.println("functionId: " + functionId);
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
            System.out.println(JSON.toJson(dr910W));
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
            System.out.println(JSON.toJson(dr910W));
            gwMessagingSystem.sendMessage(functionId + uuId, JSON.toJson(dr910W));
        } else {
            resultCode = common.readCon(jsonBody, "rtCd");
            System.out.println("resultCode: " + resultCode);
            gwMessagingSystem.sendMessage(functionId + uuId, JSON.toJson(resultCode));
        }
        return null;
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