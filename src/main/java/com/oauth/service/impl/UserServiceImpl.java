package com.oauth.service.impl;

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
import com.oauth.utils.RedisCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(rollbackFor = {Exception.class, CustomException.class})
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
    GwMessagingSystem gwMessagingSystem;
    @Value("${server.timeout}")
    private long TIME_OUT;
    @Value("#{${device.model.code}}")
    Map<String, String> modelCodeMap;

    /** 회원 로그인 */
    @Override
    public ResponseEntity<?> doLogin(String userId, String userPassword, String pushToken) throws CustomException {

        String userNickname;
        String password;
        String registUserType;
        ApiResponse.Data result = new ApiResponse.Data();

        // Device Set 생성
        Set<String> userDeviceIds = new HashSet<>();
        List<ApiResponse.Data.Device> data = new ArrayList<>();

        List<String> deviceId;
        List<String> controlAuthKey;
        List<String> deviceNickname;
        List<String> regSort;
        List<String> tmpRegistKey;
        List<String> latitude;
        List<String> longitude;

        String msg;
        String token;
        String hp;

        try {

            AuthServerDTO account = memberMapper.getAccountByUserId(userId);
            if (account == null) {
                msg = "계정이 존재하지 않습니다.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else {
                registUserType = account.getRegistUserType();
                password = account.getUserPassword();
                if(!encoder.matches(userPassword, password)){
                    msg = "PW 에러";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }
            }

            AuthServerDTO member = memberMapper.getUserByUserId(userId);
            if (member == null) {
                msg = "계정이 존재하지 않습니다.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else userNickname = member.getUserNickname();

            List<AuthServerDTO> deviceInfoList = memberMapper.getDeviceIdByUserId(userId);
            if(deviceInfoList == null) {
                msg = "계정이 존재하지 않습니다.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else {

                deviceId = Common.extractJson(deviceInfoList.toString(), "deviceId");
                controlAuthKey = Common.extractJson(deviceInfoList.toString(), "controlAuthKey");
                deviceNickname = Common.extractJson(deviceInfoList.toString(), "deviceNickname");
                regSort = Common.extractJson(deviceInfoList.toString(), "regSort");
                tmpRegistKey = Common.extractJson(deviceInfoList.toString(), "tmpRegistKey");
                latitude = Common.extractJson(deviceInfoList.toString(), "latitude");
                longitude = Common.extractJson(deviceInfoList.toString(), "longitude");

                log.info("deviceId: " + deviceId);
                log.info("controlAuthKey: " + controlAuthKey);
                log.info("deviceNickname: " + deviceNickname);
                log.info("regSort: " + regSort);
                log.info("tmpRegistKey: " + tmpRegistKey);
                log.info("latitude: " + latitude);
                log.info("longitude: " + longitude);


                // Mapper실행 후 사용자가 가지고 있는 Device 개수
                int numDevices = deviceInfoList.size();

                if(deviceId != null &&
                        controlAuthKey != null &&
                        deviceNickname != null &&
                        regSort != null &&
                        tmpRegistKey != null &&
                        latitude != null &&
                        longitude != null){

                    // Device 추가
                    for (int i = 0; i < numDevices; i++) {
                        ApiResponse.Data.Device device = Common.createDevice(
                                deviceId.get(i),
                                controlAuthKey.get(i),
                                deviceNickname.get(i),
                                regSort.get(i),
                                tmpRegistKey.get(i),
                                latitude.get(i),
                                longitude.get(i),
                                userDeviceIds);
                        data.add(device);
                    }
                }
            }

            token = common.createJwtToken(userId, "NORMAL", "Login");
            log.info("Token: " + token);
            result.setRegistUserType(registUserType);
            result.setAccessToken(token);
            result.setUserNickname(userNickname);
            result.setDevice(data);

            msg = "로그인 성공";

            hp = memberMapper.getHpByUserId(userId).getHp();
            if (hp == null) {
                msg = "계정이 존재하지 않습니다.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else result.setHp(hp);

            AuthServerDTO params = new AuthServerDTO();

            params.setAccessToken(token);
            params.setUserId(userId);

            if(memberMapper.updateLoginDatetime(params) <= 0) {
                msg = "LOGIN_INFO_UPDATE_ERROR";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }

            result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            return new ResponseEntity<>(result, HttpStatus.OK);
        }catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.OK);
        }
    }

    /** 회원가입 */
    @Override
    public ResponseEntity<?> doRegist(AuthServerDTO params) throws CustomException {

        ResponseEntity<?> result = null;
        ApiResponse.Data data = new ApiResponse.Data();
        String userPassword = params.getUserPassword();
        String msg;
        String token;
        String userId = params.getUserId();

        try {

            userPassword = encoder.encode(userPassword);
            params.setUserPassword(userPassword);

            if(memberMapper.insertAccount(params) <= 0) {
                msg = "회원가입 실패";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }

            if(memberMapper.insertMember(params) <= 0) {
                msg = "회원가입 실패";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }

            msg = "회원가입 성공";

            token = common.createJwtToken(userId, "NORMAL", "Regist");
            log.info("Token: " + token);
            data.setAccessToken(token);
            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            return new ResponseEntity<>(data, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
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
            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);

            return new ResponseEntity<>(data, HttpStatus.OK);
        }catch (Exception e){
            System.out.println(e.getMessage());
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /** ID 찾기 */
    @Override
    public ResponseEntity<?> doIdFind(AuthServerDTO params) throws CustomException {

        ApiResponse.Data data = new ApiResponse.Data();
        String userHp = params.getHp();
        String modelCode = params.getModelCode();
        String deviceId = params.getDeviceId();
        String msg;
        List<AuthServerDTO> member = null;
        List<String> userId;
        try {

            // 구형 모델의 경우
            if(modelCode.equals(modelCodeMap.get("oldModel"))) member = memberMapper.getUserByHp(userHp);
            else if(modelCode.equals(modelCodeMap.get("newModel"))) member = memberMapper.getUserByDeviceId(deviceId);

            if (member == null) {
                msg = "일치하는 회원정보가 없습니다.";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            } else userId = Common.extractJson(member.toString(), "userId");

            msg = "ID 찾기 성공";

            data.setUserIdList(userId);
            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            return new ResponseEntity<>(data, HttpStatus.OK);
        }catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /** 비밀번호 찾기 - 초기화 */
    @Override
    public ResponseEntity<?> doResetPassword(AuthServerDTO params) throws CustomException {

        ApiResponse.Data data = new ApiResponse.Data();
        String stringObject;
        String modelCode = params.getModelCode();
        AuthServerDTO member;
        String msg;

        try {
            if(params.getDeviceId().equals("null")) params.setDeviceId(null);
            // 구형 모델의 경우
            if(modelCode.equals(modelCodeMap.get("oldModel")) || modelCode.equals(modelCodeMap.get("newModel")))
                member = memberMapper.getUserByUserIdAndHp(params);
            else
                member = memberMapper.getUserByUserIdAndHpAndDeviceId(params);

            if(member == null) stringObject = "N";
            else {
                data.setRegistUserType(member.getRegistUserType());
                stringObject = "Y";
            }

            if(stringObject.equals("Y"))
                msg = "비밀번호 찾기 - 초기화 성공";
            else
                msg = "비밀번호 찾기 - 초기화 실패";


            if(memberMapper.updatePushToken(params) <= 0) {
                msg = "구글 FCM TOKEN 갱신 실패.";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            data.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200
                    : ApiResponse.ResponseType.CUSTOM_2002, msg);

            return new ResponseEntity<>(data, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /** 비밀번호 변경 - 생성 */
    @Override
    public ResponseEntity<?> doChangePassword(AuthServerDTO params) throws CustomException {

        ApiResponse.Data data = new ApiResponse.Data();
        String stringObject = "N";
        String msg;

        try{

            params.setNewPassword(encoder.encode(params.getUserPassword()));

            if(memberMapper.updatePassword(params) <= 0){
                msg = "비밀번호 변경 - 생성 실패";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
            } else stringObject = "Y";

            if(stringObject.equals("Y"))
                msg = "비밀번호 변경 - 생성 성공";
            else
                msg = "비밀번호 변경 - 생성 실패";


            if(memberMapper.updatePushToken(params) <= 0) {
                msg = "구글 FCM TOKEN 갱신 실패.";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            data.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200
                    : ApiResponse.ResponseType.CUSTOM_2002, msg);

            return new ResponseEntity<>(data, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /** 사용자정보 조회 */
    @Override
    public ResponseEntity<?> doSearch(AuthServerDTO params) throws CustomException {

        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        String userId = params.getUserId();

        try{
            AuthServerDTO member = memberMapper.getUserByUserId(userId);

            if (member == null) {
                msg = "사용자정보가 없습니다.";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
            } else {
                data.setUserId(member.getUserId());
                data.setUserNickname(member.getUserNickname());
                data.setHp(member.getHp());
                data.setHouseholder(member.getHouseholder());
                msg = "사용자정보 조회 성공";
            }

            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            return new ResponseEntity<>(data, HttpStatus.OK);
        }catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /** 회원 별칭(이름) 및 전화번호 변경 */
    @Override
    public ResponseEntity<?> doUpdateUserNicknameHp(AuthServerDTO params) throws CustomException{

        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        String userPassword = params.getUserPassword();
        AuthServerDTO dbPassword;

        try{
            dbPassword = memberMapper.getPasswordByUserId(params.getUserId());
            if (dbPassword == null) {
                msg = "계정이 존재하지 않습니다.";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
            }

            if(!encoder.matches(userPassword, dbPassword.getUserPassword())) {
                msg = "비밀번호 오류.";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
            }

            if(memberMapper.updateUserNicknameAndHp(params) <= 0) {
                msg = "회원 별칭(이름) 및 전화번호 변경 실패.";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
            } else {
                msg = "회원 별칭(이름) 및 전화번호 변경 성공";
            }

            if(memberMapper.updatePushToken(params) <= 0) {
                msg = "구글 FCM TOKEN 갱신 실패.";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            return new ResponseEntity<>(data, HttpStatus.OK);
        }catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /** 비밀번호 변경 - 로그인시 */
    @Override
    public ResponseEntity<?> doUpdatePassword(AuthServerDTO params) throws CustomException{

        ApiResponse.Data data = new ApiResponse.Data();
        String stringObject = "N";
        String oldPassword = params.getOldPassword();
        String userId = params.getUserId();
        String msg;

        try{
            AuthServerDTO account = memberMapper.getAccountByUserId(userId);

            if (account == null) {
                msg = "계정이 존재하지 않습니다.";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
            }
            params.setNewPassword(encoder.encode(params.getNewPassword()));

            if(!encoder.matches(oldPassword, account.getUserPassword())){
                msg = "PW 에러";
                data.setResult(ApiResponse.ResponseType.CUSTOM_2002, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            if(memberMapper.updatePassword(params) <= 0){
                msg = "비밀번호 변경 - 로그인시 실패";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
            } else stringObject = "Y";

            if(stringObject.equals("Y"))
                msg = "비밀번호 변경 - 로그인시 성공";
            else
                msg = "비밀번호 변경 - 로그인시 실패";


            data.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200
                    : ApiResponse.ResponseType.CUSTOM_2002, msg);

            if(memberMapper.updatePushToken(params) <= 0) {
                msg = "구글 FCM TOKEN 갱신 실패.";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            return new ResponseEntity<>(data, HttpStatus.OK);
        }catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /** 사용자(세대원) 정보 조회 */
    @Override
    public ResponseEntity<?> doViewHouseholdMemebers(AuthServerDTO params) throws CustomException{

        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        String userId = params.getUserId();

        try {

            // Device Set 생성
            Set<String> userIds = new HashSet<>();
            List<ApiResponse.Data.User> user = new ArrayList<>();

            List<AuthServerDTO> deviceIds = memberMapper.getDeviceIdByUserId(userId);
            if(deviceIds != null){
                List<AuthServerDTO> members = memberMapper.getHouseMembersByUserId(deviceIds);
                log.info("members: " + members.size());
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
            } else {
                msg = "계정이 존재하지 않습니다.";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
            }

            msg = "사용자(세대원) 정보 조회 성공";

            data.setUser(user);
            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            return new ResponseEntity<>(data, HttpStatus.OK);
        }catch (CustomException e){
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /** 사용자 추가 - 초대 */
    @Override
    public ResponseEntity<?> doAddUser(AuthServerDTO params)
            throws CustomException{

        String stringObject = "N";
        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        AuthServerDTO pushYn;
        MobiusResponse mobiusCode;
        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();

        try {

            if(memberMapper.inviteHouseMember(params) <= 0){
                msg = "사용자 추가 - 초대 실패";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
            } else stringObject = "Y";

            if(stringObject.equals("Y")) {
                conMap.put("body", "Add User OK");
                msg = "사용자 추가 - 초대 성공";
            }
            else {
                conMap.put("body", "Add User FAIL");
                msg = "사용자 추가 - 초대 실패";
            }

            params.setUserId(params.getRequestUserId());
            if(memberMapper.updatePushToken(params) <= 0) {
                msg = "구글 FCM TOKEN 갱신 실패.";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            pushYn = memberMapper.getPushYnStatus(params);
            conMap.put("pushYn", pushYn.getFPushYn());
            conMap.put("targetToken", params.getPushToken());
            conMap.put("title", "Add User");
            conMap.put("id", "Add User ID");
            conMap.put("isEnd", "false");

            String jsonString = objectMapper.writeValueAsString(conMap);
            log.info("jsonString: " + jsonString);


            data.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200
                    : ApiResponse.ResponseType.CUSTOM_2002, msg);

            mobiusCode = mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);
            if(!mobiusCode.getResponseCode().equals("201")){
                msg = "PUSH 메세지 전송 오류";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            return new ResponseEntity<>(data, HttpStatus.OK);
        }catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /** 사용자 초대 - 수락여부 */
    @Override
    public ResponseEntity<?> doInviteStatus(AuthServerDTO params)
            throws CustomException{

        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        AuthServerDTO pushYn;
        String requestUserId = params.getRequestUserId();
        String responseUserId = params.getResponseUserId();
        String inviteAcceptYn = params.getInviteAcceptYn();
        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();

        try {

            /* *
             * 수락시: responseUserId 사용자는 requestUserId 사용자가 가진 DeviceId에 자동으로 mapping된다.
             * 거부시: TBR_OPR_USER_INVITE_STATUS 수락여부 항목 N UPDATE
             * 수락시 쿼리 흐름:
             * 1. TBR_OPR_USER_INVITE_STATUS
             * 2. TBR_OPR_USER_DEVICE에서 requestUserId 검색
             * 3. 2번 출력값으로 TBR_OPR_USER_DEVICE에 responseUserId INSERT
             * */

            List<AuthServerDTO> member;
            if(inviteAcceptYn.equals("Y")){

                if(memberMapper.acceptInvite(params) <= 0){
                    msg = "사용자 초대 - 수락 실패";
                    data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                    return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
                }

                member = memberMapper.getDeviceIdByUserId(requestUserId);
                if (member == null) {
                    msg = "계정이 존재하지 않습니다.";
                    data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                    return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
                }
                log.info("member: " + member);

                Common.updateMemberDTOList(member, "responseUserId", responseUserId);
                Common.updateMemberDTOList(member, "householder", "N");

                if(memberMapper.insertNewHouseMember(member) <= 0){
                    msg = "사용자 초대 - 수락 실패";
                    data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                    return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
                }

            } else if(inviteAcceptYn.equals("N")){

                if(memberMapper.acceptInvite(params) <= 0){
                    msg = "사용자 초대 - 수락 실패";
                    data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                    return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
                }

            } else {
                msg = "예기치 못한 오류로 인해 서버에 연결할 수 없습니다";
                data.setResult(ApiResponse.ResponseType.HTTP_500, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            conMap.put("body", "Accept Invite OK");
            msg = "사용자 초대 - 수락여부 성공";

            // params에 userId 추가
            params.setUserId(params.getRequestUserId());

            if(memberMapper.updatePushToken(params) <= 0) {
                msg = "구글 FCM TOKEN 갱신 실패.";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            pushYn = memberMapper.getPushYnStatus(params);
            conMap.put("pushYn", pushYn.getFPushYn());
            conMap.put("targetToken", params.getPushToken());
            conMap.put("title", "Accept Invite");
            conMap.put("id", "Accept Invite ID");
            conMap.put("isEnd", "false");

            String jsonString = objectMapper.writeValueAsString(conMap);
            log.info("jsonString: " + jsonString);

            if(!mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString).getResponseCode().equals("201")) {
                msg = "PUSH 메세지 전송 오류";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
            }

            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            return new ResponseEntity<>(data, HttpStatus.OK);
        }catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /** 사용자 초대 - 목록 조회 */
    @Override
    public ResponseEntity<?> doInviteListView(AuthServerDTO params)
            throws CustomException{

        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        String userId = params.getUserId();
        try {

            List<AuthServerDTO> invitationInfo = memberMapper.getInvitationList(userId);
            System.out.println("invitationInfo: " + invitationInfo);
            if (invitationInfo.isEmpty()) {
                msg = "사용자 초대 이력이 없습니다.";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

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

            msg = "사용자 초대 - 목록 조회 성공";

            data.setInvitation(inv);
            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            return new ResponseEntity<>(data, HttpStatus.OK);
        }catch (CustomException e){
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /** 사용자(세대원) - 강제탈퇴 */
    @Override
    public ResponseEntity<?> doDelHouseholdMembers(AuthServerDTO params)
            throws CustomException{

        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        AuthServerDTO pushYn;
        String userId = params.getUserId();
        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            if(memberMapper.delHouseMember(userId) <= 0){
                msg = "사용자(세대원) 강제탈퇴 실패";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
            }

            msg = "사용자(세대원) - 강제탈퇴 성공";

            if(memberMapper.updatePushToken(params) <= 0) {
                msg = "구글 FCM TOKEN 갱신 실패.";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            pushYn = memberMapper.getPushYnStatus(params);
            conMap.put("pushYn", pushYn.getFPushYn());
            conMap.put("targetToken", params.getPushToken());
            conMap.put("title", "Force Delete Member");
            conMap.put("body", "Force Delete Member OK");
            conMap.put("id", "Force Delete Member ID");
            conMap.put("isEnd", "false");

            String jsonString = objectMapper.writeValueAsString(conMap);
            log.info("jsonString: " + jsonString);


            if(!mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString).getResponseCode().equals("201")) {
                msg = "PUSH 메세지 전송 오류";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
            }

            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            return new ResponseEntity<>(data, HttpStatus.OK);
        }catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /** 홈 IoT 컨트롤러 알림 설정 */
    @Override
    public ResponseEntity<?> doPushSet(AuthServerDTO params)
            throws CustomException{

        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        String userId = params.getUserId();
        String deviceId = params.getDeviceId();
        List<String> pushCode = params.getPushCd();
        List<String> pushYn = params.getPushYn();

        System.out.println("pushCode: " + pushCode.toString());
        System.out.println("pushYn: " + pushYn.toString());

            try{

                for(int i = 0; i < pushCode.size(); ++i){
                    switch (pushCode.get(i)) {
                        case "01":
                            params.setFPushYn(pushYn.get(i));
                            break;
                        case "02":
                            params.setSPushYn(pushYn.get(i));
                            break;
                        case "03":
                            params.setTPushYn(pushYn.get(i));
                            break;
                    }
                }
                System.out.println("params: " + params);
                if(memberMapper.updatePushCodeStatus(params) <= 0){
                    msg = "홈 IoT 컨트롤러 알림 설정 실패";
                    data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                    return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
                }

                msg = "홈 IoT 컨트롤러 알림 설정 성공";

                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
        }catch (CustomException e){
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /** 홈 IoT 컨트롤러 알림 정보 조회 */
    @Override
    public HashMap<String, Object> doSearchPushSet(AuthServerDTO params)
            throws CustomException{

        String userId = params.getUserId();
        String deviceId = params.getDeviceId();
        HashMap<String, Object> resultMap = new LinkedHashMap<String, Object>();
        List<AuthServerDTO> deviceIdList;

        // "push" 부분을 표현하는 List 생성
        List<Map<String, String>> pushList = new ArrayList<>();
        try{

            deviceIdList = memberMapper.getDeviceIdByUserId(userId);

            // deviceIds를 쉼표로 구분된 String으로 변환
            String deviceIds = deviceIdList.stream()
                    .map(AuthServerDTO::getDeviceId)
                    .collect(Collectors.joining("','", "'", "'"));

            System.out.println("deviceIds: " + deviceIds);

            List<AuthServerDTO> pushCodeInfo = memberMapper.getPushCodeStatus(params.getUserId(), deviceIds);
            if (pushCodeInfo == null) {
                resultMap.put("resultCode", "200");
                resultMap.put("resultMsg", "홈 IoT 컨트롤러 알림 정보 조회 실패");
                return resultMap;
            }
            System.out.println(pushCodeInfo);

            // 사용자가 가진 DeviceId 리스트 개수 만큼 생성
            for(int i = 0; pushCodeInfo.size() > i; ++i){
                System.out.println("int i: " + i);
                // 각각의 "pushCd"와 "pushYn"을 가지는 Map을 생성하여 리스트에 추가
                Map<String, String> push1 = new LinkedHashMap<>();
                push1.put("pushCd", "01");
                push1.put("pushYn", pushCodeInfo.get(i).getFPushYn());
                push1.put("deviceId", pushCodeInfo.get(i).getDeviceId());
                push1.put("controlAuthKey", pushCodeInfo.get(i).getControlAuthKey());
                push1.put("modelCode", pushCodeInfo.get(i).getModelCode());
                pushList.add(push1);

                Map<String, String> push2 = new LinkedHashMap<>();
                push2.put("pushCd", "02");
                push2.put("pushYn", pushCodeInfo.get(i).getSPushYn());
                push2.put("deviceId", pushCodeInfo.get(i).getDeviceId());
                push2.put("controlAuthKey", pushCodeInfo.get(i).getControlAuthKey());
                push2.put("modelCode", pushCodeInfo.get(i).getModelCode());
                pushList.add(push2);

                Map<String, String> push3 = new LinkedHashMap<>();
                push3.put("pushCd", "03");
                push3.put("pushYn", pushCodeInfo.get(i).getTPushYn());
                push3.put("deviceId", pushCodeInfo.get(i).getDeviceId());
                push3.put("controlAuthKey", pushCodeInfo.get(i).getControlAuthKey());
                push3.put("modelCode", pushCodeInfo.get(i).getModelCode());
                pushList.add(push3);
            }

            log.info("pushList: " + pushList);
            resultMap.put("push", pushList);

            resultMap.put("resultCode", "200");
            resultMap.put("resultMsg", "홈 IoT 컨트롤러 알림 정보 조회 성공");

            return resultMap;
        }catch (CustomException e){
            log.error("", e);
            resultMap.put("resultCode", "400");
            resultMap.put("resultMsg", "ERROR");
            return resultMap;
        }
    }

    /** 사용자(세대주) 탈퇴 */
    @Override
    public ResponseEntity<?> doDelHouseholder(AuthServerDTO params)
            throws CustomException{

        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        String nextHouseholdId = null;
        AuthServerDTO pushYn;
        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        try {

            pushYn = memberMapper.getPushYnStatus(params);
            conMap.put("pushYn", pushYn.getFPushYn());
            conMap.put("targetToken", params.getPushToken());
            conMap.put("title", "Delete householder");
            conMap.put("id", "Delete householder ID");
            conMap.put("isEnd", "false");

            String jsonString = objectMapper.writeValueAsString(conMap);
            log.info("jsonString: " + jsonString);

            List<AuthServerDTO> deviceIds = memberMapper.getDeviceIdByUserId(params.getUserId());
            if (deviceIds.isEmpty()) {
                msg = "등록된 존재하지 않습니다.";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
            }

            List<AuthServerDTO> members = memberMapper.getHouseMembersByUserId(deviceIds);
            if (members == null) {
                msg = "등록된 사용자가 존재하지 않습니다.";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
            }
            // List에서 요청자 userId 제거
            members.stream()
                    .filter(x -> x.getUserId()
                            .equals(params.getUserId()))
                    .collect(Collectors.toList())
                    .forEach(members::remove);

            List<String> userIdList = Common.extractJson(members.toString(), "userId");

            if(userIdList != null) nextHouseholdId = userIdList.get(0);

            if(memberMapper.delHouseMember(params.getUserId()) <= 0){
                msg = "사용자(세대주) 탈퇴 실패";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
            }

            if(memberMapper.updateHouseholdTbrOprUser(nextHouseholdId) <= 0){
                msg = "사용자(세대주) 탈퇴 실패";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
            }

            if( memberMapper.updateHouseholdTbrOprUserDevice(nextHouseholdId) <= 0){
                msg = "사용자(세대주) 탈퇴 실패";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
            }

            conMap.put("body", "Delete householder OK");
            msg = "사용자(세대주) 탈퇴  실패";

            if(memberMapper.updatePushToken(params) <= 0) {
                msg = "구글 FCM TOKEN 갱신 실패.";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            if(!mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString).getResponseCode().equals("201")) {
                msg = "PUSH 메세지 전송 오류";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
            }

            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            return new ResponseEntity<>(data, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /** 홈IoT 서비스 회원 탈퇴 */
    @Override
    public ResponseEntity<?> doWithdrawal(AuthServerDTO params)
            throws CustomException{

        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        String userId = params.getUserId();
        String userPassword = params.getUserPassword();
        AuthServerDTO account;

        try {

            account = memberMapper.getAccountByUserId(userId);
            if(!encoder.matches(userPassword, account.getUserPassword())){
                msg = "PW 에러";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }
            /* *
             * 홈IoT 서비스 탈퇴 시 삭제 Table
             * 1. TBR_OPR_ACCOUNT
             * 2. TBR_OPR_USER
             * 3. TBR_OPR_USER_DEVICE
             * 4. TBT_OPR_DEVICE_REGIST
             * 프로시져 사용 (deleteUserFromService)
             * */
            if(!memberMapper.deleteMemberFromService(userId).equals("100")){
                msg = "홈IoT 서비스 회원 탈퇴 실패";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
            }

            msg = "홈IoT 서비스 회원 탈퇴 성공";


            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            return new ResponseEntity<>(data, HttpStatus.OK);
        }catch (CustomException e){
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /** 홈 IoT 컨트롤러 인증 */
    @Override
    public ResponseEntity<?> doDeviceAuthCheck(AuthServerDTO params)
            throws CustomException{

        ApiResponse.Data result = new ApiResponse.Data();
        String msg;
        AuthServerDTO pushYn;
        String userId = params.getUserId();
        List<AuthServerDTO> deviceIdAndAuthKey;
        List<AuthServerDTO> deviceAuthCheck;
        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();

        try{
            deviceIdAndAuthKey = deviceMapper.getDeviceAuthCheckValuesByUserId(userId);
            if(deviceIdAndAuthKey.isEmpty()){
                msg = "홈 IoT 컨트롤러 인증 실패.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }else {
                deviceAuthCheck = deviceMapper.deviceAuthCheck(deviceIdAndAuthKey);
                if(deviceAuthCheck.isEmpty()) {
                    msg = "홈 IoT 컨트롤러 인증 실패.";
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                    return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
                }
            }

            conMap.put("body", "Device Auth Check OK");
            msg = "홈 IoT 컨트롤러 인증 성공";

            result.setResult(ApiResponse.ResponseType.HTTP_200, msg);

            if(memberMapper.updatePushToken(params) <= 0) {
                msg = "구글 FCM TOKEN 갱신 실패.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            pushYn = memberMapper.getPushYnStatus(params);
            conMap.put("pushYn", pushYn.getFPushYn());
            conMap.put("targetToken", params.getPushToken());
            conMap.put("title", "Device Auth Check");
            conMap.put("id", "Device Auth Check ID");
            conMap.put("isEnd", "false");

            String jsonString = objectMapper.writeValueAsString(conMap);
            log.info("jsonString: " + jsonString);

            if(!mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString).getResponseCode().equals("201")) {
                msg = "PUSH 메세지 전송 오류";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }
            return new ResponseEntity<>(result, HttpStatus.OK);
        }catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /** 홈 IoT 최초 등록 인증 */
    @Override
    public ResponseEntity<?> doFirstDeviceAuthCheck(AuthServerDTO params)
            throws CustomException{

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject = "N";
        String msg;
        String userId = params.getUserId();

        MobiusResponse aeResult;
        MobiusResponse cntResult;
        MobiusResponse cinResult;
        MobiusResponse subResult;

        Map<String, String> conMap = new HashMap<>();

        try{

            /*
            * 위 API 호출 마다 SerialNum, UserId 정보로 Cin 생성
            * 생성이 정상적으로 동작 후 201을 Return 한다면 해당 AE, CNT는 존재하므로 생성X
            * 하지만 201을 Return 하지 못하는 경우 AE, CNT가 없다 판단하여 신규 생성
            * */
            params.setSerialNumber("    " + params.getSerialNumber());
            params.setModelCode(" " + params.getModelCode());
            aeResult = mobiusService.createAe(common.stringToHex(params.getSerialNumber()));
            cntResult = mobiusService.createCnt(common.stringToHex(params.getSerialNumber()), params.getUserId());
            subResult = mobiusService.createSub(common.stringToHex(params.getSerialNumber()), params.getUserId(), "gw");

            if(aeResult == null && cntResult == null && subResult == null){
                msg = "홈 IoT 최초 등록 인증 실패";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }
            else stringObject = "Y";

            if(stringObject.equals("Y")){
                result.setDeviceId("0.2.481.1.1." + common.stringToHex(params.getModelCode()) + "." + common.stringToHex(params.getSerialNumber()));
                msg = "최초 인증 성공";
            } else {
                msg = "최초 인증 실패";
            }

            if(memberMapper.updatePushToken(params) <= 0) {
                msg = "구글 FCM TOKEN 갱신 실패.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            result.setResult("Y".equalsIgnoreCase(stringObject) ?
                    ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);

            return new ResponseEntity<>(result, HttpStatus.OK);
        }catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /** API 인증키 갱신 */
    @Override
    public ResponseEntity<?> doAccessTokenRenewal(AuthServerDTO params)
            throws CustomException{

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject;
        String msg;
        String inputPassword = params.getUserPassword();
        String userId = params.getUserId();
        String token;
        AuthServerDTO account;

        try{
            account = memberMapper.getAccountByUserId(userId);
            if (account == null) {
                msg = "계정이 존재하지 않습니다.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }

            if(!encoder.matches(inputPassword, account.getUserPassword())){
                msg = "PW 에러";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            token = common.createJwtToken(userId, "NORMAL", "AccessTokenRenewal");
            log.info("token: " + token);

            if(token.isEmpty()) stringObject = "N";
            else stringObject = "Y";

            if(stringObject.equals("Y")) msg = "API인증키 갱신 성공";
            else msg = "API인증키 갱신 실패";

            result.setAccessToken(token);

            result.setResult("Y".equalsIgnoreCase(stringObject) ?
                    ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);

            return new ResponseEntity<>(result, HttpStatus.OK);
        }catch (CustomException e){
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /** 홈 IoT 컨트롤러 삭제(회원 매핑 삭제) */
    @Override
    public ResponseEntity<?> doUserDeviceDelete(AuthServerDTO params)
            throws CustomException {

        String stringObject = "N";
        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        AuthServerDTO pushYn;
        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();

        try{

            pushYn = memberMapper.getPushYnStatus(params);
            conMap.put("pushYn", pushYn.getFPushYn());
            conMap.put("targetToken", params.getPushToken());
            conMap.put("title", "User Device Delete");
            conMap.put("id", "User Device Delete ID");
            conMap.put("isEnd", "false");

            String jsonString = objectMapper.writeValueAsString(conMap);
            log.info("jsonString: " + jsonString);

            /* *
             * TBT_OPR_DEVICE_REGIST - 임시 단말 등록 정보
             * TBR_OPR_USER_DEVICE - 사용자 단말 정보
             * TBR_OPR_DEVICE_DETAIL - 단말정보상세
             * 프로시져
             * */
            if(!memberMapper.deleteControllerMapping(params).equals("100")){
                msg = "홈 IoT 컨트롤러 삭제(회원 매핑 삭제) 실패";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
            } else stringObject = "Y";

            if(stringObject.equals("Y")) {
                conMap.put("body", "User Device Delete OK");
                msg = "홈 IoT 컨트롤러 삭제(회원 매핑 삭제) 성공";
            }
            else {
                conMap.put("body", "User Device Delete OK");
                msg = "홈 IoT 컨트롤러 삭제(회원 매핑 삭제) 실패";
            }

            if(memberMapper.updatePushToken(params) <= 0) {
                msg = "구글 FCM TOKEN 갱신 실패.";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            if(!mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString).getResponseCode().equals("201")) {
                msg = "PUSH 메세지 전송 오류";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
            }

            data.setResult("Y".equalsIgnoreCase(stringObject) ?
                    ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);

            return new ResponseEntity<>(data, HttpStatus.OK);
        }catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /** 스마트알림 - PUSH 이력 조회 */
    @Override
    public ResponseEntity<?> doViewPushHistory(AuthServerDTO params)
            throws CustomException{

        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        String userId = params.getUserId();
        List<AuthServerDTO> member;

        try{

            member = memberMapper.getPushInfoList(params);
            if (member == null) {
                msg = "계정이 존재하지 않습니다.";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
            } else {

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

            msg = "스마트알림 - PUSH 이력 조회 성공";

            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            return new ResponseEntity<>(data, HttpStatus.OK);
        }catch (CustomException e){
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /** 기기 별칭 수정 */
    @Override
    public ResponseEntity<?> doDeviceNicknameChange(AuthServerDTO params)
            throws CustomException{

        ApiResponse.Data data = new ApiResponse.Data();
        String stringObject = "N";
        String msg;
        AuthServerDTO pushYn;
        String userId = params.getUserId();
        String deviceId = params.getDeviceId();
        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        try {

            // deviceNickname Input 대신 newDeviceNickname을 받아서 Setter 사용
            params.setDeviceNickname(params.getNewDeviceNickname());

            if(deviceMapper.changeDeviceNickname(params) <= 0){
                msg = "기기 별칭 수정 실패";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
            }
            if(deviceMapper.changeDeviceNicknameTemp(params) <= 0){
                msg = "기기 별칭 수정 실패";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
            }else stringObject = "Y";

            if(stringObject.equals("Y")) {
                conMap.put("body", "Device Nickname Change OK");
                msg = "기기 별칭 수정 성공";
            } else {
                conMap.put("body", "Device Nickname Change FAIL");
                msg = "기기 별칭 수정 실패";
            }

            if(memberMapper.updatePushToken(params) <= 0) {
                msg = "구글 FCM TOKEN 갱신 실패.";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            pushYn = memberMapper.getPushYnStatus(params);
            conMap.put("pushYn", pushYn.getFPushYn());
            conMap.put("targetToken", params.getPushToken());
            conMap.put("title", "Device Nickname Change");
            conMap.put("id", "Device Nickname Change ID");
            conMap.put("isEnd", "false");

            String jsonString = objectMapper.writeValueAsString(conMap);
            log.info("jsonString: " + jsonString);

            if(!mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString).getResponseCode().equals("201")) {
                msg = "PUSH 메세지 전송 오류";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
            }

            data.setResult("Y".equalsIgnoreCase(stringObject) ?
                    ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);

            return new ResponseEntity<>(data, HttpStatus.OK);
        }catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /** 기기 밝기 조절 */
    @Override
    public ResponseEntity<?> doBrightnessControl(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        String msg;
        AuthServerDTO serialNumber;
        String userId = params.getUserId();
        String deviceId = params.getDeviceId();
        String uuId = common.getTransactionId();
        String redisValue;
        AuthServerDTO pushYn;
        Map<String, Object> conMap = new HashMap<>();
        Map<String, Object> conMap1 = new HashMap<>();
        MobiusResponse mobiusResponse;
        ObjectMapper objectMapper = new ObjectMapper();
        String responseMessage;

        try{

            serialNumber = deviceMapper.getSingleSerialNumberBydeviceId(params.getDeviceId());
            if(serialNumber.getSerialNumber() == null){
                msg = "기기 밝기 조절 실패";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }
            conMap.put("controlAuthKey", params.getControlAuthKey());
            conMap.put("deviceId", params.getDeviceId());
            conMap.put("deviceType", params.getDeviceType());
            conMap.put("modelCode", params.getModelCode());
            conMap.put("brightnessLevel", params.getBrightnessLevel());
            conMap.put("functionId", "blCf");

            redisValue = userId + "," + "blCf";
            redisCommand.setValues(uuId, redisValue);
            String jsonString = objectMapper.writeValueAsString(conMap);
            mobiusResponse = mobiusService.createCin(serialNumber.getSerialNumber(), userId, jsonString);
            if(!mobiusResponse.getResponseCode().equals("201")){
                msg = "중계서버 오류";
                result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            try {
                // 메시징 시스템을 통해 응답 메시지 대기
                responseMessage = gwMessagingSystem.waitForResponse("blCf" + uuId, TIME_OUT, TimeUnit.SECONDS);
                if(responseMessage == null) {
                    msg = "REQ_TIME_OUT";
                    result.setResult(ApiResponse.ResponseType.HTTP_500, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);

                } else if(!responseMessage.equals("0")) {
                    msg = "기기 밝기 수정 실패";
                    result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }
                // 응답 확인
                log.info("receiveCin에서의 응답: " + responseMessage);
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                log.error("", e);
            }

            conMap1.put("body", "Brightness Control OK");
            msg = "기기 밝기 수정 성공";
            result.setResult(ApiResponse.ResponseType.HTTP_200, msg);

            if(memberMapper.updatePushToken(params) <= 0) {
                msg = "구글 FCM TOKEN 갱신 실패.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            pushYn = memberMapper.getPushYnStatus(params);
            conMap1.put("pushYn", pushYn.getFPushYn());
            conMap1.put("targetToken", params.getPushToken());
            conMap1.put("title", "Reset Password");
            conMap1.put("id", "Reset Password ID");
            conMap1.put("isEnd", "false");

            String jsonString1 = objectMapper.writeValueAsString(conMap1);
            log.info("jsonString1: " + jsonString1);

            redisCommand.deleteValues(uuId);

            if(!mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString1).getResponseCode().equals("201")) {
                msg = "PUSH 메세지 전송 오류";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }
            return new ResponseEntity<>(result, HttpStatus.OK);
        }catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /** 공지사항 조회 */
    @Override
    public ResponseEntity<?> doNotice(AuthServerDTO params) throws CustomException {

        ApiResponse.Data data = new ApiResponse.Data();
        String userId = params.getUserId();
        String msg;
        List<AuthServerDTO> noticeList;
        try {

            noticeList = memberMapper.getNoticeList();
            if(noticeList.isEmpty()) {
                msg = "공지사항 목록이 없습니다.";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }
            else {

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

            msg = "공지사항 조회 성공";

            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            return new ResponseEntity<>(data, HttpStatus.OK);
        }catch (CustomException e){
            log.error("", e);
        }
        return null;
    }

    /** 기기 설치 위치 별칭 수정 */
    @Override
    public ResponseEntity<?> doUpdateDeviceLocationNickname(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject = "N";
        String msg;

        String userId = params.getUserId();
        String deviceId = params.getDeviceId();
        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        try {

            if(memberMapper.updateDeviceLocationNicknameDeviceDetail(params) <= 0){
                msg = "기기 설치 위치 별칭 수정 실패";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            } else stringObject = "Y";
            if(memberMapper.updateDeviceLocationNicknameDeviceRegist(params) <= 0){
                msg = "기기 설치 위치 별칭 수정 실패";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            } else stringObject = "Y";

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

            if(memberMapper.updatePushToken(params) <= 0) {
                msg = "구글 FCM TOKEN 갱신 실패.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            conMap.put("targetToken", params.getPushToken());
            conMap.put("title", "Device Auth Check");
            conMap.put("id", "Device Auth Check ID");
            conMap.put("isEnd", "false");

            String jsonString = objectMapper.writeValueAsString(conMap);

            if(!mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString).getResponseCode().equals("201")) {
                msg = "PUSH 메세지 전송 오류";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            }

            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /** 임시 저장키 생성 */
    @Override
    public ResponseEntity<?> dogenerateTempKey(String userId) throws Exception {

        ApiResponse.Data result = new ApiResponse.Data();

        try {
            result.setTmpRegistKey(userId + common.getCurrentDateTime());
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
         log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.OK);
        }
    }

    /** 안전안심 알람 설정 */
    @Override
    public ResponseEntity<?> doSafeAlarmSet(AuthServerDTO params) throws Exception {

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject = "N";
        String msg;

        try {

            if(memberMapper.UpdateSafeAlarmSet(params) <= 0){
                msg = "안전안심 알람 설정 실패";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
            } else stringObject = "Y";

            if(stringObject.equals("Y"))
                msg = "안전안심 알람 설정 성공";
            else
                msg = "안전안심 알람 설정 실패";

            result.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200
                    : ApiResponse.ResponseType.CUSTOM_2002, msg);

            if(memberMapper.updatePushToken(params) <= 0) {
                msg = "구글 FCM TOKEN 갱신 실패.";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }
}
