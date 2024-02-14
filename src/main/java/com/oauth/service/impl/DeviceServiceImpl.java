package com.oauth.service.impl;

import com.oauth.dto.AuthServerDTO;
import com.oauth.dto.gw.DeviceInfoUpsert;
import com.oauth.dto.gw.DeviceStatusInfoDR910W;
import com.oauth.dto.gw.PowerOnOff;
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
            deviceInfoUpsert.setOldAddr(params.getOldAddr());
            deviceInfoUpsert.setNewAddr(params.getNewAddr());
            deviceInfoUpsert.setAddrDetail(params.getAddrDetail());
            deviceInfoUpsert.setLatitude(params.getLatitude());
            deviceInfoUpsert.setLongitude(params.getLongitude());
            deviceInfoUpsert.setDeviceNickname(params.getDeviceNickname());
            deviceInfoUpsert.setAddrNickname(params.getAddrNickname());

            deviceInfoUpsert.setFunctionId("mfAr");
            deviceInfoUpsert.setUuId(common.getTransactionId());

            // deviceId, controlAuthKey 가 모두 존재할 경우에만 수정으로 판단
            if(deviceId != null && controlAuthKey != null && registYn.equals("N")){

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
                    stringObject = "N";
                    redisCommand.setValues(deviceInfoUpsert.getUuId(), userId);
                    mobiusService.createCin(serialNumber, userId, JSON.toJson(deviceInfoUpsert));
                }
                else stringObject = "Y";

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
                result.setDeviceStatusInfoDR910W(dr910W);
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
}
