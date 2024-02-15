package com.oauth.service.impl;

import com.oauth.constants.MobiusResponse;
import com.oauth.dto.AuthServerDTO;
import com.oauth.dto.gw.*;
import com.oauth.mapper.DeviceMapper;
import com.oauth.response.ApiResponse;
import com.oauth.service.mapper.DeviceService;
import com.oauth.utils.Common;
import com.oauth.utils.CustomException;
import com.oauth.utils.JSON;
import com.oauth.utils.RedisCommand;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.sql.SQLException;

@Service
public class DeviceServiceImpl implements DeviceService {

    @Autowired
    Common common;
    @Autowired
    MobiusService mobiusService;
    @Autowired
    DeviceMapper deviceMapper;
    @Autowired
    RedisCommand redisCommand;
    @Autowired
    SqlSessionFactory sqlSessionFactory;
    @Autowired
    MobiusResponse mobiusResponse;

    /** 전원 On/Off */
    @Override
    public ResponseEntity<?> doPowerOnOff(AuthServerDTO params) throws CustomException{

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject = null;
        String msg = null;
        PowerOnOff powerOnOff = new PowerOnOff();
        String serialNumber = null;
        String userId = params.getUserId();
        String deviceId = params.getDeviceId();
        String jsonBody = null;
        try {
            powerOnOff.setUserId(params.getUserId());
            powerOnOff.setDeviceId(params.getDeviceId());
            powerOnOff.setControlAuthKey(params.getControlAuthKey());
            powerOnOff.setDeviceType(params.getDeviceType());
            powerOnOff.setModelCode(params.getModelCode());
            powerOnOff.setPowerStatus(params.getPowerStatus());
            powerOnOff.setFunctionId("powr");
            powerOnOff.setUuId(common.getTransactionId());

            redisCommand.setValues(powerOnOff.getUuId(), userId);

            AuthServerDTO device = deviceMapper.getSerialNumberBydeviceId(deviceId);
            serialNumber = device.getSerialNumber();

            if(!serialNumber.isEmpty()) {
                stringObject = "Y";
                mobiusService.createCin(serialNumber, userId, JSON.toJson(powerOnOff));
            } else stringObject = "N";

            if(stringObject.equals("Y")) msg = "전원 On/Off 성공";
            else msg = "전원 On/Off 실패";

            result.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);

            return new ResponseEntity<>(result, HttpStatus.OK);
        }catch (Exception e){
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /** 홈 IoT 컨트롤러 정보 등록/수정 */
    @Override
    public ResponseEntity<?> doDeviceInfoUpsert(AuthServerDTO params) throws CustomException, SQLException {

        // Transaction용 클래스 선언
        SqlSession session = sqlSessionFactory.openSession();
        // 자동 Commit 기능 OFF
        session.getConnection().setAutoCommit(false);

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject = null;
        String msg = null;
        DeviceInfoUpsert deviceInfoUpsert = new DeviceInfoUpsert();
        String userId = params.getUserId();
        String deviceId = params.getDeviceId();
        String controlAuthKey = params.getControlAuthKey();
        String registYn = params.getRegistYn();
        String serialNumber = params.getSerialNumber();

        int insertDeviceModelCodeResult = 0;
        int insertDeviceResult = 0;
        int insertDeviceRegistResult = 0;
        int insertDeviceDetailResult = 0;

        int updateDeviceRegistLocationResult = 0;
        int updateDeviceDetailLocationResult = 0;
        try {

            deviceInfoUpsert.setAccessToken(params.getAccessToken());
            deviceInfoUpsert.setUserId(params.getUserId());
            deviceInfoUpsert.setHp(params.getHp());
            deviceInfoUpsert.setRegisYn(registYn);
            deviceInfoUpsert.setDeviceId(deviceId);
            deviceInfoUpsert.setControlAuthKey(controlAuthKey);
            deviceInfoUpsert.setTmpRegistryKey(params.getTmpRegistKey());
            deviceInfoUpsert.setDeviceType(params.getDeviceType());
            deviceInfoUpsert.setModelCode(params.getModelCode());
            deviceInfoUpsert.setSerialNumber(params.getSerialNumber());
            deviceInfoUpsert.setZipCode(params.getZipCode());
//            deviceInfoUpsert.setOldAddr(params.getOldAddr());
//            deviceInfoUpsert.setNewAddr(params.getNewAddr());
//            deviceInfoUpsert.setAddrDetail(params.getAddrDetail());
            deviceInfoUpsert.setLatitude(params.getLatitude());
            deviceInfoUpsert.setLongitude(params.getLongitude());
            deviceInfoUpsert.setDeviceNickname(params.getDeviceNickname());
//            deviceInfoUpsert.setAddrNickname(params.getAddrNickname());s

            deviceInfoUpsert.setFunctionId("mfAr");
            deviceInfoUpsert.setUuId(common.getTransactionId());

            if(registYn.equals("N")){

                /* *
                 * IoT 디바이스 UPDATE 순서
                 * 1. TBT_OPR_DEVICE_REGIST - 임시 단말 등록 정보
                 * 2. TBR_OPR_DEVICE_DETAIL - 단말정보상세
                 * */

                try{
                    DeviceMapper dMapper = session.getMapper(DeviceMapper.class);

                    updateDeviceRegistLocationResult = dMapper.updateDeviceRegistLocation(params);
                    updateDeviceDetailLocationResult = dMapper.updateDeviceDetailLocation(params);

                    session.commit();
                }catch (Exception e){
                    session.rollback();
                }

                if(updateDeviceRegistLocationResult <= 0 || updateDeviceDetailLocationResult <= 0){
                    stringObject = "Y";
                    redisCommand.setValues(deviceInfoUpsert.getUuId(), userId);
                    mobiusService.createCin("gwSever", "gwSeverCnt", JSON.toJson(deviceInfoUpsert));
                }
                else stringObject = "N";
            } else {

                /* *
                 * IoT 디바이스 등록 INSERT 순서
                 * 1. TBD_IOT_DEVICE_MODL_CD - 디바이스 모델 코드
                 * 2. TBR_IOT_DEVICE - 디바이스
                 * 3. TBT_OPR_DEVICE_REGIST - 임시 단말 등록 정보
                 * 4. TBR_OPR_DEVICE_DETAIL - 단말정보상세
                 * */
                try {
                    DeviceMapper dMapper = session.getMapper(DeviceMapper.class);

                    insertDeviceModelCodeResult = dMapper.insertDeviceModelCode(params);
                    insertDeviceResult = dMapper.insertDevice(params);
                    insertDeviceRegistResult = dMapper.insertDeviceRegist(params);
                    insertDeviceDetailResult = dMapper.insertDeviceDetail(params);

                    // 여기까지 왔다면 모든 작업이 성공했으므로 커밋
                    session.commit();
                } catch (Exception e) {
                    session.rollback();
                } finally {
                    session.close(); // 세션을 닫아줘야 합니다.
                }
                // TODO: stringObject 관련 처리 구문 추가 필요
//                insertDeviceModelCodeResult = deviceMapper.insertDeviceModelCode(params);
//                if(insertDeviceModelCodeResult <= 0) stringObject = "N";
//                else {
//                    insertDeviceResult = deviceMapper.insertDevice(params);
//
//                    if(insertDeviceResult <= 0) stringObject = "N";
//                    else {
//                        insertDeviceRegistResult = deviceMapper.insertDeviceRegist(params);
//
//                        if(insertDeviceRegistResult <= 0) stringObject = "N";
//                        else {
//                            insertDeviceDetailResult = deviceMapper.insertDeviceDetail(params);
//
//                            if(insertDeviceDetailResult <= 0) stringObject = "N";
//                            else stringObject = "Y";
//                        }
//                    }
//                }
            }

            if(stringObject.equals("Y")) {
                msg = "홈 IoT 컨트롤러 정보 등록/수정 성공";
                result.setLatitude(params.getLatitude());
                result.setLongitude(params.getLongitude());
                result.setTmpRegistKey(params.getTmpRegistKey());
            }
            else msg = "홈 IoT 컨트롤러 정보 등록/수정 실패";

            result.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);

            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (CustomException e){
            System.out.println(e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    /** 홈 IoT 컨트롤러 상태 정보 조회  */
    @Override
    public ResponseEntity<?> doDeviceStatusInfo(AuthServerDTO params) throws CustomException{

        ApiResponse.Data result = new ApiResponse.Data();
        DeviceStatusInfoDR910W dr910W = DeviceStatusInfoDR910W.getInstance();
        String stringObject = null;
        String msg = null;
        String serialNumber = null;
        String userId = params.getUserId();
        String deviceId = params.getDeviceId();
        String jsonBody = null;
        try {
            if(dr910W.getRKey() != null){
                stringObject = "Y";
                dr910W.setModelCategoryCode("01");
                dr910W.setDeviceStatus("1");
                result.setDeviceStatusInfo(dr910W);
            } else stringObject = "N";

            if(stringObject.equals("Y")) msg = "홈 IoT 컨트롤러 상태 정보 조회 성공";
            else msg = "홈 IoT 컨트롤러 상태 정보 조회 실패";

            result.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);

            return new ResponseEntity<>(result, HttpStatus.OK);
        }catch (Exception e){
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /** 모드변경  */
    @Override
    public ResponseEntity<?> doModeChange(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        ModeChange modeChange = new ModeChange();
        String stringObject = null;
        String msg = null;

        String modeCode = params.getModeCode();
        String sleepCode = params.getSleepCode();
        String userId = params.getUserId();
        try  {

            modeChange.setAccessToken(params.getAccessToken());
            modeChange.setUserId(params.getUserId());
            modeChange.setDeviceId(params.getDeviceId());
            modeChange.setControlAuthKey(params.getControlAuthKey());
            modeChange.setModelCode(params.getModelCode());
            modeChange.setModeCode(modeCode);
            modeChange.setSleepCode(sleepCode);
            modeChange.setFunctionId("opMd");
            modeChange.setUuid(common.getTransactionId());

            redisCommand.setValues(modeChange.getUuid(), userId);
            mobiusResponse = mobiusService.createCin("gwSever", "gwSeverCnt", JSON.toJson(modeChange));

            if(mobiusResponse.getResponseCode().equals("201")) stringObject = "Y";
            else stringObject = "N";

            System.out.println("mobiusResponse.getResponseCode(): " + mobiusResponse.getResponseCode());
            if(stringObject.equals("Y")) msg = "모드변경 성공";
            else msg = "모드변경 실패";

            result.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);

            return new ResponseEntity<>(result, HttpStatus.OK);
        }catch (Exception e){
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /** 실내온도 설정  */
    @Override
    public ResponseEntity<?> doTemperatureSet(AuthServerDTO params) throws CustomException{

        ApiResponse.Data result = new ApiResponse.Data();
        TemperatureSet temperatureSet = new TemperatureSet();
        String stringObject = null;
        String msg = null;

        String userId = params.getUserId();

        try {

            temperatureSet.setAccessToken(params.getAccessToken());
            temperatureSet.setUserId(userId);
            temperatureSet.setDeviceId(params.getDeviceId());
            temperatureSet.setControlAuthKey(params.getControlAuthKey());
            temperatureSet.setTemperature(params.getTemperture());
            temperatureSet.setFunctionId("htTp");
            temperatureSet.setUuId(common.getTransactionId());

            redisCommand.setValues(temperatureSet.getUuId(), userId);
            mobiusResponse = mobiusService.createCin("gwSever", "gwSeverCnt", JSON.toJson(temperatureSet));

            if(mobiusResponse.getResponseCode().equals("201")) stringObject = "Y";
            else stringObject = "N";

            System.out.println("mobiusResponse.getResponseCode(): " + mobiusResponse.getResponseCode());
            if(stringObject.equals("Y")) msg = "실내온도 설정 성공";
            else msg = "실내온도 설정 실패";

            result.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    /** 난방수온도 설정  */
    @Override
    public ResponseEntity<?> doBoiledWaterTempertureSet(AuthServerDTO params) throws CustomException{

        ApiResponse.Data result = new ApiResponse.Data();
        BoiledWaterTempertureSet boiledWaterTempertureSet = new BoiledWaterTempertureSet();
        String stringObject = null;
        String msg = null;

        String userId = params.getUserId();

        try {

            boiledWaterTempertureSet.setAccessToken(params.getAccessToken());
            boiledWaterTempertureSet.setUserId(userId);
            boiledWaterTempertureSet.setDeviceId(params.getDeviceId());
            boiledWaterTempertureSet.setControlAuthKey(params.getControlAuthKey());
            boiledWaterTempertureSet.setTemperature(params.getTemperture());
            boiledWaterTempertureSet.setFunctionId("wtTp");
            boiledWaterTempertureSet.setUuId(common.getTransactionId());

            redisCommand.setValues(boiledWaterTempertureSet.getUuId(), userId);
            mobiusResponse = mobiusService.createCin("gwSever", "gwSeverCnt", JSON.toJson(boiledWaterTempertureSet));

            if(mobiusResponse.getResponseCode().equals("201")) stringObject = "Y";
            else stringObject = "N";

            System.out.println("mobiusResponse.getResponseCode(): " + mobiusResponse.getResponseCode());
            if(stringObject.equals("Y")) msg = "난방수온도 설정 성공";
            else msg = "난방수온도 설정 실패";

            result.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);
            return new ResponseEntity<>(result, HttpStatus.OK);

        } catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    /** 온수온도 설정 */
    @Override
    public ResponseEntity<?> doWaterTempertureSet(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        WaterTempertureSet waterTempertureSet = new WaterTempertureSet();
        String stringObject = null;
        String msg = null;
        String userId = params.getUserId();

        try {

            waterTempertureSet.setAccessToken(params.getAccessToken());
            waterTempertureSet.setUserId(userId);
            waterTempertureSet.setDeviceId(params.getDeviceId());
            waterTempertureSet.setControlAuthKey(params.getControlAuthKey());
            waterTempertureSet.setTemperature(params.getTemperture());
            waterTempertureSet.setFunctionId("hwTp");
            waterTempertureSet.setUuId(common.getTransactionId());

            redisCommand.setValues(waterTempertureSet.getUuId(), userId);
            mobiusResponse = mobiusService.createCin("gwSever", "gwSeverCnt", JSON.toJson(waterTempertureSet));

            if(mobiusResponse.getResponseCode().equals("201")) stringObject = "Y";
            else stringObject = "N";

            if(stringObject.equals("Y")) msg = "온수온도 설정 성공";
            else msg = "온수온도 설정 실패";

            result.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    /** 빠른온수 설정 */
    @Override
    public ResponseEntity<?> doFastHotWaterSet(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        FastHotWaterSet fastHotWaterSet = new FastHotWaterSet();
        String stringObject = null;
        String msg = null;
        String userId = params.getUserId();

        try {

            fastHotWaterSet.setAccessToken(params.getAccessToken());
            fastHotWaterSet.setUserId(userId);
            fastHotWaterSet.setDeviceId(params.getDeviceId());
            fastHotWaterSet.setControlAuthKey(params.getControlAuthKey());
            fastHotWaterSet.setModeCode(params.getModeCode());
            fastHotWaterSet.setFunctionId("ftMd");
            fastHotWaterSet.setUuId(common.getTransactionId());

            redisCommand.setValues(fastHotWaterSet.getUuId(), userId);
            mobiusResponse = mobiusService.createCin("gwSever", "gwSeverCnt", JSON.toJson(fastHotWaterSet));

            if(mobiusResponse.getResponseCode().equals("201")) stringObject = "Y";
            else stringObject = "N";

            if(stringObject.equals("Y")) msg = "빠른온수 설정 성공";
            else msg = "빠른온수 설정 실패";

            result.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);
            return new ResponseEntity<>(result, HttpStatus.OK);

        } catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    /** 잠금 모드 설정  */
    @Override
    public ResponseEntity<?> doLockSet(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        LockSet lockSet = new LockSet();
        String stringObject = null;
        String msg = null;
        String userId = params.getUserId();

        try {

            lockSet.setAccessToken(params.getAccessToken());
            lockSet.setUserId(userId);
            lockSet.setDeviceId(params.getDeviceId());
            lockSet.setControlAuthKey(params.getControlAuthKey());
            lockSet.setLockSet(params.getLockSet());
            lockSet.setFunctionId("fcLc");
            lockSet.setUuId(common.getTransactionId());

            redisCommand.setValues(lockSet.getUuId(), userId);
            mobiusResponse = mobiusService.createCin("gwSever", "gwSeverCnt", JSON.toJson(lockSet));

            if(mobiusResponse.getResponseCode().equals("201")) stringObject = "Y";
            else stringObject = "N";

            if(stringObject.equals("Y")) msg = "잠금 모드 설정 성공";
            else msg = "잠금 모드 설정 실패";

            result.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }



}
