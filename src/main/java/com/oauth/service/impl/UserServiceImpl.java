package com.oauth.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oauth.constants.MobiusResponse;
import com.oauth.dto.AuthServerDTO;
import com.oauth.dto.gw.DeviceStatusInfo;
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

        String msg;
        String token;
        String hp;

        AuthServerDTO householdStatus;
        try {

            AuthServerDTO account = memberMapper.getAccountByUserId(userId);
            if (account == null) {
                msg = "계정이 존재하지 않습니다.";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1004, msg);
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

            householdStatus = memberMapper.getHouseholdByUserId(userId);
            AuthServerDTO member = memberMapper.getUserByUserId(userId);
            if (member == null) {
                msg = "계정이 존재하지 않습니다.";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1004, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else userNickname = member.getUserNickname();

            if(memberMapper.getDeviceIdFromDeviceGroup(householdStatus.getGroupId()).getDeviceCount().equals("0"))
                result.setDeviceInfo("N");
            else
                result.setDeviceInfo("Y");

            token = common.createJwtToken(userId, "NORMAL", "Login");
            log.info("Token: " + token);
            result.setRegistUserType(registUserType);
            result.setAccessToken(token);
            result.setUserNickname(userNickname);

            msg = "로그인 성공";

            hp = memberMapper.getHpByUserId(userId).getHp();
            if (hp == null) {
                msg = "계정이 존재하지 않습니다.";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1004, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else result.setHp(hp);

            AuthServerDTO params = new AuthServerDTO();

            params.setAccessToken(token);
            params.setPushToken(pushToken);
            params.setUserId(userId);

            if(memberMapper.updatePushToken(params) <= 0) log.info("구글 FCM TOKEN 갱신 실패.");
            if(memberMapper.updateLoginDatetime(params) <= 0) log.info("LOGIN_INFO_UPDATE_ERROR");

            result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            log.info("result: " + result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        }catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
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

            params.setNewHp(params.getHp());
            if(memberMapper.checkDuplicateHp(params.getNewHp()).getHpCount().equals("0")){
                if(memberMapper.updateUserNicknameAndHp(params) <= 0) {
                    msg = "회원 별칭(이름) 및 전화번호 변경 실패.";
                    data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(data, HttpStatus.OK);
                }
            } else {
                msg = "회원 별칭(이름) 및 전화번호 변경 실패.";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1007, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            if(memberMapper.insertAccount(params) <= 0) {
                msg = "회원가입 실패";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            if(memberMapper.insertMember(params) <= 0) {
                msg = "회원가입 실패";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            if(memberMapper.insertHouseholder(params) <= 0) {
                msg = "회원가입 실패";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            msg = "회원가입 성공";

            token = common.createJwtToken(userId, "NORMAL", "Regist");
            log.info("Token: " + token);
            data.setAccessToken(token);
            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            log.info("data: " + data);
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

            if(stringObject.equals("Y")) {
                msg = "중복 되는 ID";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1004, msg);
            } else{
                msg = "중복 되지 않는 ID";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            }

            data.setDuplicationYn(stringObject);
            log.info("data: " + data);
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
        String msg;
        List<AuthServerDTO> member;
        List<String> userId;
        try {

            member = memberMapper.getUserByHp(userHp);
            if (member.isEmpty()) {
                msg = "일치하는 회원정보가 없습니다.";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1004, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            } else userId = Common.extractJson(member.toString(), "userId");

            msg = "ID 찾기 성공";

            data.setUserIdList(userId);
            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            log.info("data: " + data);
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
        AuthServerDTO member;
        String msg;

        try {

            member = memberMapper.getUserByUserIdAndHp(params);
            if(member == null) stringObject = "N";
            else {
                data.setRegistUserType(member.getRegistUserType());
                stringObject = "Y";
            }

            if(stringObject.equals("Y"))
                msg = "비밀번호 찾기 - 초기화 성공";
            else
                msg = "비밀번호 찾기 - 초기화 실패";

            if(memberMapper.updatePushToken(params) <= 0) log.info("구글 FCM TOKEN 갱신 실패.");

            data.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200
                    : ApiResponse.ResponseType.CUSTOM_1004, msg);

            log.info("data: " + data);
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
                data.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
                new ResponseEntity<>(data, HttpStatus.OK);
            } else stringObject = "Y";

            if(stringObject.equals("Y")) msg = "비밀번호 변경 - 생성 성공";
            else msg = "비밀번호 변경 - 생성 실패";

            if(memberMapper.updatePushToken(params) <= 0) log.info("구글 FCM TOKEN 갱신 실패.");

            data.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200
                    : ApiResponse.ResponseType.CUSTOM_1003, msg);

            log.info("data: " + data);
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
                data.setResult(ApiResponse.ResponseType.CUSTOM_1004, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            } else {
                data.setUserId(member.getUserId());
                data.setUserNickname(member.getUserNickname());
                data.setHp(member.getHp());
                data.setHouseholder(member.getHouseholder());
                msg = "사용자정보 조회 성공";
            }

            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            log.info("data: " + data);
            return new ResponseEntity<>(data, HttpStatus.OK);
        }catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /** 사용자 그룹 정보 조회 */
    @Override
    public ResponseEntity<?> doSearchGroupInfo(AuthServerDTO params) throws CustomException {

        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        List<AuthServerDTO> groupInfo;
        List<Map<String, Object>> groupInfoList = new ArrayList<>();
        try {
            groupInfo = memberMapper.getMemberByGroupIdxList(params);
            if (groupInfo == null) {
                msg = "그룹 정보가 없습니다.";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1004, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }
            for(AuthServerDTO authServerDTO : groupInfo){
                Map<String, Object> map = new HashMap<>();
                map.put("groupIdx", authServerDTO.getGroupIdx());
                map.put("groupName", authServerDTO.getGroupName());
                groupInfoList.add(map);
            }
            msg = "그룹 정보 조회 성공";
            data.setGroupInfo(groupInfoList);
            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            return new ResponseEntity<>(data, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /** 회원 별칭(이름) 및 전화번호 변경 */
    @Override
    public ResponseEntity<?> doUpdateUserNicknameHp(AuthServerDTO params) throws CustomException{

        ApiResponse.Data data = new ApiResponse.Data();
        String msg = null;
        String userPassword = params.getUserPassword();
        AuthServerDTO dbPassword;

        try{
            dbPassword = memberMapper.getPasswordByUserId(params.getUserId());
            if (dbPassword == null) {
                msg = "계정이 존재하지 않습니다.";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1004, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            if(!encoder.matches(userPassword, dbPassword.getUserPassword())) {
                msg = "비밀번호 오류.";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            if(memberMapper.updateGrpNick(params) <= 0) {
                msg = "회원 별칭(이름) 및 전화번호 변경 실패.";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            if(params.getNewHp() !=  null && !params.getNewHp().isEmpty()){
                if(memberMapper.checkDuplicateHp(params.getNewHp()).getHpCount().equals("0")){
                    if(memberMapper.updateUserNicknameAndHp(params) <= 0) {
                        msg = "회원 별칭(이름) 및 전화번호 변경 실패.";
                        data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                        return new ResponseEntity<>(data, HttpStatus.OK);
                    } else msg = "회원 별칭(이름) 및 전화번호 변경 성공";
                } else {
                    msg = "회원 별칭(이름) 및 전화번호 변경 실패.";
                    data.setResult(ApiResponse.ResponseType.CUSTOM_1007, msg);
                    return new ResponseEntity<>(data, HttpStatus.OK);
                }
            }

            if(memberMapper.updatePushToken(params) <= 0) log.info("구글 FCM TOKEN 갱신 실패.");

            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            log.info("data: " + data);
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
                data.setResult(ApiResponse.ResponseType.CUSTOM_1004, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }
            params.setNewPassword(encoder.encode(params.getNewPassword()));

            if(!encoder.matches(oldPassword, account.getUserPassword())){
                msg = "PW 에러";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            if(memberMapper.updatePassword(params) <= 0){
                msg = "비밀번호 변경 - 로그인시 실패";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                new ResponseEntity<>(data, HttpStatus.OK);
            } else stringObject = "Y";

            if(stringObject.equals("Y"))
                msg = "비밀번호 변경 - 로그인시 성공";
            else
                msg = "비밀번호 변경 - 로그인시 실패";


            data.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200
                    : ApiResponse.ResponseType.CUSTOM_1003, msg);

            if(memberMapper.updatePushToken(params) <= 0) log.info("구글 FCM TOKEN 갱신 실패.");
            log.info("data: " + data);
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
        String groupIdx = params.getGroupIdxList();
        List<AuthServerDTO> userIdList;
        List<String> groupIdxList;
        List<HashMap<String, String>> user = new ArrayList<>();
        try {

            groupIdxList = Arrays.asList(groupIdx.split(","));

            userIdList = memberMapper.getFamilyMemberByGroupIdxList(groupIdxList);

            if(userIdList != null){
                for(AuthServerDTO authServerDTO : userIdList){
                    HashMap<String, String> userMap = new HashMap<>();
                    String household = "N";
                    if(authServerDTO.getUserId().equals(authServerDTO.getGroupId())) household = "Y";
                    userMap.put("userId", authServerDTO.getUserId());
                    userMap.put("userNickname", authServerDTO.getUserNickname());
                    userMap.put("householder", household);
                    userMap.put("groupName", authServerDTO.getGroupName());
                    userMap.put("groupId", authServerDTO.getGroupId());
                    userMap.put("groupIdx", authServerDTO.getGroupIdx());
                    user.add(userMap);  // 리스트에 HashMap 추가
                }
            } else {
                msg = "계정이 존재하지 않습니다.";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1004, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            msg = "사용자(세대원) 정보 조회 성공";

            data.setUser(user);
            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            log.info("data: " + data);
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
        AuthServerDTO userNickname;
        AuthServerDTO pushToken;
        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();

        try {

            params.setUserId(params.getRequestUserId());
            if(memberMapper.updatePushToken(params) <= 0) log.info("구글 FCM TOKEN 갱신 실패.");

            userNickname = memberMapper.getUserNickname(params.getRequestUserId());
            userNickname.setUserNickname(common.stringToHex(userNickname.getUserNickname()));

            pushToken = memberMapper.getPushTokenByUserId(params.getResponseUserId());

            if(pushToken == null){
                msg = "뭘 추가 하는 거죠?";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }
            params.setUserId(params.getResponseUserId());

            conMap.put("targetToken", pushToken.getPushToken());
            conMap.put("title", "adUr");
            conMap.put("isEnd", "false");
            conMap.put("body", "adUr");
            conMap.put("userNickname", userNickname.getUserNickname());
            conMap.put("pushYn", "Y");

            String jsonString = objectMapper.writeValueAsString(conMap);
            log.info("Add User jsonString: " + jsonString);

            if(!mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString).getResponseCode().equals("201")){
                log.info("PUSH 메세지 전송 오류");
            } else {
                if(memberMapper.inviteHouseMember(params) <= 0){
                    msg = "사용자 추가 - 초대 실패";
                    data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    new ResponseEntity<>(data, HttpStatus.OK);
                } else stringObject = "Y";
            }

            if(stringObject.equals("Y")) msg = "사용자 추가 - 초대 성공";
            else msg = "사용자 추가 - 초대 실패";

            data.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200
                    : ApiResponse.ResponseType.CUSTOM_1018, msg);

            log.info("data: " + data);
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
        AuthServerDTO userNickname;
        AuthServerDTO pushToken;

        List<AuthServerDTO> deviceIdList;
        List<AuthServerDTO> inputList;

        String requestUserId = params.getRequestUserId();
        String responseUserId = params.getResponseUserId();
        String responseHp = params.getResponseHp();
        String inviteAcceptYn = params.getInviteAcceptYn();
        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            params.setUserId(requestUserId);
            // 세대주가 가지고 있는 기기 정보 List
            deviceIdList = memberMapper.getRegistDeviceIdByUserId(params);

            if(inviteAcceptYn.equals("Y")){

            /* TODO
            *  1. 신규 세대원을 TBD_USER_INVITE_GROUP 테이블에 추가
            *  2. 신규 세대원 관련 PUSH Message Info 등록
            * */
                memberMapper.insertInviteGroupMember(params);
                params.setUserId(responseUserId);

//                params.setNewId(params.getRequestUserId());
//                params.setOldId(params.getResponseUserId());
//                memberMapper.updateGrpInfoTableForNewHousehold(params);

                inputList = new ArrayList<>();
                for(AuthServerDTO authServerDTO : deviceIdList){
                    AuthServerDTO newDevice = new AuthServerDTO();
                    newDevice.setDeviceId(authServerDTO.getDeviceId());
                    newDevice.setUserId(responseUserId);
                    newDevice.setHp(responseHp);

                    // 리스트에 추가
                    inputList.add(newDevice);
                }
                if(memberMapper.insertUserDevicePushByList(inputList) <= 0){
                    msg = "사용자 초대 - 수락 실패";
                    data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(data, HttpStatus.OK);
                }

                inputList = new ArrayList<>();
                for(AuthServerDTO authServerDTO : deviceIdList){
                    AuthServerDTO newDevice = new AuthServerDTO();
                    newDevice.setDeviceId(authServerDTO.getDeviceId());
                    newDevice.setUserId(responseUserId);

                    // 리스트에 추가
                    inputList.add(newDevice);
                }

                if(deviceMapper.insertDeviceListGrpInfo(inputList) <= 0){
                    msg = "사용자 초대 - 수락 실패";
                    data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(data, HttpStatus.OK);
                }
                if(memberMapper.acceptInvite(params) <= 0){
                    msg = "사용자 초대 - 수락 실패";
                    data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(data, HttpStatus.OK);
                }
            } else if(inviteAcceptYn.equals("N")){

                if(memberMapper.acceptInvite(params) <= 0){
                    msg = "사용자 초대 - 수락 실패";
                    data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(data, HttpStatus.OK);
                }

            } else {
                msg = "예기치 못한 오류로 인해 서버에 연결할 수 없습니다";
                data.setResult(ApiResponse.ResponseType.HTTP_500, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            conMap.put("body", "Accept Invite OK");
            msg = "사용자 초대 - 수락여부 성공";

            // params에 userId 추가
            params.setUserId(params.getResponseUserId());

            if(memberMapper.updatePushToken(params) <= 0) log.info("구글 FCM TOKEN 갱신 실패.");
            pushToken = memberMapper.getPushTokenByUserId(requestUserId);

            userNickname = memberMapper.getUserNickname(requestUserId);
            userNickname.setUserNickname(common.stringToHex(userNickname.getUserNickname()));

            conMap.put("pushYn", "Y");
            conMap.put("targetToken", pushToken.getPushToken());
            conMap.put("userNickname", userNickname.getUserNickname());
            conMap.put("title", "acIv");
            conMap.put("acIv", inviteAcceptYn);
            conMap.put("id", "Accept Invite ID");
            conMap.put("isEnd", "false");

            String jsonString = objectMapper.writeValueAsString(conMap);
            log.info("jsonString: " + jsonString);

            if(!mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString).getResponseCode().equals("201"))
                log.info("PUSH 메세지 전송 오류");

            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            log.info("data: " + data);
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

        List<Map<String, String>> invitationList = new ArrayList<>();
        List<AuthServerDTO> invitationInfo;
        try {

            invitationInfo = memberMapper.getInvitationList(userId);
            if (invitationInfo.isEmpty()) {
                msg = "사용자 초대 이력이 없습니다.";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            for(AuthServerDTO authServerDTO : invitationInfo){
                Map<String, String> map = new HashMap<>();
                map.put("invitationIdx", authServerDTO.getInvitationIdx());
                map.put("inviteAcceptYn", authServerDTO.getInviteAcceptYn());
                map.put("requestUserId", authServerDTO.getRequestUserId());
                map.put("requestUserNick", authServerDTO.getRequestUserNick());
                map.put("responseUserId", authServerDTO.getResponseUserId());
                map.put("responseHp", authServerDTO.getResponseHp());
                map.put("inviteStartDate", authServerDTO.getInviteStartDate());
                map.put("inviteEndDate", authServerDTO.getInviteEndDate());
                map.put("groupIdx", authServerDTO.getGroupIdx());
                map.put("groupName", authServerDTO.getGroupName());
                invitationList.add(map);
            }

            msg = "사용자 초대 - 목록 조회 성공";

            data.setInvitation(invitationList);
            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            log.info("data: " + data);
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
        String delUserId = params.getDelUserId();
        String userId = params.getUserId();
        List<AuthServerDTO> deviceIdList;
        List<AuthServerDTO> inputList;

        try {

            memberMapper.delHouseholdMember(delUserId);
            // TODO: TBR_OPR_USER_DEVICE_PUSH 테이블 삭제
            memberMapper.deleteUserDevicePush(delUserId);

            // TBR_IOT_DEVICE_GRP_INFO deleteDeviceGrpInfo
            // TODO: 세대주 ID로 세대주 등록 기기 목록을 Regist 테이블에서 불러온후 해당 기기를 세대원 TBR_IOT_DEVICE_GRP_INFO 테이블에서 삭제
            deviceIdList = memberMapper.getDeviceIdFromRegist(userId);

            inputList = new ArrayList<>();
            for(AuthServerDTO authServerDTO : deviceIdList){
                AuthServerDTO newDevice = new AuthServerDTO();
                newDevice.setDeviceId(authServerDTO.getDeviceId());
                newDevice.setUserId(userId);
                // 리스트에 추가
                inputList.add(newDevice);
            }

            memberMapper.deleteDeviceGrpInfo(inputList);

            // TODO: TBD_USER_INVITE_GROUP 테이블 삭제
            if(memberMapper.deleteUserInviteGroup(params) <= 0){
                msg = "사용자(세대원) 강제탈퇴 실패";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            msg = "사용자(세대원) - 강제탈퇴 성공";

            if(memberMapper.updatePushToken(params) <= 0) log.info("구글 FCM TOKEN 갱신 실패.");

            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            log.info("data: " + data);
            return new ResponseEntity<>(data, HttpStatus.OK);
        }catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.OK);
        }
    }

    /** 홈 IoT 컨트롤러 알림 설정 */
    @Override
    public ResponseEntity<?> doPushSet(AuthServerDTO params)
            throws CustomException{

        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        List<String> pushCode = params.getPushCd();
        List<String> pushYn = params.getPushYn();

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
                if(memberMapper.updatePushCodeStatus(params) <= 0) log.info("홈 IoT 컨트롤러 알림 설정 실패");

                msg = "홈 IoT 컨트롤러 알림 설정 성공";

                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                log.info("data: " + data);
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
        String householderId;
        HashMap<String, Object> resultMap = new LinkedHashMap<String, Object>();
        List<AuthServerDTO> deviceIdList;

        // "push" 부분을 표현하는 List 생성
        List<Map<String, String>> pushList = new ArrayList<>();
        try{

            // TDOD: 세대주 ID 쿼리
            householderId = memberMapper.getHouseholdByUserId(userId).getGroupId();
            deviceIdList = memberMapper.getDeviceIdByUserIds(householderId);

            // deviceIds를 쉼표로 구분된 String으로 변환
            String deviceIds = deviceIdList.stream()
                    .map(AuthServerDTO::getDeviceId)
                    .collect(Collectors.joining("','", "'", "'"));

            List<AuthServerDTO> pushCodeInfo = memberMapper.getPushCodeStatus(params.getUserId(), deviceIds);
            if (pushCodeInfo == null) {
                resultMap.put("resultCode", "1018");
                resultMap.put("resultMsg", "홈 IoT 컨트롤러 알림 정보 조회 실패");
                return resultMap;
            }
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

            resultMap.put("push", pushList);
            resultMap.put("resultCode", "200");
            resultMap.put("resultMsg", "홈 IoT 컨트롤러 알림 정보 조회 성공");

            log.info("resultMap: " + resultMap);
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

        String userId = params.getUserId();
        AuthServerDTO nextHouseholder;

        try {

            /**
             * TODO
             * 1. TBD_USER_INVITE_GROUP 에서 기존 세대주 삭제
             * 2. 다음 세대원을 세대주로 변경
             * 3. TBT_OPR_DEVICE_REGIST, TBR_OPR_USER_DEVICE USER_ID 다음 세대원으로 변경
             * */

            // TODO: 1. TBD_USER_INVITE_GROUP 에서 기존 세대주 삭제
            params.setDelUserId(userId);
            if(memberMapper.deleteUserInviteGroup(params) <= 0){
                msg = "사용자(세대원) 강제탈퇴 실패";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            // TODO: 2. 다음 세대원 검색
            nextHouseholder = memberMapper.getNextUserId(params);
            params.setNextUserId(nextHouseholder.getUserId());

            // TODO: 3. TBT_OPR_DEVICE_REGIST USER_ID, HP 업데이트
            if(memberMapper.updateDeviceRegist(params) <= 0){
                msg = "사용자(세대원) 강제탈퇴 실패";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            // TODO: 4. TBR_OPR_USER_DEVICE USER_ID 업데이트
            if(memberMapper.updateUserDevice(params) <= 0){
                msg = "사용자(세대원) 강제탈퇴 실패";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }
            msg = "사용자(세대주) 탈퇴 성공";

            if(memberMapper.updatePushToken(params) <= 0) log.info("구글 FCM TOKEN 갱신 실패.");
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
                data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            msg = "홈IoT 서비스 회원 탈퇴 성공";

            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            log.info("data: " + data);
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
        String stringObject;
        try{

            if(deviceMapper.checkDeviceStatus(params).getDeviceId() == null) {
                msg = "홈 IoT 컨트롤러 인증 실패";
                stringObject = "N";
            }
            else{
                msg = "홈 IoT 컨트롤러 인증 성공";
                stringObject = "Y";
            }

            if(stringObject.equals("N")) result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
            else result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            log.info("result: " + result);
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
        MobiusResponse cntResult = null;
        MobiusResponse subResult = null;

        List<AuthServerDTO> groupMemberList;

        try{
            /*
            * 위 API 호출 마다 SerialNum, UserId 정보로 Cin 생성
            * 생성이 정상적으로 동작 후 201을 Return 한다면 해당 AE, CNT는 존재하므로 생성X
            * 하지만 201을 Return 하지 못하는 경우 AE, CNT가 없다 판단하여 신규 생성
            * TODO: GRP_ID 기준 모든 UserId에 대한 CNT, SUB 생성
            * */

            groupMemberList = memberMapper.getGroupMemberByUserId(userId);

            params.setSerialNumber("    " + params.getSerialNumber());
            params.setModelCode(" " + params.getModelCode());
            aeResult = mobiusService.createAe(common.stringToHex(params.getSerialNumber()));

            for(AuthServerDTO authServerDTO : groupMemberList){
                cntResult = mobiusService.createCnt(common.stringToHex(params.getSerialNumber()), authServerDTO.getUserId());
                subResult = mobiusService.createSub(common.stringToHex(params.getSerialNumber()), authServerDTO.getUserId(), "gw");
            }

            if(aeResult == null && cntResult == null && subResult == null){
                msg = "홈 IoT 최초 등록 인증 실패";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                new ResponseEntity<>(result, HttpStatus.OK);
            }
            else stringObject = "Y";

            if(stringObject.equals("Y")){
                result.setDeviceId("0.2.481.1.1." + common.stringToHex(params.getModelCode()) + "." + common.stringToHex(params.getSerialNumber()));
                msg = "최초 인증 성공";
            } else {
                msg = "최초 인증 실패";
            }

            if(memberMapper.updatePushToken(params) <= 0) log.info("구글 FCM TOKEN 갱신 실패.");

            result.setResult("Y".equalsIgnoreCase(stringObject) ?
                    ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);

            log.info("result: " + result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        }catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /** API 인증키 갱신 */
    @Override
    public ResponseEntity<?> doAccessTokenVerification(AuthServerDTO params)
            throws CustomException{

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject;
        String msg;
        String userId = params.getUserId();
        String token = params.getAccessToken();
        AuthServerDTO account;
        AuthServerDTO member;

        try{
            account = memberMapper.getAccountByUserId(userId);
            if (account == null) {
                msg = "계정이 존재하지 않습니다.";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1004, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            member = memberMapper.getUserByUserId(userId);
            if(member.getAccessToken().equals(token)){
                stringObject = "Y";
                result.setTokenVerify(stringObject);
            } else {
                stringObject = "N";
                result.setTokenVerify(stringObject);
            }

            msg = "API인증키 검증 성공";

            result.setResult(ApiResponse.ResponseType.HTTP_200, msg);

            log.info("result: " + result);
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
        try{

            /* *
             * TBT_OPR_DEVICE_REGIST - 임시 단말 등록 정보
             * TBR_OPR_USER_DEVICE - 사용자 단말 정보
             * TBR_OPR_DEVICE_DETAIL - 단말정보상세
             * 프로시져
             * */
            if(!memberMapper.deleteControllerMapping(params).equals("100")){
                msg = "홈 IoT 컨트롤러 삭제(회원 매핑 삭제) 실패";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                new ResponseEntity<>(data, HttpStatus.OK);
            } else stringObject = "Y";

            if(stringObject.equals("Y")) msg = "홈 IoT 컨트롤러 삭제(회원 매핑 삭제) 성공";
            else msg = "홈 IoT 컨트롤러 삭제(회원 매핑 삭제) 실패";

            if(memberMapper.updatePushToken(params) <= 0) log.info("구글 FCM TOKEN 갱신 실패.");

            data.setResult("Y".equalsIgnoreCase(stringObject) ?
                    ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1018, msg);

            log.info("data: " + data);
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
        List<AuthServerDTO> member;
        List<Map<String, String>> pushInfoList = new ArrayList<>();

        try{
            member = memberMapper.getPushInfoList(params);
            if (member == null) {
                msg = "계정이 존재하지 않습니다.";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1004, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            } else {
                for(AuthServerDTO authServerDTO : member){
                    Map<String, String> map = new HashMap<>();
                    map.put("pushIdx", authServerDTO.getPushIdx());
                    map.put("pushTitle", authServerDTO.getPushTitle());
                    map.put("pushContent", authServerDTO.getPushContent());
                    map.put("pushType", authServerDTO.getPushType());
                    map.put("pushDatetime", authServerDTO.getPushDatetime());
                    pushInfoList.add(map);
                }
            }

            msg = "스마트알림 - PUSH 이력 조회 성공";
            data.setPushInfo(pushInfoList);
            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            log.info("data: " + data);
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

        try {
            // deviceNickname Input 대신 newDeviceNickname을 받아서 Setter 사용
            params.setDeviceNickname(params.getNewDeviceNickname());

            if(deviceMapper.changeDeviceNickname(params) <= 0){
                msg = "기기 별칭 수정 실패";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                new ResponseEntity<>(data, HttpStatus.OK);
            }
            if(deviceMapper.changeDeviceNicknameTemp(params) <= 0){
                msg = "기기 별칭 수정 실패";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                new ResponseEntity<>(data, HttpStatus.OK);
            }else stringObject = "Y";

            if(stringObject.equals("Y")) msg = "기기 별칭 수정 성공";
            else msg = "기기 별칭 수정 실패";

            if(memberMapper.updatePushToken(params) <= 0) log.info("구글 FCM TOKEN 갱신 실패.");

            data.setResult("Y".equalsIgnoreCase(stringObject) ?
                    ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1018, msg);

            log.info("data: " + data);
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
        String userId;
        String deviceId = params.getDeviceId();
        String uuId = common.getTransactionId();
        String redisValue;
        AuthServerDTO firstDeviceUser;
        AuthServerDTO household;
        AuthServerDTO userNickname;

        Map<String, Object> conMap = new HashMap<>();
        Map<String, Object> conMap1 = new HashMap<>();
        DeviceStatusInfo.Device deviceInfo = new DeviceStatusInfo.Device();
        MobiusResponse mobiusResponse;
        ObjectMapper objectMapper = new ObjectMapper();
        String responseMessage;

        try{
            firstDeviceUser = memberMapper.getFirstDeviceUser(deviceId);
            userId = firstDeviceUser.getUserId();

            serialNumber = deviceMapper.getSingleSerialNumberBydeviceId(params.getDeviceId());
            if(serialNumber.getSerialNumber() == null){
                msg = "기기 밝기 조절 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1008, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }
            System.out.println("params.getBrightnessLevel(): " + params.getBrightnessLevel());

            conMap.put("controlAuthKey", params.getControlAuthKey());
            conMap.put("deviceId", params.getDeviceId());
            conMap.put("deviceType", params.getDeviceType());
            conMap.put("modelCode", params.getModelCode());
            conMap.put("brightnessLevel", params.getBrightnessLevel());
            conMap.put("functionId", "blCf");
            conMap.put("uuId", uuId);

            redisValue = params.getUserId() + "," + "blCf";
            redisCommand.setValues(uuId, redisValue);

            String jsonString = objectMapper.writeValueAsString(conMap);
            mobiusResponse = mobiusService.createCin(common.stringToHex("    " + serialNumber.getSerialNumber()), userId, jsonString);
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
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1015, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);

                } else if(!responseMessage.equals("0")) {
                    msg = "기기 밝기 수정 실패";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1014, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }
                } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                log.error("", e);
            }

            gwMessagingSystem.removeMessageQueue("blCf" + uuId);
            redisCommand.deleteValues(uuId);

            if(memberMapper.updatePushToken(params) <= 0) log.info("구글 FCM TOKEN 갱신 실패.");

            conMap1.put("body", "Brightness Control OK");
            msg = "기기 밝기 수정 성공";
            result.setResult(ApiResponse.ResponseType.HTTP_200, msg);

            deviceInfo.setBlCf(params.getBrightnessLevel());
            deviceInfo.setDeviceId(deviceId);
            deviceMapper.updateDeviceStatusFromApplication(deviceInfo);

            household = memberMapper.getHouseholdByUserId(params.getUserId());
            params.setGroupId(household.getGroupId());
            List<AuthServerDTO> userIds = memberMapper.getUserIdsByDeviceId(params);
            List<AuthServerDTO> pushYnList = memberMapper.getPushYnStatusByUserIds(userIds);
            userNickname = memberMapper.getUserNickname(params.getUserId());
            userNickname.setUserNickname(common.stringToHex(userNickname.getUserNickname()));

            for(int i = 0; i < userIds.size(); ++i){
                log.info("쿼리한 UserId: " + userIds.get(i).getUserId());
                conMap1.put("pushYn", pushYnList.get(i).getFPushYn());
                conMap1.put("modelCode", common.getModelCodeFromDeviceId(deviceId));
                conMap1.put("targetToken", memberMapper.getPushTokenByUserId(userIds.get(i).getUserId()).getPushToken());
                conMap1.put("userNickname", userNickname.getUserNickname());
                conMap1.put("deviceNick", common.returnDeviceNickname(deviceId));
                conMap1.put("title", "blCf");
                conMap1.put("id", "Brightness Control ID");
                conMap1.put("isEnd", "false");

                String jsonString1 = objectMapper.writeValueAsString(conMap1);
                log.info("jsonString: " + jsonString);

                if(!mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString1).getResponseCode().equals("201"))
                    log.info("PUSH 메세지 전송 오류");
            }

            params.setCodeType("1");
            params.setCommandId("blCf");
            params.setControlCode("blCf");
            params.setControlCodeName("밝기 설정");
            params.setCommandFlow("0");
            params.setDeviceId(deviceId);
            params.setUserId(params.getUserId());

            if(memberMapper.insertCommandHistory(params) <= 0) log.info("DB_ERROR 잠시 후 다시 시도 해주십시오.");

            params.setPushTitle("기기제어");
            params.setPushContent("밝기 설정");
            params.setDeviceType("01");
            if(memberMapper.insertPushHistory(params) <= 0) log.info("PUSH HISTORY INSERT ERROR");

            log.info("result: " + result);
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
        String msg;
        List<AuthServerDTO> notice;
        List<Map<String, String>> noticeList = new ArrayList<>();
        try {
            notice = memberMapper.getNoticeList();
            if(notice.isEmpty()) {
                msg = "공지사항 목록이 없습니다.";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }
            else {
                for(AuthServerDTO authServerDTO : notice){
                    Map<String, String> map = new HashMap<>();
                    map.put("noticeIdx", authServerDTO.getNoticeIdx());
                    map.put("noticeTitle", authServerDTO.getNoticeTitle());
                    map.put("noticeContent", authServerDTO.getNoticeContent());
                    map.put("noticeType", authServerDTO.getNoticeType());
                    map.put("noticeStartDate", authServerDTO.getNoticeStartDate());
                    map.put("noticeEndDate", authServerDTO.getNoticeEndDate());
                    noticeList.add(map);
                }
                data.setNoticeInfo(noticeList);
            }

            msg = "공지사항 조회 성공";

            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            log.info("data: " + data);
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

        try {

            if(memberMapper.updateDeviceLocationNicknameDeviceDetail(params) <= 0){
                msg = "기기 설치 위치 별칭 수정 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                new ResponseEntity<>(result, HttpStatus.OK);
            } else stringObject = "Y";
            if(memberMapper.updateDeviceLocationNicknameDeviceRegist(params) <= 0){
                msg = "기기 설치 위치 별칭 수정 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                new ResponseEntity<>(result, HttpStatus.OK);
            } else stringObject = "Y";

            if(stringObject.equals("Y")) msg = "기기 설치 위치 별칭 수정 성공";
            else msg = "기기 설치 위치 별칭 수정 실패";

            result.setResult(ApiResponse.ResponseType.HTTP_200, msg);

            if(memberMapper.updatePushToken(params) <= 0) log.info("구글 FCM TOKEN 갱신 실패.");

            log.info("result: " + result);
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
            log.info("result: " + result);
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

            if(memberMapper.InsertSafeAlarmSet(params) <= 0){
                msg = "안전안심 알람 설정 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                new ResponseEntity<>(result, HttpStatus.OK);
            } else stringObject = "Y";

            if(stringObject.equals("Y"))
                msg = "안전안심 알람 설정 성공";
            else
                msg = "안전안심 알람 설정 실패";

            result.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200
                    : ApiResponse.ResponseType.CUSTOM_1018, msg);

            if(memberMapper.updatePushToken(params) <= 0) log.info("구글 FCM TOKEN 갱신 실패.");

            log.info("result: " + result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /** 빠른온수 예약 정보 조회 */
    @Override
    public ResponseEntity<?> doGetFastHotWaterInfo(AuthServerDTO params) throws Exception {
        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject = "N";
        String msg;
        String deviceId = params.getDeviceId();

        AuthServerDTO fwhInfo;
        try {
            fwhInfo = memberMapper.getFwhInfo(deviceId);
            if(fwhInfo == null){
                msg = "빠른온수 예약 정보 없음";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                new ResponseEntity<>(result, HttpStatus.OK);
            } else {
                result.setAwakeList(fwhInfo.getFastHotWater());
                stringObject = "Y";
            }

            if(stringObject.equals("Y"))
                msg = "빠른온수 예약 정보 조회 성공";
            else
                msg = "빠른온수 예약 정보 조회 실패";

            result.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200
                    : ApiResponse.ResponseType.CUSTOM_1018, msg);

            log.info("result: " + result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }
}
