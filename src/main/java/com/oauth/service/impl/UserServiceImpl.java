package com.oauth.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oauth.constants.MobiusResponse;
import com.oauth.dto.AuthServerDTO;
import com.oauth.mapper.DeviceMapper;
import com.oauth.mapper.MemberMapper;
import com.oauth.message.GwMessagingSystem;
import com.oauth.response.ApiResponse;
import com.oauth.service.mapper.UserService;
import com.oauth.utils.Common;
import com.oauth.utils.CustomException;
import com.oauth.utils.JSON;
import com.oauth.utils.RedisCommand;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Member;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserServiceImpl implements UserService {
    @Autowired
    MobiusService mobiusService;
    @Autowired
    private MemberMapper memberMapper;
    @Autowired
    private DeviceMapper deviceMapper;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private Common common;
    @Autowired
    private RedisCommand redisCommand;
    @Autowired
    SqlSessionFactory sqlSessionFactory;
    @Autowired
    GwMessagingSystem gwMessagingSystem;
    @Value("${server.timeout}")
    private long TIME_OUT;
    @Value("#{${device.model.code}}")
    Map<String, String> modelCodeMap;

    /** 회원 로그인 */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public ResponseEntity<?> doLogin(String userId, String userPassword, String pushToken) throws CustomException {

        String userNickname = null;

        String stringObject;
        String password;
        ApiResponse.Data result = new ApiResponse.Data();

        // Device Set 생성
        Set<String> userDeviceIds = new HashSet<>();
        List<ApiResponse.Data.Device> data = new ArrayList<>();

        List<String> deviceId;
        List<String> controlAuthKey;
        List<String> deviceNickname;
        List<String> regSort;
        String msg;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            System.out.println(encoder.encode(userPassword));
            AuthServerDTO account = memberMapper.getAccountByUserId(userId);
            AuthServerDTO member = memberMapper.getUserByUserId(userId);

            password = account.getUserPassword();

            if(!encoder.matches(userPassword, password)){
                msg = "PW 에러";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            if(member != null) userNickname = member.getUserNickname();

            List<AuthServerDTO> deviceInfoList = memberMapper.getDeviceIdByUserId(userId);
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

                if(deviceId != null &&
                        controlAuthKey != null &&
                        deviceNickname != null &&
                        regSort != null){

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
            }

            result.setAccessToken(common.getTransactionId());
            result.setUserNickname(userNickname);
            result.setDevice(data);

            if(stringObject.equals("Y")) {
                conMap.put("body", "Login OK");
                msg = "로그인 성공";
            }
            else {
                conMap.put("body", "Login FAIL");
                msg = "로그인 실패";
            }

            conMap.put("targetToken", pushToken);
            conMap.put("title", "Login");
            conMap.put("id", "Login ID");
            conMap.put("isEnd", "false");

            String jsonString = objectMapper.writeValueAsString(conMap);
            System.out.println("jsonString: " + jsonString);
            mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);

            result.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);
            return new ResponseEntity<>(result, HttpStatus.OK);
        }catch (Exception e){
            System.out.println(e.getMessage());
            log.error("", e);
        }
        return null;
    }

    /** 회원가입 */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public ResponseEntity<?> doRegist(AuthServerDTO params) throws CustomException {

        ResponseEntity<?> result = null;

        // Transaction용 클래스 선언
        SqlSession session = sqlSessionFactory.openSession();

        ApiResponse.Data data = new ApiResponse.Data();
        String stringObject;

        String userPassword = params.getUserPassword();
        String msg;
        int result1;
        int result2;
        MemberMapper mMapper = session.getMapper(MemberMapper.class);
        try {

            userPassword = encoder.encode(userPassword);
            params.setUserPassword(userPassword);

            result1 = mMapper.insertAccount(params);
            result2 = mMapper.insertMember(params);

            if (result1 > 0 && result2 > 0) stringObject = "Y";
            else stringObject = "N";

            if (stringObject.equals("Y")) msg = "회원가입 성공";
            else msg = "회원가입 실패";

            data.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200
                    : ApiResponse.ResponseType.CUSTOM_2002, msg);

            result = new ResponseEntity<>(data, HttpStatus.OK);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            log.error("", e);
        }
        return result;
    }

    /** 회원중복 체크 */
    @Override
    public ResponseEntity<?> doDuplicationCheck(AuthServerDTO params) throws CustomException {

        ApiResponse.Data data = new ApiResponse.Data();
        String stringObject;
        String userId = params.getUserId();
        String msg;

        try{
            AuthServerDTO member = memberMapper.getUserByUserId(userId);

            if(member == null) stringObject = "N";
            else stringObject = "Y";

            if(stringObject.equals("Y")) msg = "중복 되는 ID";
            else msg = "중복 되지 않는 ID";

            data.setDuplicationYn(stringObject);
            data.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200
                    : ApiResponse.ResponseType.CUSTOM_1001, msg);

            return new ResponseEntity<>(data, HttpStatus.OK);
        }catch (Exception e){
            System.out.println(e.getMessage());
            log.error("", e);
        }
        return null;
    }

    /** ID 찾기 */
    @Override
    public ResponseEntity<?> doIdFind(AuthServerDTO params) throws CustomException {

        ApiResponse.Data data = new ApiResponse.Data();
        String stringObject;
        String userHp = params.getHp();
        String modelCode = params.getModelCode();
        String deviceId = params.getDeviceId();
        String msg;
        List<AuthServerDTO> member = null;
        List<String> userId = null;
        try {
            System.out.println("userHp: " + userHp);
            System.out.println("modelCode: " + modelCode);
            System.out.println("deviceId: " + deviceId);
            System.out.println("modelCodeMap: " + modelCodeMap);
            // 구형 모델의 경우
            if(modelCode.equals(modelCodeMap.get("oldModel"))) member = memberMapper.getUserByHp(userHp);
            else if(modelCode.equals(modelCodeMap.get("newModel"))) member = memberMapper.getUserByDeviceId(deviceId);

            if(member.isEmpty()) stringObject = "N";
            else {
                stringObject = "Y";
                userId = Common.extractJson(member.toString(), "userId");
            }
            
            if(stringObject.equals("Y")) msg = "ID 찾기 성공";
            else msg = "입력한 아이디와 일치하는 회원정보가 없습니다.";

            data.setUserIdList(userId);
            data.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200
                    : ApiResponse.ResponseType.CUSTOM_2002, msg);
            return new ResponseEntity<>(data, HttpStatus.OK);

        }catch (Exception e){
            System.out.println(e.getMessage());
            log.error("", e);
        }
        return null;
    }

    /** 비밀번호 찾기 - 초기화 */
    @Override
    public ResponseEntity<?> doResetPassword(AuthServerDTO params) throws CustomException {

        ApiResponse.Data data = new ApiResponse.Data();
        String stringObject;
        String modelCode = params.getModelCode();
        AuthServerDTO member;
        String msg;
        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        try {

            // 구형 모델의 경우
            if(modelCode.equals(modelCodeMap.get("oldModel")) || modelCode.equals(modelCodeMap.get("newModel")))
                member = memberMapper.getUserByUserIdAndHp(params);
            else
                member = memberMapper.getUserByUserIdAndHpAndDeviceId(params);

            if(member == null) stringObject = "N";
            else stringObject = "Y";

            if(stringObject.equals("Y")) {
                conMap.put("body", "Reset Password OK");
                msg = "비밀번호 찾기 - 초기화 성공";
            } else{
                conMap.put("body", "Reset Password FAIL");
                msg = "비밀번호 찾기 - 초기화 실패";
            }

            conMap.put("targetToken", params.getPushToken());
            conMap.put("title", "Reset Password");
            conMap.put("id", "Reset Password ID");
            conMap.put("isEnd", "false");

            String jsonString = objectMapper.writeValueAsString(conMap);
            System.out.println("jsonString: " + jsonString);
            mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);

            data.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200
                    : ApiResponse.ResponseType.CUSTOM_2002, msg);
            return new ResponseEntity<>(data, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            throw new RuntimeException(e);
        }
    }

    /** 비밀번호 변경 - 생성 */
    @Override
    public ResponseEntity<?> doChangePassword(AuthServerDTO params) throws CustomException {

        ApiResponse.Data data = new ApiResponse.Data();
        String stringObject;
        String msg;
        int result;
        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        try{

            Common.updateMemberDTOList(params, "userPassword", encoder.encode(params.getUserPassword()));
            result = memberMapper.updatePassword(params);

            if(result > 0) stringObject = "Y";
            else stringObject = "N";

            if(stringObject.equals("Y")) {
                conMap.put("body", "Change Password OK");
                msg = "비밀번호 변경 - 생성 성공";
            } else{
                conMap.put("body", "Change Password FAIL");
                msg = "비밀번호 변경 - 생성 실패";
            }

            conMap.put("targetToken", params.getPushToken());
            conMap.put("title", "Change Password");
            conMap.put("id", "Change Password ID");
            conMap.put("isEnd", "false");

            String jsonString = objectMapper.writeValueAsString(conMap);
            System.out.println("jsonString: " + jsonString);
            mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);

            data.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200
                    : ApiResponse.ResponseType.CUSTOM_2002, msg);

            return new ResponseEntity<>(data, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            throw new RuntimeException(e);
        }
    }

    /** 사용자정보 조회 */
    @Override
    public ResponseEntity<?> doSearch(AuthServerDTO params) throws CustomException {
        String stringObject;
        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        String userId = params.getUserId();

        try{
            AuthServerDTO member = memberMapper.getUserByUserId(userId);

            if (member != null) {
                stringObject = "Y";
                data.setUserId(member.getUserId());
                data.setUserNickname(member.getUserNickname());
                data.setHp(member.getHp());
            } else stringObject = "N";

            if(stringObject.equals("Y")) msg = "사용자정보 조회 성공";
            else msg = "사용자정보 조회 실패";

            data.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200
                    : ApiResponse.ResponseType.CUSTOM_2002, msg);
            return new ResponseEntity<>(data, HttpStatus.OK);

        }catch (Exception e){
            System.out.println(e.getMessage());
            log.error("", e);
        }
        return null;
    }

    /** 회원 별칭(이름) 및 전화번호 변경 */
    @Override
    public ResponseEntity<?> doUpdateUserNicknameHp(AuthServerDTO params) throws CustomException{

        ApiResponse.Data data = new ApiResponse.Data();
        String stringObject = null;
        String msg;
        String userPassword = params.getUserPassword();
        String oldHp = params.getOldHp();
        String newHp = params.getNewHp();
        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();

        try{

            AuthServerDTO pwCheck = memberMapper.passwordCheck(userPassword);

            if(pwCheck == null) stringObject = "N";
            else {
                if(oldHp.equals(newHp)) stringObject = "M";
                else if(newHp == null) {
                    params.setNewHp(oldHp);
                    int result = memberMapper.updateUserNicknameAndHp(params);
                    if(result > 0) stringObject = "Y";
                    else stringObject = "N";
                }
            }

            if(stringObject.equals("Y")) {
                conMap.put("body", "Update Nickname and PhoneNum OK");
                msg = "회원 별칭(이름) 및 전화번호 변경 성공";
            }
            else if(stringObject.equals("M")) msg = "동일한 전화번호 변경 오류";
            else {
                conMap.put("body", "Update Nickname and PhoneNum FAIL");
                msg = "회원 별칭(이름) 및 전화번호 변경 실패";
            }


            conMap.put("targetToken", params.getPushToken());
            conMap.put("title", "Update Nickname and PhoneNum");
            conMap.put("id", "Update Nickname and PhoneNum ID");
            conMap.put("isEnd", "false");

            String jsonString = objectMapper.writeValueAsString(conMap);
            System.out.println("jsonString: " + jsonString);
            mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);

            data.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200
                    : ApiResponse.ResponseType.CUSTOM_2002, msg);

            return new ResponseEntity<>(data, HttpStatus.OK);
        }catch (Exception e){
            System.out.println(e.getMessage());
            log.error("", e);
        }
        return null;
    }

    /** 비밀번호 변경 - 로그인시 */
    @Override
    public ResponseEntity<?> doUpdatePassword(AuthServerDTO params) throws CustomException{

        ApiResponse.Data data = new ApiResponse.Data();
        String stringObject;
        String oldPassword = params.getOldPassword();
        String userId = params.getUserId();

        String msg;
        int pwChangeResult;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();

        try{
            AuthServerDTO account = memberMapper.getAccountByUserId(userId);
            params.setNewPassword(encoder.encode(params.getNewPassword()));

            if(!encoder.matches(oldPassword, account.getUserPassword())){
                msg = "PW 에러";
                data.setResult(ApiResponse.ResponseType.CUSTOM_2002, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            pwChangeResult = memberMapper.updatePassword(params);
            if(pwChangeResult > 0)  stringObject = "Y";
            else stringObject = "N";

            if(stringObject.equals("Y")) {
                conMap.put("body", "Update Password OK");
                msg = "비밀번호 변경 - 로그인시 성공";
            } else{
                conMap.put("body", "Update Password FAIL");
                msg = "비밀번호 변경 - 로그인시 실패";
            }

            data.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200
                    : ApiResponse.ResponseType.CUSTOM_2002, msg);

            conMap.put("targetToken", params.getPushToken());
            conMap.put("title", "Update Password");
            conMap.put("id", "Update Password ID");
            conMap.put("isEnd", "false");

            String jsonString = objectMapper.writeValueAsString(conMap);
            System.out.println("jsonString: " + jsonString);
            mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);

            return new ResponseEntity<>(data, HttpStatus.OK);
        }catch (Exception e) {
            log.error("", e);
            throw new RuntimeException(e);
        }
    }

    /** 사용자(세대원) 정보 조회 */
    @Override
    public ResponseEntity<?> doViewHouseholdMemebers(AuthServerDTO params) throws CustomException{

        String stringObject;
        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        String userId = params.getUserId();
        try {

            // Device Set 생성
            Set<String> userIds = new HashSet<>();
            List<ApiResponse.Data.User> user = new ArrayList<>();

            List<AuthServerDTO> deviceIds = memberMapper.getDeviceIdByUserId(userId);
            if(!deviceIds.isEmpty()){
                stringObject = "Y";
                List<AuthServerDTO> members = memberMapper.getHouseMembersByUserId(deviceIds);
                System.out.println("members: " + members.size());
                List<AuthServerDTO> memberStream = Common.deduplication(members, AuthServerDTO::getUserId);

                List<String> userIdList = Common.extractJson(memberStream.toString(), "userId");
                List<String> userNicknameList = Common.extractJson(memberStream.toString(), "userNickname");
                List<String> householderdList = Common.extractJson(memberStream.toString(), "householder");

                // Mapper실행 후 사용자가 가지고 있는 Member 개수
                int numMembers = memberStream.size();

                if(userIdList != null && userNicknameList != null && householderdList != null){
                    // Member 추가
                    for (int i = 0; i < numMembers; i++) {
                        ApiResponse.Data.User users = Common.createUsers(
                                userIdList.get(i),
                                userNicknameList.get(i),
                                householderdList.get(i),
                                userIds);
                        user.add(users);
                    }
                }
            } else stringObject = "N";

            if(stringObject.equals("Y")) msg = "사용자(세대원) 정보 조회 성공";
            else msg = "사용자(세대원) 정보 조회 실패";

            data.setUser(user);
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

    /** 사용자 추가 - 초대 */
    @Override
    public ResponseEntity<?> doAddUser(AuthServerDTO params)
            throws CustomException{

        String stringObject;
        ApiResponse.Data data = new ApiResponse.Data();
        String msg = null;
        int result;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        try {

            result = memberMapper.inviteHouseMember(params);

            if(result <= 0) stringObject = "N";
            else stringObject = "Y";

            if(stringObject.equals("Y")) {
                conMap.put("body", "Add User OK");
                msg = "사용자 추가 - 초대 성공";
            }
            else {
                conMap.put("body", "Add User FAIL");
                msg = "사용자 추가 - 초대 실패";
            }

            conMap.put("targetToken", params.getPushToken());
            conMap.put("title", "Add User");
            conMap.put("id", "Add User ID");
            conMap.put("isEnd", "false");

            String jsonString = objectMapper.writeValueAsString(conMap);
            System.out.println("jsonString: " + jsonString);
            mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);

            data.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200
                    : ApiResponse.ResponseType.CUSTOM_2002, msg);

            return new ResponseEntity<>(data, HttpStatus.OK);
        }catch (Exception e){
            System.out.println(e.getMessage());
            log.error("", e);
        }
        return null;
    }

    /** 사용자 초대 - 수락여부 */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public ResponseEntity<?> doInviteStatus(AuthServerDTO params)
            throws CustomException{

        // Transaction용 클래스 선언
        SqlSession session = sqlSessionFactory.openSession();

        String stringObject;
        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        String requestUserId = params.getRequestUserId();
        String responseUserId = params.getResponseUserId();
        String inviteAcceptYn = params.getInviteAcceptYn();
        MemberMapper mMapper = session.getMapper(MemberMapper.class);
        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        try {

            /**
             * 수락시: responseUserId 사용자는 requestUserId 사용자가 가진 DeviceId에 자동으로 mapping된다.
             * 거부시: TBR_OPR_USER_INVITE_STATUS 수락여부 항목 N UPDATE
             * 수락시 쿼리 흐름:
             * 1. TBR_OPR_USER_INVITE_STATUS
             * 2. TBR_OPR_USER_DEVICE에서 requestUserId 검색
             * 3. 2번 출력값으로 TBR_OPR_USER_DEVICE에 responseUserId INSERT
             * */

            List<AuthServerDTO> member;
            int insertNewHouseMemberResult;
            int acceptInviteResult;
            if(inviteAcceptYn.equals("Y")){

                acceptInviteResult = mMapper.acceptInvite(params);

                member = mMapper.getDeviceIdByUserId(requestUserId);

                Common.updateMemberDTOList(member, "responseUserId", responseUserId);
                Common.updateMemberDTOList(member, "householder", "N");

                System.out.println(member);
                insertNewHouseMemberResult = mMapper.insertNewHouseMember(member);

                if(insertNewHouseMemberResult > 0 && acceptInviteResult > 0) stringObject = "Y";
                else stringObject = "N";

            } else if(inviteAcceptYn.equals("N")){

                acceptInviteResult = mMapper.acceptInvite(params);
                if(acceptInviteResult > 0) stringObject = "Y";
                else stringObject = "N";

            } else {
                msg = "예기치 못한 오류로 인해 서버에 연결할 수 없습니다";
                data.setResult(ApiResponse.ResponseType.HTTP_500, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            if(stringObject.equals("Y")) {
                conMap.put("body", "Accept Invite OK");
                msg = "사용자 초대 - 수락여부 성공";
            }
            else {
                conMap.put("body", "Accept Invite FAIL");
                msg = "사용자 초대 - 수락여부 실패";
            }

            conMap.put("targetToken", params.getPushToken());
            conMap.put("title", "Accept Invite");
            conMap.put("id", "Accept Invite ID");
            conMap.put("isEnd", "false");

            String jsonString = objectMapper.writeValueAsString(conMap);
            System.out.println("jsonString: " + jsonString);
            mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);

            data.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200
                    : ApiResponse.ResponseType.CUSTOM_2002, msg);

            return new ResponseEntity<>(data, HttpStatus.OK);
        }catch (Exception e){
            log.error("", e);
        }
        return null;
    }

    /** 사용자 초대 - 목록 조회 */
    @Override
    public ResponseEntity<?> doInviteListView(AuthServerDTO params)
            throws CustomException{

        String stringObject;
        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        try {

            String userId = params.getUserId();

            List<AuthServerDTO> invitationInfo = memberMapper.getInvitationList(userId);

            if(invitationInfo.isEmpty()) stringObject = "N";
            else stringObject = "Y";

            // Device Set 생성
            Set<String> invitationIds = new HashSet<>();
            List<ApiResponse.Data.Invitation> inv = new ArrayList<>();

            List<String> invitationIdxList = Common.extractJson(invitationInfo.toString(), "invitationIdx");
            List<String> inviteAcceptYnList = Common.extractJson(invitationInfo.toString(), "inviteAcceptYn");
            List<String> requestUserIdList = Common.extractJson(invitationInfo.toString(), "requestUserId");
            List<String> requestUserNickList = Common.extractJson(invitationInfo.toString(), "requestUserNick");
            List<String> responseUserIdList = Common.extractJson(invitationInfo.toString(), "responseUserId");
            List<String> responseUserNickList = Common.extractJson(invitationInfo.toString(), "responseUserNick");
            List<String> responseHpList = Common.extractJson(invitationInfo.toString(), "responseHp");
            List<String> inviteStartDateList = Common.extractJson(invitationInfo.toString(), "inviteStartDate");
            List<String> inviteEndDateList = Common.extractJson(invitationInfo.toString(), "inviteEndDate");

            // Mapper실행 후 사용자가 가지고 있는 Invitation 개수
            int numInvitations = invitationInfo.size();

            if(invitationIdxList != null
                    && inviteAcceptYnList != null
                    && requestUserIdList != null
                    && requestUserNickList != null
                    && responseUserIdList != null
                    && responseUserNickList != null
                    && responseHpList != null
                    && inviteStartDateList != null
                    && inviteEndDateList != null
            ){
                // Member 추가
                for (int i = 0; i < numInvitations; i++) {
                    ApiResponse.Data.Invitation invitations = Common.createInvitations(
                            invitationIdxList.get(i),
                            inviteAcceptYnList.get(i),
                            requestUserIdList.get(i),
                            requestUserNickList.get(i),
                            responseUserIdList.get(i),
                            responseUserNickList.get(i),
                            responseHpList.get(i),
                            inviteStartDateList.get(i),
                            inviteEndDateList.get(i),
                            invitationIds);
                    inv.add(invitations);
                }
            }

            if(stringObject.equals("Y")) msg = "사용자 초대 - 목록 조회 성공";
            else msg = "사용자 초대 - 목록 조회 실패";

            data.setResult("Y".equalsIgnoreCase(stringObject) ?
                    ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);
            data.setInvitation(inv);
            return new ResponseEntity<>(data, HttpStatus.OK);

        }catch (CustomException e){
            e.printStackTrace();
        }
        return null;
    }

    /** 사용자(세대원) - 강제탈퇴 */
    @Override
    public ResponseEntity<?> doDelHouseholdMembers(AuthServerDTO params)
            throws CustomException{

        String stringObject;
        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        String userId = params.getUserId();
        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            int result = memberMapper.delHouseMember(userId);

            if(result > 0) stringObject = "Y";
            else stringObject = "N";

            if(stringObject.equals("Y")) {
                conMap.put("body", "Force Delete Member OK");
                msg = "사용자(세대원) - 강제탈퇴 성공";
            }
            else {
                conMap.put("body", "Force Delete Member FAIL");
                msg = "사용자(세대원) - 강제탈퇴 실패";
            }

            conMap.put("targetToken", params.getPushToken());
            conMap.put("title", "Force Delete Member");
            conMap.put("id", "Force Delete Member ID");
            conMap.put("isEnd", "false");

            String jsonString = objectMapper.writeValueAsString(conMap);
            System.out.println("jsonString: " + jsonString);
            mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);

            data.setResult("Y".equalsIgnoreCase(stringObject) ?
                    ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);
            return new ResponseEntity<>(data, HttpStatus.OK);
        }catch (Exception e){
            log.error("", e);
        }
        return null;
    }

    /** 홈 IoT 컨트롤러 알림 설정 */
    @Override
    public ResponseEntity<?> doPushSet(AuthServerDTO params, HashMap<String, String> controlMap)
            throws CustomException{

        ApiResponse.Data data = new ApiResponse.Data();
        String stringObject;
        String msg ;
        String userId = params.getUserId();
        String deviceId = params.getDeviceId();
        List<HashMap<String, String>> memberList = new ArrayList<>();
        HashMap<String, String> memberMap = new HashMap<String, String>();

            try{
                memberMap.put("fPushYn", controlMap.get("01"));
                memberMap.put("sPshYn", controlMap.get("02"));
                memberMap.put("tPushYn", controlMap.get("03"));
                memberMap.put("userId", userId);
                memberMap.put("deviceId", deviceId);

                memberList.add(memberMap);
                int result = memberMapper.updatePushCodeStatus(memberList);

                if(result > 0) stringObject = "Y";
                else stringObject = "N";

                if(stringObject.equals("Y")) msg = "홈 IoT 컨트롤러 알림 설정 성공";
                else msg = "홈 IoT 컨트롤러 알림 설정 실패";

                data.setResult("Y".equalsIgnoreCase(stringObject) ?
                        ApiResponse.ResponseType.HTTP_200 :
                        ApiResponse.ResponseType.CUSTOM_1003, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);

        }catch (CustomException e){
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /** 홈 IoT 컨트롤러 알림 정보 조회 */
    // TODO: Return Entity나 에러 구문 추가하기
    @Override
    public HashMap<String, Object> doSearchPushSet(AuthServerDTO params)
            throws CustomException{

        String stringObject;
        ApiResponse.Data result = new ApiResponse.Data();
        String msg = null;

        HashMap<String, Object> resultMap = new LinkedHashMap<String, Object>();

        // "push" 부분을 표현하는 List 생성
        List<Map<String, String>> pushList = new ArrayList<>();
        try{

            resultMap.put("resultCode", "200");
            resultMap.put("resultMsg", "로그인 성공");

            AuthServerDTO pushCodeInfo = memberMapper.getPushCodeStatus(params);

            // 각각의 "pushCd"와 "pushYn"을 가지는 Map을 생성하여 리스트에 추가
            Map<String, String> push1 = new LinkedHashMap<>();
            push1.put("pushCd", "01");
            push1.put("pushYn", pushCodeInfo.getFPushYn());
            pushList.add(push1);

            Map<String, String> push2 = new LinkedHashMap<>();
            push2.put("pushCd", "02");
            push2.put("pushYn", pushCodeInfo.getSPushYn());
            pushList.add(push2);

            Map<String, String> push3 = new LinkedHashMap<>();
            push3.put("pushCd", "03");
            push3.put("pushYn", pushCodeInfo.getTPushYn());
            pushList.add(push3);

            resultMap.put("push", pushList);

            return resultMap;
        }catch (CustomException e){
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /** 사용자(세대주) 탈퇴 */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public ResponseEntity<?> doDelHouseholder(AuthServerDTO params)
            throws CustomException{

        String stringObject;
        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        String nextHouseholdId = null;
        int result = 0;
        int result1 = 0;
        int result2 = 0;

        // Transaction용 클래스 선언
        SqlSession session = sqlSessionFactory.openSession();
        MemberMapper mMapper = session.getMapper(MemberMapper.class);

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        try {

                List<AuthServerDTO> deviceIds = mMapper.getDeviceIdByUserId(params.getUserId());
                List<AuthServerDTO> members = mMapper.getHouseMembersByUserId(deviceIds);

                if(deviceIds != null && members != null){
                    stringObject = "Y";

                    // List에서 요청자 userId 제거
                    members.stream()
                            .filter(x -> x.getUserId()
                                    .equals(params.getUserId()))
                            .collect(Collectors.toList())
                            .forEach(members::remove);

                    List<String> userIdList = Common.extractJson(members.toString(), "userId");

                    if(userIdList != null) nextHouseholdId = userIdList.get(0);

                    result = mMapper.delHouseMember(params.getUserId());
                    if(result <= 0) stringObject = "N";

                    result1 = mMapper.updateHouseholdTbrOprUser(nextHouseholdId);
                    result2 = mMapper.updateHouseholdTbrOprUserDevice(nextHouseholdId);

                } else stringObject = "N";
                
                if(stringObject.equals("Y")) {
                    conMap.put("body", "Delete householder OK");
                    msg = "사용자(세대주) 탈퇴  성공";
                }
                else {
                    conMap.put("body", "Delete householder OK");
                    msg = "사용자(세대주) 탈퇴  실패";
                }

                conMap.put("targetToken", params.getPushToken());
                conMap.put("title", "Delete householder");
                conMap.put("id", "Delete householder ID");
                conMap.put("isEnd", "false");

                String jsonString = objectMapper.writeValueAsString(conMap);
                System.out.println("jsonString: " + jsonString);
                mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);

                data.setResult("Y".equalsIgnoreCase(stringObject) ?
                        ApiResponse.ResponseType.HTTP_200 :
                        ApiResponse.ResponseType.CUSTOM_1003, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);

            } catch (Exception e){
                log.error("", e);
            }
        return null;
    }

    /** 홈IoT 서비스 회원 탈퇴 */
    @Override
    public ResponseEntity<?> doWirhdrawal(AuthServerDTO params)
            throws CustomException{

        String stringObject;
        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        String userId = params.getUserId();
        String member;
        try {

            /**
             * 홈IoT 서비스 탈퇴 시 삭제 Table
             * 1. TBR_OPR_ACCOUNT
             * 2. TBR_OPR_USER
             * 3. TBR_OPR_USER_DEVICE
             * 4. TBT_OPR_DEVICE_REGIST
             * 프로시져 사용 (deleteUserFromService)
             * */
            member = memberMapper.deleteMemberFromService(userId);
            if(member.isEmpty()) stringObject = "N";
            else stringObject = "Y";

            if(stringObject.equals("Y")) msg = "홈IoT 서비스 회원 탈퇴 성공";
            else msg = "홈IoT 서비스 회원 탈퇴 실패";

            data.setResult("Y".equalsIgnoreCase(stringObject) ?
                    ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);

            return new ResponseEntity<>(data, HttpStatus.OK);

        }catch (CustomException e){
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /** 홈 IoT 컨트롤러 인증 */
    @Override
    public ResponseEntity<?> doDeviceAuthCheck(AuthServerDTO params)
            throws CustomException{

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject;
        String msg;

        String userId = params.getUserId();

        List<AuthServerDTO> deviceIdAndAuthKey;
        List<AuthServerDTO> deviceAuthCheck;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        try{
            deviceIdAndAuthKey = deviceMapper.getDeviceAuthCheckValuesByUserId(userId);
            if(deviceIdAndAuthKey.isEmpty()){
                stringObject = "N";
            }else {
                deviceAuthCheck = deviceMapper.deviceAuthCheck(deviceIdAndAuthKey);
                if(deviceAuthCheck.isEmpty()) {
                    stringObject = "N";
                } else {
                    stringObject = "Y";
                }
            }
            if(stringObject.equals("Y")) {
                conMap.put("body", "Device Auth Check OK");
                msg = "홈 IoT 컨트롤러 인증 성공";
            }
            else {
                conMap.put("body", "Device Auth Check FAIL");
                msg = "홈 IoT 컨트롤러 인증 실패";
            }

            result.setResult("Y".equalsIgnoreCase(stringObject) ?
                    ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);

            conMap.put("targetToken", params.getPushToken());
            conMap.put("title", "Device Auth Check");
            conMap.put("id", "Device Auth Check ID");
            conMap.put("isEnd", "false");

            String jsonString = objectMapper.writeValueAsString(conMap);
            System.out.println("jsonString: " + jsonString);
            mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);

            return new ResponseEntity<>(result, HttpStatus.OK);
        }catch (Exception e){
            System.out.println(e.getMessage());
            log.error("", e);
        }
        return null;
    }

    // TODO: 푸시서버 AE, CNT 생성 관련 논의 필요
    /** 홈 IoT 최초 등록 인증 */
    @Override
    public ResponseEntity<?> doFirstDeviceAuthCheck(AuthServerDTO params)
            throws CustomException{

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject;
        String msg;

        MobiusResponse aeResult1;
        MobiusResponse aeResult2;
        MobiusResponse cntResult1;
        MobiusResponse cntResult2;
        MobiusResponse cinResult;
        MobiusResponse subResult;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();

        try{

            /*
            * TODO:
            *  1. App에서 요청 후 생성한 TempKey와 요청값에 보낸 TempKey가 있는지 확인
            *  2. Redis에서 TempKey가 검색될 경우 유효한 요청으로 판단
            *  3. 유효한 요청일 경우 DeviceId 생성 후 Return
            *  4. DeviceId: 0.2.481.1.1.ModelCode.SerialNumber
            * */

            String redisValue = redisCommand.getValues(params.getTmpRegistKey());

            if(!redisValue.isEmpty()) stringObject = "Y";
            else stringObject = "N";



            aeResult1 = mobiusService.createAe(params.getSerialNumber());
            aeResult2 = mobiusService.createAe("ToPushServer");

            cntResult1 = mobiusService.createCnt(params.getSerialNumber(), params.getUserId());
            cntResult2 = mobiusService.createCnt("ToPushServer","ToPushServerCnt");

            subResult = mobiusService.createSub(params.getSerialNumber(), params.getUserId(), "gw");

            if(aeResult1.getResponseCode().equals("201") &&
                    aeResult2.getResponseCode().equals("201") &&
                    cntResult1.getResponseCode().equals("201") &&
                    cntResult2.getResponseCode().equals("201") &&
                    subResult.getResponseCode().equals("201")){
                stringObject = "Y";
            } else stringObject = "N";

            if(stringObject.equals("Y")){
                result.setDeviceId("0.2.481.1.1." + params.getModelCode() + "." + params.getSerialNumber());
                conMap.put("body", "First Device Auth Check OK");
                msg = "최초 인증 성공";
            } else {
                conMap.put("body", "First Device Auth Check FAIL");
                msg = "최초 인증 실패";
            }

            conMap.put("targetToken", params.getPushToken());
            conMap.put("title", "First Device Auth Check");
            conMap.put("id", "First Device Auth Check ID");
            conMap.put("isEnd", "false");

            String jsonString = objectMapper.writeValueAsString(conMap);

            cinResult = mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);

            if(!cinResult.getResponseCode().equals("201")) {
                msg = "PUSH 메세지 전송 오류";
            }

            result.setResult("Y".equalsIgnoreCase(stringObject) ?
                    ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);

            return new ResponseEntity<>(result, HttpStatus.OK);
        }catch (Exception e){
            log.error("", e);
        }
        return null;
    }

    /** API인증키 갱신 */
    @Override
    public ResponseEntity<?> doAccessTokenRenewal(AuthServerDTO params)
            throws CustomException{

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject = "Y";
        String msg = null;

        String inputPassword = params.getUserPassword();
        String userId = params.getUserId();
        String newAccessToken = common.getTransactionId();
        System.out.println("newAccessToken: " + newAccessToken);
        try{

            AuthServerDTO dbPassword = memberMapper.passwordCheck(inputPassword);

            if(inputPassword.equals(dbPassword.getUserPassword()) && encoder.matches(inputPassword, dbPassword.getUserPassword())){
                stringObject = "Y";
                redisCommand.setValues(userId, newAccessToken, Duration.ofMinutes(30));
            } else stringObject = "N";

            if(stringObject.equals("Y")) msg = "API인증키 갱신 성공";
            else msg = "API인증키 갱신 실패";

            result.setAccessToken(newAccessToken);
            result.setResult("Y".equalsIgnoreCase(stringObject) ?
                    ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);

            return new ResponseEntity<>(result, HttpStatus.OK);

        }catch (CustomException e){
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /** 홈 IoT 컨트롤러 삭제(회원 매핑 삭제) */
    @Override
    public ResponseEntity<?> doUserDeviceDelete(AuthServerDTO params)
            throws CustomException {

        String stringObject;
        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        String member;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        try{
            /**
             * TBT_OPR_DEVICE_REGIST - 임시 단말 등록 정보
             * TBR_OPR_USER_DEVICE - 사용자 단말 정보
             * TBR_OPR_DEVICE_DETAIL - 단말정보상세
             * 프로시져
             * */
            member = memberMapper.deleteControllerMapping(params);
            if(member.isEmpty()) stringObject = "N";
            else stringObject = "Y";

            if(stringObject.equals("Y")) {
                conMap.put("body", "User Device Delete OK");
                msg = "홈 IoT 컨트롤러 삭제(회원 매핑 삭제) 성공";
            }
            else {
                conMap.put("body", "User Device Delete OK");
                msg = "홈 IoT 컨트롤러 삭제(회원 매핑 삭제) 실패";
            }

            conMap.put("targetToken", params.getPushToken());
            conMap.put("title", "User Device Delete");
            conMap.put("id", "User Device Delete ID");
            conMap.put("isEnd", "false");

            String jsonString = objectMapper.writeValueAsString(conMap);
            System.out.println("jsonString: " + jsonString);
            mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);

            data.setResult("Y".equalsIgnoreCase(stringObject) ?
                    ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);

            return new ResponseEntity<>(data, HttpStatus.OK);
        }catch (Exception e){
            log.error("", e);
        }
        return null;
    }

    /** 스마트알림 - PUSH 이력 조회 */
    @Override
    public ResponseEntity<?> doViewPushHistory(AuthServerDTO params)
            throws CustomException{

        ApiResponse.Data data = new ApiResponse.Data();
        ApiResponse.Data.PushInfo pushInfo = new ApiResponse.Data.PushInfo();

        String stringObject = null;
        String msg = null;
        String userId = params.getUserId();
        List<AuthServerDTO> member = null;
        try{

            member = memberMapper.getPushInfoList(userId);

            if(member == null) stringObject = "N";
            else {
                stringObject = "Y";

                // Set 생성
                Set<String> pushSet = new HashSet<>();
                List<ApiResponse.Data.PushInfo> pushInfoArray = new ArrayList<>();

                List<String> pushIdxList = Common.extractJson(member.toString(), "pushIdx");
                List<String> pushTitleList = Common.extractJson(member.toString(), "pushTitle");
                List<String> pushContentList = Common.extractJson(member.toString(), "pushContent");
                List<String> pushTypeList = Common.extractJson(member.toString(), "pushType");
                List<String> pushDatetimeList = Common.extractJson(member.toString(), "pushDatetime");

                int numPush = member.size();

                if(pushIdxList != null
                        && pushTitleList != null
                        && pushContentList != null
                        && pushTypeList != null
                        && pushDatetimeList != null
                ){
                    // Member 추가
                    for (int i = 0; i < numPush; i++) {
                        ApiResponse.Data.PushInfo pushes = Common.createPush(
                                pushIdxList.get(i),
                                pushTitleList.get(i),
                                pushContentList.get(i),
                                pushTypeList.get(i),
                                pushDatetimeList.get(i),
                                pushSet);
                        pushInfoArray.add(pushes);
                    }
                }

                data.setPushInfo(pushInfoArray);
            }

            if(stringObject.equals("Y")) msg = "스마트알림 - PUSH 이력 조회 성공";
            else msg = "스마트알림 - PUSH 이력 조회 실패";

            data.setResult("Y".equalsIgnoreCase(stringObject) ?
                    ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);
            return new ResponseEntity<>(data, HttpStatus.OK);
        }catch (CustomException e){
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /** 기기 별칭 수정 */
    @Override
    public ResponseEntity<?> doDeviceNicknameChange(AuthServerDTO params)
            throws CustomException{

        ApiResponse.Data data = new ApiResponse.Data();
        String stringObject;
        String msg;
        int result;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        try {

            result = deviceMapper.changeDeviceNickname(params);

            if(result <= 0) stringObject = "N";
            else stringObject = "Y";

            if(stringObject.equals("Y")) {
                conMap.put("body", "Device Nickname Change OK");
                msg = "기기 별칭 수정 성공";
            }
            else {
                conMap.put("body", "Device Nickname Change FAIL");
                msg = "기기 별칭 수정 실패";
            }

            conMap.put("targetToken", params.getPushToken());
            conMap.put("title", "Device Nickname Change");
            conMap.put("id", "Device Nickname Change ID");
            conMap.put("isEnd", "false");

            String jsonString = objectMapper.writeValueAsString(conMap);
            System.out.println("jsonString: " + jsonString);
            mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);

            data.setResult("Y".equalsIgnoreCase(stringObject) ?
                    ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);
            return new ResponseEntity<>(data, HttpStatus.OK);

        }catch (Exception e){
            log.error("", e);
        }
        return null;
    }

    /** 기기 밝기 조절 */
    @Override
    public ResponseEntity<?> doBrightnessControl(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject = null;
        String msg;
        AuthServerDTO serialNumber = null;
        String userId = params.getUserId();
        String uuId = common.getTransactionId();
        String redisValue;
        Map<String, Object> conMap = new HashMap<>();
        Map<String, Object> conMap1 = new HashMap<>();
        MobiusResponse mobiusResponse;
        ObjectMapper objectMapper = new ObjectMapper();
        String responseMessage;
        try{
            serialNumber = deviceMapper.getSerialNumberBydeviceId(params.getDeviceId());

            conMap.put("controlAuthKey", params.getControlAuthKey());
            conMap.put("deviceId", params.getDeviceId());
            conMap.put("deviceType", params.getDeviceType());
            conMap.put("modelCode", params.getModelCode());
            conMap.put("brightnessLevel", params.getBrightnessLevel());
            conMap.put("functionId", "blCf");

            redisValue = userId + "," + "blCf";
            redisCommand.setValues(uuId, redisValue);
            String jsonString = objectMapper.writeValueAsString(conMap);
            mobiusResponse = mobiusService.createCin("gwSever", "gwSeverCnt", jsonString);
            if(!mobiusResponse.getResponseCode().equals("201")){
                msg = "중계서버 오류";
                result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            try {
                // 메시징 시스템을 통해 응답 메시지 대기
                responseMessage = gwMessagingSystem.waitForResponse("blCf" + uuId, TIME_OUT, TimeUnit.SECONDS);
                if(responseMessage == null) stringObject = "T";
                else {
                    if(responseMessage.equals("\"200\"")) stringObject = "Y";
                    else stringObject = "N";
                    // 응답 처리
                    System.out.println("receiveCin에서의 응답: " + responseMessage);
                }

            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                e.printStackTrace();
            }

            if(stringObject.equals("Y")) {
                conMap1.put("body", "Brightness Control OK");
                msg = "기기 밝기 수정 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            }
            else if(stringObject.equals("N")) {
                conMap1.put("body", "Brightness Control OK");
                msg = "기기 밝기 수정 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }
            else {
                msg = "응답이 없거나 시간 초과";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
            }

            conMap1.put("targetToken", params.getPushToken());
            conMap1.put("title", "Reset Password");
            conMap1.put("id", "Reset Password ID");
            conMap1.put("isEnd", "false");

            String jsonString1 = objectMapper.writeValueAsString(conMap1);
            System.out.println("jsonString1: " + jsonString1);
            mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString1);

            redisCommand.deleteValues(uuId);
            return new ResponseEntity<>(result, HttpStatus.OK);
        }catch (CustomException e){
            System.out.println(e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    /** 공지사항 조회 */
    @Override
    public ResponseEntity<?> doNotice(AuthServerDTO params) throws CustomException {

        ApiResponse.Data data = new ApiResponse.Data();
        ApiResponse.Data.NoticeInfo noticeInfo = new ApiResponse.Data.NoticeInfo();

        String stringObject = null;
        String msg = null;
        List<AuthServerDTO> noticeList = null;
        try {

            noticeList = memberMapper.getNoticeList();
            if(noticeList.isEmpty()) stringObject = "N";
            else {
                stringObject = "Y";

                Set<String> noticeSet = new HashSet<>();
                List<ApiResponse.Data.NoticeInfo> noticeInfoArray = new ArrayList<>();

                List<String> noticeIdxList = Common.extractJson(noticeList.toString(), "noticeIdx");
                List<String> noticeTitle = Common.extractJson(noticeList.toString(), "noticeTitle");
                List<String> noticeContent = Common.extractJson(noticeList.toString(), "noticeContent");
                List<String> noticeType = Common.extractJson(noticeList.toString(), "noticeType");
                List<String> noticeStartDate = Common.extractJson(noticeList.toString(), "noticeStartDate");
                List<String> noticeEndDate = Common.extractJson(noticeList.toString(), "noticeEndDate");

                int numNotice = noticeList.size();
                if(noticeIdxList != null
                        && noticeTitle != null
                        && noticeContent != null
                        && noticeStartDate != null
                        && noticeEndDate != null
                        && noticeType != null
                ){
                    // Member 추가
                    for (int i = 0; i < numNotice; i++) {
                        ApiResponse.Data.NoticeInfo notices = Common.createNotice(
                                noticeIdxList.get(i),
                                noticeTitle.get(i),
                                noticeContent.get(i),
                                noticeType.get(i),
                                noticeStartDate.get(i),
                                noticeEndDate.get(i),
                                noticeSet);
                        noticeInfoArray.add(notices);
                    }
                }
                data.setNoticeInfo(noticeInfoArray);
            }

            if(stringObject.equals("Y")) msg = "공지사항 조회 성공";
            else msg = "공지사항 조회 실패";

            data.setResult("Y".equalsIgnoreCase(stringObject) ?
                    ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);
            return new ResponseEntity<>(data, HttpStatus.OK);

        }catch (CustomException e){
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /** 기기 설치 위치 별칭 수정 */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public ResponseEntity<?> doUpdateDeviceLocationNickname(AuthServerDTO params) throws CustomException {

        // Transaction용 클래스 선언
        SqlSession session = sqlSessionFactory.openSession();
        MemberMapper mMapper = session.getMapper(MemberMapper.class);
        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject = null;
        String msg = null;
        String userId = params.getUserId();
        int updateResult1 = 0;
        int updateResult2 = 0;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        try {

            updateResult1 = mMapper.updateDeviceLocationNicknameDeviceDetail(params);
            updateResult2 = mMapper.updateDeviceLocationNicknameDeviceRegist(params);

            if(updateResult1 > 0 && updateResult2 > 0) stringObject = "Y";
            else stringObject = "N";

            if(stringObject.equals("Y")) {
                conMap.put("body", "Update Device Location OK");
                msg = "기기 설치 위치 별칭 수정 성공";
            }
            else {
                conMap.put("body", "Update Device Location FAIL");
                msg = "기기 설치 위치 별칭 수정 실패";
            }

            result.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200
                    : ApiResponse.ResponseType.CUSTOM_2002, msg);

            conMap.put("targetToken", params.getPushToken());
            conMap.put("title", "Device Auth Check");
            conMap.put("id", "Device Auth Check ID");
            conMap.put("isEnd", "false");

            String jsonString = objectMapper.writeValueAsString(conMap);
            mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);

            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
        }
        return null;
    }

    /** 임시저장키 생성 */
    @Override
    public ResponseEntity<?> doGenerateTempKey(String userId) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject;
        String msg;
        String tmpRegistKey = null;
        AuthServerDTO userInfo;
        try {

            userInfo = memberMapper.getUserByUserId(userId);

            if(userInfo != null) {
                stringObject = "Y";
                tmpRegistKey = userId + "_" + common.getCurrentDateTime();
                redisCommand.setValues(tmpRegistKey, userId, Duration.ofMinutes(TIME_OUT));
            }
            else stringObject = "N";

            if(stringObject.equals("Y")) msg = "임시저장키 생성 성공";
            else msg = "임시저장키 생성 실패";

            result.setTmpRegistKey(tmpRegistKey);
            result.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200
                    : ApiResponse.ResponseType.CUSTOM_2002, msg);

            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
        }
        return null;
    }
}
