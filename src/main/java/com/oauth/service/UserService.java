package com.oauth.service;

import com.oauth.dto.member.MemberDTO;
import com.oauth.mapper.MemberMapper;
import com.oauth.response.ApiResponse;
import com.oauth.utils.Common;
import com.oauth.utils.CustomException;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class UserService {
    @Autowired
    MobiusService mobiusService;
    @Autowired
    private MemberMapper memberMapper;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private Common common;

    public ResponseEntity<?> doLogin(String userId, String userPassword) throws CustomException {

        String stringObject = null;
        String userNickname = null;
        String accessToken = null;

        ApiResponse.Data result = new ApiResponse.Data();

        // Device Set 생성
        Set<String> userDeviceIds = new HashSet<>();
        List<ApiResponse.Data.Device> data = new ArrayList<>();

        List<String> deviceId = null;
        List<String> controlAuthKey = null;
        List<String> deviceNickname = null;
        List<String> regSort = null;

        try {
            MemberDTO member = memberMapper.getUserByUserId(userId);
            if(member == null) {
                stringObject = "N";
            } else {
                userNickname = member.getUserNickname();
                accessToken = member.getAccessToken();
            }

            List<MemberDTO> deviceInfoList = memberMapper.getDeviceIdByUserId(userId);
            if(deviceInfoList == null) {
                stringObject = "N";
            } else {
                stringObject = "Y";

                deviceId = Common.extractJson(deviceInfoList.toString(), "deviceId");
                controlAuthKey = Common.extractJson(deviceInfoList.toString(), "controlAuthKey");
                deviceNickname = Common.extractJson(deviceInfoList.toString(), "deviceNickname");
                regSort = Common.extractJson(deviceInfoList.toString(), "regSort");
                // Mapper실행 후 사용자가 가지고 있는 Device 개수
                int numDevices = deviceInfoList.size();

                // 하나의 리스트에 값들을 합쳐서 저장
                List<String> combinedList = new ArrayList<>();
                for (int i = 0; i < deviceId.size(); i++) {
                    String combinedValue = deviceId.get(i) + " " + controlAuthKey.get(i) + " " +
                            deviceNickname.get(i) + " " + regSort.get(i);
                    combinedList.add(combinedValue);
                }

                // Device 추가
                for (int i = 0; i < numDevices; i++) {
                    ApiResponse.Data.Device device = Common.createDevice(
                            deviceId.get(i),
                            controlAuthKey.get(i),
                            deviceNickname.get(i),
                            regSort.get(i),
                            userDeviceIds);
                    data.add(device);
                }
            }

            result.setAccessToken(accessToken);
            result.setUserNickname(userNickname);
            //result.setResult("Y".equalsIgnoreCase(stringObject) ? ApiResponse.ResponseType.HTTP_200 : ApiResponse.ResponseType.CUSTOM_1003);
            result.setDevice(data);
            return new ResponseEntity<>(result, HttpStatus.OK);
        }catch (CustomException e){
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public ResponseEntity<?> doRegist(MemberDTO params) throws CustomException {

        String stringObject = null;
        ApiResponse.Data data = new ApiResponse.Data();

        String hp = params.getHp();
        String userNickname = params.getUserNickname();
        String userId = params.getUserId();
        String userPassword = params.getUserPassword();

        try{

            userPassword = encoder.encode(userPassword);
            params.setUserPassword(userPassword);

            int result1 = memberMapper.insertAccount(params);
            int result2 = memberMapper.insertMember(params);

            if(result1 > 0 && result2 > 0) stringObject = "Y";
            else stringObject = "N";

//            data.setResult("Y".equalsIgnoreCase(stringObject)
//                    ? ApiResponse.ResponseType.HTTP_200
//                    : ApiResponse.ResponseType.CUSTOM_2002);

            return new ResponseEntity<>(data, HttpStatus.OK);
        }catch (CustomException e){
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public ResponseEntity<?> doDuplicationCheck(MemberDTO params) throws CustomException {

        String stringObject = null;
        ApiResponse.Data data = new ApiResponse.Data();
        String userId = params.getUserId();
        String msg = null;
        try{
            MemberDTO member = memberMapper.getUserByUserId(userId);

            if(member == null) stringObject = "N";
            else stringObject = "Y";

            if(stringObject.equals("Y")) msg = "ID 찾기 성공";
            else msg = "입력한 아이디와 일치하는 회원정보가 없습니다.";

            data.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200
                    : ApiResponse.ResponseType.CUSTOM_2002, msg);

            return new ResponseEntity<>(data, HttpStatus.OK);

        }catch (CustomException e){
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
}
