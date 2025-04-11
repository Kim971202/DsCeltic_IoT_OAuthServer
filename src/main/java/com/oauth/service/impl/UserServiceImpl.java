package com.oauth.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oauth.constants.MobiusResponse;
import com.oauth.dto.AuthServerDTO;
import com.oauth.dto.gw.*;
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

    /**
     * 회원 로그인
     */
    @Override
    public ResponseEntity<?> doLogin(String userId, String userPassword, String phoneId, String pushToken)
            throws CustomException {

        String userNickname;
        String password;
        String registUserType;
        ApiResponse.Data result = new ApiResponse.Data();

        String msg;
        String token;
        String hp;

        AuthServerDTO params = new AuthServerDTO();
        AuthServerDTO householdStatus;
        AuthServerDTO phoneIdInfo;
        AuthServerDTO info = new AuthServerDTO();
        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();

        try {

            AuthServerDTO account = memberMapper.getAccountByUserId(userId);
            if (account == null) {
                msg = "계정이 존재하지 않습니다.";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1016, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else {
                registUserType = account.getRegistUserType();
                password = account.getUserPassword();
                if (!encoder.matches(userPassword, password)) {
                    msg = "PW 에러";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }
            }

            householdStatus = memberMapper.getHouseholdByUserId(userId);
            AuthServerDTO member = memberMapper.getUserByUserId(userId);
            if (member == null) {
                msg = "계정이 존재하지 않습니다.";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1016, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else
                userNickname = member.getUserNickname();

            if (memberMapper.getDeviceIdFromDeviceGroup(householdStatus.getGroupId()).getDeviceCount().equals("0"))
                result.setDeviceInfo("N");
            else
                result.setDeviceInfo("Y");

            token = common.createJwtToken(userId, "NORMAL", "Login");
            result.setRegistUserType(registUserType);
            result.setAccessToken(token);
            result.setUserNickname(userNickname);

            params.setUserId(userId);

            /*
             * 요청받은 PUSH_TOKEN 기준 같은 TOKEN 가진 사용자의 로그인 상태를 N으로 수정 한다.
             * 1. 동일한 PUSH_TOKEN을 가진 USER_ID(요청자 제외) 쿼리 TBR_OPR_ACCOUNT
             * 2. 이후 TBR_OPR_USER에 로그인 상태 값을 N으로 수정
             * */
            List<AuthServerDTO> authServerDTOList = memberMapper.getUserIdListByPushToken(params);
            if(!authServerDTOList.isEmpty()){
                memberMapper.updateLoginStatusByUserIdList(authServerDTOList);
            }

            if (phoneId != null) {
                // 로그인 시도 하는 사용자가 LogOut 상태일 경우 아래 로직 적용 X
                if (memberMapper.getUserLoginoutStatus(userId).getLoginoutStatus().equals("Y")) {
                    // phoneId의 값이 DEFAULT인 경우 최초 로그인 이므로 PASS
                    phoneIdInfo = memberMapper.getPhoneIdInfo(userId);
                    if (!phoneIdInfo.getPhoneId().equals("DEFAULT")) {
                        // phoneId의 값이 입력 받은 쿼리한 phoneId와 다른 경우 PUSH 전송
                        if (!phoneId.equals(phoneIdInfo.getPhoneId())) {
                            conMap.put("targetToken", memberMapper.getPushTokenByUserId(userId).getPushToken());
                            conMap.put("title", "Duplicated_Login");
                            String jsonString = objectMapper.writeValueAsString(conMap);
                            mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);
                        }
                    }
                }
                params.setPhoneId(phoneId);
                memberMapper.updatePhoneId(params);
            }

            // TBR_OPR_USER 테이블의 USER_STATUS_LOG_INOUT의 값을 Y
            info.setUserId(userId);
            info.setLoginoutStatus("Y");
            memberMapper.updateLoginoutStatus(info);

            msg = "로그인 성공";

            hp = memberMapper.getHpByUserId(userId).getHp();
            if (hp == null) {
                msg = "계정이 존재하지 않습니다.";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1016, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else
                result.setHp(hp);

            params.setAccessToken(token);
            params.setPushToken(pushToken);

            memberMapper.updatePushToken(params);
            memberMapper.updateLoginDatetime(params);

            result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            log.info("result: {} ", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 회원 로그아웃
     */
    @Override
    public ResponseEntity<?> doLogout(String userId) throws CustomException {
        ApiResponse.Data result = new ApiResponse.Data();
        String msg;
        AuthServerDTO info = new AuthServerDTO();
        try {

            info.setUserId(userId);
            info.setLoginoutStatus("N");

            if (memberMapper.updateLoginoutStatus(info) <= 0) {
                msg = "회원 로그아웃 실패.";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            msg = "회원 로그아웃 성공";

            result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            log.info("result: {} ", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 회원가입
     */
    @Override
    public ResponseEntity<?> doRegist(AuthServerDTO params) throws CustomException {

        ApiResponse.Data data = new ApiResponse.Data();
        String userPassword = params.getUserPassword();
        String msg;
        String token;
        String userId = params.getUserId();

        try {
            userPassword = encoder.encode(userPassword);
            params.setUserPassword(userPassword);

            params.setNewHp(params.getHp());
            if (!memberMapper.checkDuplicateHp(params.getNewHp()).getHpCount().equals("0")) {
                msg = "증복 전화번호 입력.";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1007, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            if (memberMapper.insertAccount(params) <= 0) {
                msg = "회원가입 실패";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            if (memberMapper.insertMember(params) <= 0) {
                msg = "회원가입 실패";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            if (memberMapper.insertHouseholder(params) <= 0) {
                msg = "회원가입 실패";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            msg = "회원가입 성공";

            token = common.createJwtToken(userId, "NORMAL", "Regist");
            data.setAccessToken(token);
            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            log.info("data: {} ", data);
            return new ResponseEntity<>(data, HttpStatus.OK);
        } catch (CustomException e) {
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            log.error("", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 회원중복 체크
     */
    @Override
    public ResponseEntity<?> doDuplicationCheck(AuthServerDTO params) throws CustomException {

        ApiResponse.Data data = new ApiResponse.Data();
        String stringObject;
        String userId = params.getUserId();
        String msg;

        try {
            AuthServerDTO member = memberMapper.getUserByUserId(userId);

            if (member == null)
                stringObject = "N";
            else
                stringObject = "Y";

            if (stringObject.equals("Y")) {
                msg = "중복 되는 ID";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1001, msg);
            } else {
                msg = "중복 되지 않는 ID";
                data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            }

            data.setDuplicationYn(stringObject);
            log.info("data: {} ", data);
            return new ResponseEntity<>(data, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * ID 찾기
     */
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
                data.setResult(ApiResponse.ResponseType.CUSTOM_1016, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            } else
                userId = Common.extractJson(member.toString(), "userId");

            msg = "ID 찾기 성공";

            data.setUserIdList(userId);
            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            log.info("data: {} ", data);
            return new ResponseEntity<>(data, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 비밀번호 찾기
     */
    @Override
    public ResponseEntity<?> doResetPassword(AuthServerDTO params) throws CustomException {

        ApiResponse.Data data = new ApiResponse.Data();
        String stringObject;
        AuthServerDTO member;
        String msg;

        try {

            member = memberMapper.getUserByUserIdAndHp(params);
            if (member == null)
                stringObject = "N";
            else {
                data.setRegistUserType(member.getRegistUserType());
                stringObject = "Y";
            }

            if (stringObject.equals("Y"))
                msg = "비밀번호 찾기 - 초기화 성공";
            else
                msg = "비밀번호 찾기 - 초기화 실패";

            data.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200
                    : ApiResponse.ResponseType.CUSTOM_1018, msg);

            log.info("data: {}", data);
            return new ResponseEntity<>(data, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 비밀번호 변경
     */
    @Override
    public ResponseEntity<?> doChangePassword(AuthServerDTO params) throws CustomException {

        ApiResponse.Data data = new ApiResponse.Data();
        String stringObject = "N";
        String msg;

        try {
            params.setNewPassword(encoder.encode(params.getUserPassword()));
            if (memberMapper.updatePassword(params) <= 0) {
                msg = "비밀번호 변경 - 생성 실패";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                new ResponseEntity<>(data, HttpStatus.OK);
            } else
                stringObject = "Y";

            if (stringObject.equals("Y"))
                msg = "비밀번호 변경 - 생성 성공";
            else
                msg = "비밀번호 변경 - 생성 실패";

            data.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200
                    : ApiResponse.ResponseType.CUSTOM_1018, msg);

            log.info("data: {} ", data);
            return new ResponseEntity<>(data, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 사용자정보 조회
     */
    @Override
    public ResponseEntity<?> doSearch(AuthServerDTO params) throws CustomException {

        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        String userId = params.getUserId();

        try {
            AuthServerDTO member = memberMapper.getUserByUserId(userId);

            if (member == null) {
                msg = "사용자정보가 없습니다.";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1016, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            } else {
                data.setUserId(member.getUserId());
                data.setUserNickname(member.getUserNickname());
                data.setHp(member.getHp());
                data.setHouseholder(member.getHouseholder());
                msg = "사용자정보 조회 성공";
            }

            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            log.info("data: {} ",  data);
            return new ResponseEntity<>(data, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 사용자 그룹 정보 조회
     */
    @Override
    public ResponseEntity<?> doSearchGroupInfo(AuthServerDTO params) throws CustomException {

        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        List<AuthServerDTO> groupInfo;
        List<Map<String, Object>> groupInfoList = new ArrayList<>();
        try {
            groupInfo = memberMapper.getMemberByGroupIdxList(params);
            if (groupInfo == null || groupInfo.isEmpty()) {
                msg = "그룹 정보가 없습니다.";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1016, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }
            for (AuthServerDTO authServerDTO : groupInfo) {
                Map<String, Object> map = new HashMap<>();
                map.put("groupIdx", authServerDTO.getGroupIdx());
                map.put("groupName", authServerDTO.getGroupName());
                groupInfoList.add(map);
            }
            msg = "그룹 정보 조회 성공";
            data.setGroupInfo(groupInfoList);
            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            return new ResponseEntity<>(data, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 사용자 그룹 삭제
     */
    @Override
    public ResponseEntity<?> doDeleteGroup(AuthServerDTO params) throws CustomException {
        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        String groupIdx = params.getGroupIdx();
        List<AuthServerDTO> deviceIdList;

        try {
            /*
             * *
             * 기기 삭제를 위한 필요한 정보 (기기ID, 사용자ID)
             * 1. groupIdx기준 해당 그룹에 있는 기기ID 쿼리 - getDeviceIdByGroupIdx
             * 2. 기기삭제 정보 쿼리 getCheckedDeviceExist
             * 3. 기기 삭제 deleteControllerMapping
             * 4. 그룹 삭제
             */

            // 1
            deviceIdList = deviceMapper.getDeviceIdByGroupIdx(groupIdx);

            // 2
            for (AuthServerDTO authServerDTO1 : deviceIdList) {
                List<AuthServerDTO> authServerDTOList = deviceMapper.getCheckedDeviceExist(authServerDTO1.getDeviceId());
                // 3
                for (AuthServerDTO authServerDTO2 : authServerDTOList) {
                    memberMapper.deleteControllerMapping(authServerDTO2);
                }
            }

            // 4
            memberMapper.deleteUserInviteGroupByGroupIdx(groupIdx);

            // 5 그룹Idx 기준으로 TBR_OPR_USER_INVITE_STATUS에서 삭제
            if (memberMapper.deleteInviteStatusByGroupIdx(groupIdx) <= 0) {
                msg = "그룹 정보 삭제 실패";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                new ResponseEntity<>(data, HttpStatus.OK);
            }

            msg = "그룹 정보 삭제 성공";
            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            log.info("data: {} ", data);
            return new ResponseEntity<>(data, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 사용자 그룹 명칭 변경
     */
    @Override
    public ResponseEntity<?> doChangeGroupName(AuthServerDTO params) throws CustomException {
        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        String groupIdx = params.getGroupIdx();
        AuthServerDTO duplicateResult;

        try {

            // groupName 중복 여부 확인
            duplicateResult = memberMapper.checkDuplicateGroupName(params);
            if (duplicateResult.getGroupNameCount().equals("1")) {
                msg = "그룹 명칭 중복";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1019, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }
            if (deviceMapper.updateGroupName(params) <= 0) {
                msg = "그룹 명칭 변경 실패";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1019, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            if(!deviceMapper.checkDeviceCount(groupIdx).getDeviceCount().equals("0")){
                deviceMapper.updateDeviceRegistGroupName(params);
            }

            msg = "사용자 그룹 명칭 변경 성공";
            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);

            log.info("data: {}", data);
            return new ResponseEntity<>(data, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);

        }
    }

    /**
     * 사용자 그룹 생성
     */
    @Override
    public ResponseEntity<?> doCreateNewGroup(AuthServerDTO params) throws CustomException {
        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        String userId = params.getUserId();
        AuthServerDTO duplicateResult;
        try {
            // groupName 중복 여부 확인
            duplicateResult = memberMapper.checkDuplicateGroupName(params);
            if (Integer.parseInt(duplicateResult.getGroupNameCount()) >= 1) {
                msg = "그룹 명칭 중복";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1019, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            params.setGroupId(userId);

            if (memberMapper.insertInviteGroup(params) <= 0) {
                msg = "사용자 그룹 명칭 변경 실패.";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }
            if (memberMapper.updateInviteGroup(params) <= 0) {
                msg = "사용자 그룹 명칭 변경 실패.";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            msg = "사용자 그룹 생성 성공";

            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            log.info("data: {}", data);
            return new ResponseEntity<>(data, HttpStatus.OK);
        } catch (Exception e) {
            log.error("error", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 회원 별칭(이름) 및 전화번호 변경
     */
    @Override
    public ResponseEntity<?> doUpdateUserNicknameHp(AuthServerDTO params) throws CustomException {

        ApiResponse.Data data = new ApiResponse.Data();
        String msg = null;

        try {
            // 신규 전화번호 가 본인 번호 인지 확인
            if (memberMapper.checkDuplicateHpByUserId(params).getHpCount().equals("1")) {
                if (memberMapper.updateHp(params) <= 0) {
                    msg = "회원 별칭(이름) 및 전화번호 변경 실패.";
                    data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(data, HttpStatus.OK);
                }
                // 신규 전화번호 중복 확인
            } else if (memberMapper.checkDuplicateHp(params.getNewHp()).getHpCount().equals("0")) {
                if (memberMapper.updateHp(params) <= 0) {
                    msg = "회원 별칭(이름) 및 전화번호 변경 실패.";
                    data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(data, HttpStatus.OK);
                }
            } else {
                msg = "회원 별칭(이름) 및 전화번호 변경 실패.";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1007, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            if (memberMapper.updateGrpNick(params) <= 0) {
                msg = "회원 별칭(이름) 및 전화번호 변경 실패.";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            if (memberMapper.updateUserNickname(params) <= 0) {
                msg = "회원 별칭(이름) 및 전화번호 변경 실패.";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            log.info("data: {} ", data);
            return new ResponseEntity<>(data, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 비밀번호 변경 - 로그인시
     */
    @Override
    public ResponseEntity<?> doUpdatePassword(AuthServerDTO params) throws CustomException {

        ApiResponse.Data data = new ApiResponse.Data();
        String stringObject = "N";
        String oldPassword = params.getOldPassword();
        String userId = params.getUserId();
        String msg;

        try {
            AuthServerDTO account = memberMapper.getAccountByUserId(userId);

            if (account == null) {
                msg = "계정이 존재하지 않습니다.";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1016, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }
            params.setNewPassword(encoder.encode(params.getNewPassword()));

            if (!encoder.matches(oldPassword, account.getUserPassword())) {
                msg = "PW 에러";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            if (memberMapper.updatePassword(params) <= 0) {
                msg = "비밀번호 변경 - 로그인시 실패";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                new ResponseEntity<>(data, HttpStatus.OK);
            } else
                stringObject = "Y";

            if (stringObject.equals("Y"))
                msg = "비밀번호 변경 - 로그인시 성공";
            else
                msg = "비밀번호 변경 - 로그인시 실패";

            data.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200
                    : ApiResponse.ResponseType.CUSTOM_1018, msg);

            log.info("data: {}", data);
            return new ResponseEntity<>(data, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 사용자(세대원) 정보 조회
     */
    @Override
    public ResponseEntity<?> doViewHouseholdMemebers(AuthServerDTO params) throws CustomException {

        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        String groupIdx = params.getGroupIdxList();
        List<AuthServerDTO> userIdList;
        List<String> groupIdxList;
        List<HashMap<String, String>> user = new ArrayList<>();
        try {

            groupIdxList = Arrays.asList(groupIdx.split(","));

            userIdList = memberMapper.getFamilyMemberByGroupIdxList(groupIdxList);

            if (userIdList != null) {
                for (AuthServerDTO authServerDTO : userIdList) {
                    HashMap<String, String> userMap = new HashMap<>();
                    String household = "N";
                    if (authServerDTO.getUserId().equals(authServerDTO.getGroupId())) household = "Y";
                    userMap.put("userId", authServerDTO.getUserId());
                    userMap.put("userNickname", authServerDTO.getUserNickname());
                    userMap.put("householder", household);
                    userMap.put("groupName", authServerDTO.getGroupName());
                    userMap.put("groupId", authServerDTO.getGroupId());
                    userMap.put("groupIdx", authServerDTO.getGroupIdx());
                    user.add(userMap); // 리스트에 HashMap 추가
                }
            } else {
                msg = "계정이 존재하지 않습니다.";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1016, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            msg = "사용자(세대원) 정보 조회 성공";

            data.setUser(user);
            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            log.info("data: {}", data);
            return new ResponseEntity<>(data, HttpStatus.OK);
        } catch (CustomException e) {
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 사용자 추가 - 초대
     */
    @Override
    public ResponseEntity<?> doAddUser(AuthServerDTO params)
            throws CustomException {

        String stringObject = "N";
        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        String responseHp = params.getResponseHp();
        AuthServerDTO userId;
        AuthServerDTO userNickname;
        AuthServerDTO pushToken;
        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            userId = memberMapper.getUserIdByHp(responseHp);
            if(userId == null){
                msg = "사용자 추가 - 초대 실패";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            // 전화번호로 responseUserId를 쿼리하는 쿼리문 추가
            params.setResponseUserId(userId.getUserId());

            // 동일한 사용자가 동일한 그룹에 동일한 사용자의 수락 여부가 D인 경우에 초대할 경우 차단한다.
            if (Integer.parseInt(memberMapper.getInviteCountFromInviteStatus(params).getInviteCount()) > 0) {
                msg = "이미 초대한 사용자 입니다.";
                data.setResult(ApiResponse.ResponseType.CUSTOM_2002, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            // 이미 그룹에 존재하는 사용자를 초대할경우 차단 한다.
            if (Integer.parseInt(memberMapper.getInviteCountByReqeustResponseUserId(params).getInviteCount()) > 0) {
                msg = "현재 그룹 세대원 입니다.";
                data.setResult(ApiResponse.ResponseType.CUSTOM_2003, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            // 동일한 사용자가 동일한 사용자에게 5회 이상 초대를 보낼경우 차단한다. (사용자 삭제 시 초기화 됨)
            if (Integer.parseInt(memberMapper.getInviteCount(params).getInviteCount()) <= 6) {
                params.setUserId(params.getRequestUserId());

                userNickname = memberMapper.getUserNickname(params.getRequestUserId());
                userNickname.setUserNickname(common.stringToHex(userNickname.getUserNickname()));

                pushToken = memberMapper.getPushTokenByUserId(params.getResponseUserId());

                if (pushToken == null) {
                    msg = "PUSH TOKEN NULL";
                    data.setResult(ApiResponse.ResponseType.CUSTOM_1016, msg);
                    return new ResponseEntity<>(data, HttpStatus.OK);
                }
                params.setUserId(params.getResponseUserId());

                if (memberMapper.inviteHouseMember(params) <= 0) {
                    msg = "사용자 추가 - 초대 실패";
                    data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    new ResponseEntity<>(data, HttpStatus.OK);
                } else {
                    stringObject = "Y";
                }

                if (stringObject.equals("Y")){
                    msg = "사용자 추가 - 초대 성공";
                } else {
                    msg = "사용자 추가 - 초대 실패";
                }

                if (memberMapper.getUserLoginoutStatus(params.getUserId()).getLoginoutStatus().equals("Y")) {
                    conMap.put("targetToken", pushToken.getPushToken());
                    conMap.put("title", "adUr");
                    conMap.put("body", "adUr");
                    conMap.put("userNickname", userNickname.getUserNickname());
                    conMap.put("pushYn", "Y");
                    String jsonString = objectMapper.writeValueAsString(conMap);
                    mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);
                }

                data.setResult("Y".equalsIgnoreCase(stringObject)
                        ? ApiResponse.ResponseType.HTTP_200
                        : ApiResponse.ResponseType.CUSTOM_1018, msg);

                log.info("data: {} ", data);
                return new ResponseEntity<>(data, HttpStatus.OK);
            } else {
                msg = "초대 횟수 초과";
                data.setResult(ApiResponse.ResponseType.CUSTOM_2001, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 사용자 초대 - 수락여부
     */
    @Override
    public ResponseEntity<?> doInviteStatus(AuthServerDTO params)
            throws CustomException {

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

            // 사용자 초대 시 기기 없는 경우 예외 처리
            if(deviceIdList.isEmpty()){
                msg = "사용자 초대 - 수락 실패";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }
            if (inviteAcceptYn.equals("Y")) {

                /*
                 * 1. 신규 세대원을 TBD_USER_INVITE_GROUP 테이블에 추가
                 * 2. 신규 세대원 관련 PUSH Message Info 등록
                 */
                if (memberMapper.insertInviteGroupMember(params) <= 0) {
                    msg = "사용자 초대 - 수락 실패";
                    data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(data, HttpStatus.OK);
                }
                params.setUserId(responseUserId);

                inputList = new ArrayList<>();
                for (AuthServerDTO authServerDTO : deviceIdList) {
                    AuthServerDTO newDevice = new AuthServerDTO();
                    newDevice.setDeviceId(authServerDTO.getDeviceId());
                    newDevice.setUserId(responseUserId);
                    newDevice.setHp(responseHp);

                    // 리스트에 추가
                    inputList.add(newDevice);
                }
                if (memberMapper.insertUserDevicePushByList(inputList) <= 0) {
                    msg = "사용자 초대 - 수락 실패";
                    data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(data, HttpStatus.OK);
                }

                inputList = new ArrayList<>();
                for (AuthServerDTO authServerDTO : deviceIdList) {
                    AuthServerDTO newDevice = new AuthServerDTO();
                    newDevice.setDeviceId(authServerDTO.getDeviceId());
                    newDevice.setUserId(responseUserId);

                    // 리스트에 추가
                    inputList.add(newDevice);
                }

                if (deviceMapper.insertDeviceListGrpInfo(inputList) <= 0) {
                    msg = "사용자 초대 - 수락 실패";
                    data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(data, HttpStatus.OK);
                }
                if (memberMapper.acceptInvite(params) <= 0) {
                    msg = "사용자 초대 - 수락 실패";
                    data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(data, HttpStatus.OK);
                }
            } else if (inviteAcceptYn.equals("N")) {

                if (memberMapper.acceptInvite(params) <= 0) {
                    msg = "사용자 초대 - 수락 실패";
                    data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(data, HttpStatus.OK);
                }
            } else {
                msg = "예기치 못한 오류로 인해 서버에 연결할 수 없습니다";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            conMap.put("body", "Accept Invite OK");
            msg = "사용자 초대 - 수락여부 성공";

            // params에 userId 추가
            params.setUserId(params.getResponseUserId());

            pushToken = memberMapper.getPushTokenByUserId(requestUserId);

            userNickname = memberMapper.getUserNickname(requestUserId);
            userNickname.setUserNickname(common.stringToHex(userNickname.getUserNickname()));
            if (memberMapper.getUserLoginoutStatus(requestUserId).getLoginoutStatus().equals("Y")){
                conMap.put("pushYn", "Y");
                conMap.put("targetToken", pushToken.getPushToken());
                conMap.put("userNickname", userNickname.getUserNickname());
                conMap.put("title", "acIv"); // PUSH TITLE
                conMap.put("acIv", inviteAcceptYn); // PUSH CONTENT
                conMap.put("id", "Accept Invite ID");
                conMap.put("isEnd", "false");
                String jsonString = objectMapper.writeValueAsString(conMap);
                mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);
            }

            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            log.info("data: {}", data);
            return new ResponseEntity<>(data, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 사용자 초대 - 목록 조회
     */
    @Override
    public ResponseEntity<?> doInviteListView(AuthServerDTO params)
            throws CustomException {

        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        String userId = params.getUserId();

        List<Map<String, String>> invitationList = new ArrayList<>();
        List<AuthServerDTO> invitationInfo;
        try {

            invitationInfo = memberMapper.getInvitationList(userId);
            if (invitationInfo.isEmpty()) {
                msg = "사용자 초대 이력이 없습니다.";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1016, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            for (AuthServerDTO authServerDTO : invitationInfo) {
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
            log.info("data: {}", data);
            return new ResponseEntity<>(data, HttpStatus.OK);
        } catch (CustomException e) {
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 사용자(세대원) - 그룹 탈퇴
     */
    @Override
    public ResponseEntity<?> doDelHouseholdMembers(AuthServerDTO params)
            throws CustomException {

        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        String delUserId = params.getDelUserId();
        String groupIdx = params.getGroupIdx();
        List<AuthServerDTO> deviceIdList;
        List<AuthServerDTO> inputList;
        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();

        try {

            memberMapper.delHouseholdMember(delUserId);
            // TBR_IOT_DEVICE_GRP_INFO deleteDeviceGrpInfo
            // 세대주 ID로 세대주 등록 기기 목록을 Regist 테이블에서 불러온후 해당 기기를 세대원
            // TBR_IOT_DEVICE_GRP_INFO 테이블에서 삭제
            deviceIdList = memberMapper.getDeviceIdFromRegist(params);

            if (!deviceIdList.isEmpty()) {
                inputList = new ArrayList<>();
                for (AuthServerDTO authServerDTO : deviceIdList) {
                    AuthServerDTO newDevice = new AuthServerDTO();
                    newDevice.setDeviceId(authServerDTO.getDeviceId());
                    newDevice.setUserId(delUserId);
                    // 리스트에 추가
                    inputList.add(newDevice);
                }

                // TBR_OPR_USER_DEVICE_PUSH 테이블 삭제
                memberMapper.deleteUserDevicePush(inputList);
                memberMapper.deleteDeviceGrpInfo(inputList);
            }

            // TBD_USER_INVITE_GROUP 테이블 삭제
            memberMapper.deleteUserInviteGroup(params);
            memberMapper.deleteInviteStatusByHouseholdMembers(params);
            msg = "사용자(세대원) - 강제탈퇴 성공";

            if (memberMapper.getUserLoginoutStatus(delUserId).getLoginoutStatus().equals("Y")){
                conMap.put("targetToken", memberMapper.getPushTokenByUserId(delUserId).getPushToken());
                conMap.put("title", "Delete_Member");
                conMap.put("groupName", common.stringToHex(memberMapper.getGroupNameByGroupIdx(groupIdx).getGroupName()));
                String jsonString = objectMapper.writeValueAsString(conMap);
                mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);
            }

            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            log.info("data: {}", data);
            return new ResponseEntity<>(data, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.OK);
        }
    }

    /**
     * 홈 IoT 컨트롤러 알림 설정
     */
    @Override
    public ResponseEntity<?> doPushSet(AuthServerDTO params)
            throws CustomException {

        /*
         * *
         * -------------------------
         * 전체 제어의 경우
         * PushYn: "Y,Y" OR "N,N"
         * PushCode: "1,2"
         * -------------------------
         * 단건의 전체 제어
         * PushYn: "Y,Y" OR "N,N"
         * PushCode: "1,2"
         * -------------------------
         * 단건 제어
         * PushYn: "Y" OR "N"
         * PushYn: "Y" OR "N"
         * PushCode: "1" OR "2"
         * -------------------------
         */

        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        String userId = params.getUserId();
        List<String> pushCode = params.getPushCd();
        List<String> pushYn = params.getPushYn();
        List<String> deviceIdList = params.getDeviceIdList();
        List<AuthServerDTO> inputList = new ArrayList<>();

        try {
            if (deviceIdList.size() > 1) {
                // 전체 제어
                for (String deviceId : deviceIdList) {
                    AuthServerDTO pushInfo = new AuthServerDTO();
                    // 새로운 AuthServerDTO 객체 생성
                    pushInfo.setUserId(userId);
                    pushInfo.setDeviceId(deviceId);
                    pushInfo.setFPushYn(pushYn.get(0));
                    pushInfo.setSPushYn(pushYn.get(1));
                    // 리스트에 추가
                    inputList.add(pushInfo);
                }
            } else if (pushCode.size() == 1) {
                AuthServerDTO pushInfo = new AuthServerDTO();
                // 단건
                pushInfo.setUserId(userId);
                pushInfo.setDeviceId(deviceIdList.get(0));
                if (pushCode.get(0).equals("01"))
                    pushInfo.setFPushYn(pushYn.get(0));
                else
                    pushInfo.setSPushYn(pushYn.get(0));

                if (memberMapper.updatePushCodeStatusSingle(pushInfo) > 0) {
                    msg = "기기 알림 설정 성공";
                    data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                    log.info("data: {}", data);
                    return new ResponseEntity<>(data, HttpStatus.OK);
                }
            } else {
                AuthServerDTO pushInfo = new AuthServerDTO();
                // 단건의 전체
                pushInfo.setUserId(userId);
                pushInfo.setDeviceId(deviceIdList.get(0));
                pushInfo.setFPushYn(pushYn.get(0));
                pushInfo.setSPushYn(pushYn.get(1));
                // 리스트에 추가
                inputList.add(pushInfo);
            }

            if (memberMapper.updatePushCodeStatus(inputList) <= 0)
                log.info("기기 알림 설정 실패");

            msg = "기기 알림 설정 성공";

            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            log.info("data: {}", data);
            return new ResponseEntity<>(data, HttpStatus.OK);
        } catch (CustomException e) {
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 홈 IoT 컨트롤러 알림 정보 조회
     */
    @Override
    public HashMap<String, Object> doSearchPushSet(AuthServerDTO params)
            throws CustomException {

        String userId = params.getUserId();
        String householderId;
        HashMap<String, Object> resultMap = new LinkedHashMap<String, Object>();
        List<AuthServerDTO> deviceIdList;
        List<AuthServerDTO> groupInfoList;
        AuthServerDTO pushCode;
        AuthServerDTO groupInfo;
        String searchFlag = params.getSearchFlag();

        try {
            // "push" 부분을 표현하는 List 생성
            List<Map<String, String>> pushList = new ArrayList<>();

            if (searchFlag.equals("00")) {
                String deviceId = params.getDeviceId();

                pushCode = memberMapper.getSinglePushCodeStatus(params);
                groupInfo = memberMapper.getGroupInfoForPush(deviceId);

                Map<String, String> push1 = new LinkedHashMap<>();
                push1.put("pushCd", "01");
                push1.put("pushYn", pushCode.getFPushYn());
                push1.put("deviceId", pushCode.getDeviceId());
                push1.put("controlAuthKey", pushCode.getControlAuthKey());
                push1.put("modelCode", pushCode.getModelCode());
                push1.put("groupIdx", groupInfo.getGroupIdx());
                push1.put("groupName", groupInfo.getGroupName());
                push1.put("deviceNick", groupInfo.getDeviceNickname());
                pushList.add(push1);

                Map<String, String> push2 = new LinkedHashMap<>();
                push2.put("pushCd", "02");
                push2.put("pushYn", pushCode.getSPushYn());
                push2.put("deviceId", pushCode.getDeviceId());
                push2.put("controlAuthKey", pushCode.getControlAuthKey());
                push2.put("modelCode", pushCode.getModelCode());
                push2.put("groupIdx", groupInfo.getGroupIdx());
                push2.put("groupName", groupInfo.getGroupName());
                push2.put("deviceNick", groupInfo.getDeviceNickname());
                pushList.add(push2);

                Map<String, String> push3 = new LinkedHashMap<>();
                push3.put("pushCd", "03");
                push3.put("pushYn", pushCode.getTPushYn());
                push3.put("deviceId", pushCode.getDeviceId());
                push3.put("controlAuthKey", pushCode.getControlAuthKey());
                push3.put("modelCode", pushCode.getModelCode());
                push3.put("groupIdx", groupInfo.getGroupIdx());
                push3.put("groupName", groupInfo.getGroupName());
                push3.put("deviceNick", groupInfo.getDeviceNickname());
                pushList.add(push3);

                resultMap.put("push", pushList);

            } else if (searchFlag.equals("01")) {

                // TDOD: 세대주 ID 쿼리
                householderId = memberMapper.getHouseholdByUserId(userId).getGroupId();
                deviceIdList = memberMapper.getDeviceIdByUserIds(householderId);

                // deviceIdList로 regist 테이블에서 groupName, groupIdx 추출
                groupInfoList = memberMapper.getGroupInfoByDeviceId(deviceIdList);

                // deviceIds를 쉼표로 구분된 String으로 변환
                String deviceIds = deviceIdList.stream()
                        .map(AuthServerDTO::getDeviceId)
                        .collect(Collectors.joining("','", "'", "'"));

                List<AuthServerDTO> pushCodeInfo = memberMapper.getPushCodeStatus(params.getUserId(), deviceIds);
                if (pushCodeInfo == null) {
                    resultMap.put("resultCode", "1016");
                    resultMap.put("resultMsg", "쿼리 결과 없음");
                    return resultMap;
                }
                // 사용자가 가진 DeviceId 리스트 개수 만큼 생성
                for (int i = 0; pushCodeInfo.size() > i; ++i) {
                    // 각각의 "pushCd"와 "pushYn"을 가지는 Map을 생성하여 리스트에 추가
                    Map<String, String> push1 = new LinkedHashMap<>();
                    push1.put("pushCd", "01");
                    push1.put("pushYn", pushCodeInfo.get(i).getFPushYn());
                    push1.put("deviceId", pushCodeInfo.get(i).getDeviceId());
                    push1.put("controlAuthKey", pushCodeInfo.get(i).getControlAuthKey());
                    push1.put("modelCode", pushCodeInfo.get(i).getModelCode());
                    push1.put("groupIdx", groupInfoList.get(i).getGroupIdx());
                    push1.put("groupName", groupInfoList.get(i).getGroupName());
                    push1.put("deviceNick", groupInfoList.get(i).getDeviceNickname());
                    pushList.add(push1);

                    Map<String, String> push2 = new LinkedHashMap<>();
                    push2.put("pushCd", "02");
                    push2.put("pushYn", pushCodeInfo.get(i).getSPushYn());
                    push2.put("deviceId", pushCodeInfo.get(i).getDeviceId());
                    push2.put("controlAuthKey", pushCodeInfo.get(i).getControlAuthKey());
                    push2.put("modelCode", pushCodeInfo.get(i).getModelCode());
                    push2.put("groupIdx", groupInfoList.get(i).getGroupIdx());
                    push2.put("groupName", groupInfoList.get(i).getGroupName());
                    push2.put("deviceNick", groupInfoList.get(i).getDeviceNickname());
                    pushList.add(push2);

                    Map<String, String> push3 = new LinkedHashMap<>();
                    push3.put("pushCd", "03");
                    push3.put("pushYn", pushCodeInfo.get(i).getTPushYn());
                    push3.put("deviceId", pushCodeInfo.get(i).getDeviceId());
                    push3.put("controlAuthKey", pushCodeInfo.get(i).getControlAuthKey());
                    push3.put("modelCode", pushCodeInfo.get(i).getModelCode());
                    push3.put("groupIdx", groupInfoList.get(i).getGroupIdx());
                    push3.put("groupName", groupInfoList.get(i).getGroupName());
                    push3.put("deviceNick", groupInfoList.get(i).getDeviceNickname());
                    pushList.add(push3);
                }

                resultMap.put("push", pushList);
            }

            resultMap.put("resultCode", "200");
            resultMap.put("resultMsg", "기기 알림 정보 조회 성공");

            log.info("resultMap: {}", resultMap);
            return resultMap;
        } catch (CustomException e) {
            log.error("", e);
            resultMap.put("resultCode", "400");
            resultMap.put("resultMsg", "ERROR");
            return resultMap;
        }
    }

    /**
     * 사용자(세대주) 탈퇴
     */
    @Override
    public ResponseEntity<?> doDelHouseholder(AuthServerDTO params)
            throws CustomException {

        ApiResponse.Data data = new ApiResponse.Data();
        String msg;

        String userId = params.getUserId();
        AuthServerDTO nextHouseholder;
        List<AuthServerDTO> deviceIdList;
        List<AuthServerDTO> inputList;

        try {

            /*
             * *
             * 1. 신규 세대주 ID 쿼리후 기존 세대주 ID, IDX 가 있는 테이블에 ID UPDATE
             * 2. TBD_USER_INVITE_GROUP 에서 기존 세대주 삭제
             * 3. 다음 세대원을 세대주로 변경
             * 4. TBT_OPR_DEVICE_REGIST, TBR_OPR_USER_DEVICE USER_ID 다음 세대원으로 변경
             */

            // 1. 다음 세대원 검색
            nextHouseholder = memberMapper.getNextUserId(params);
            params.setNextUserId(nextHouseholder.getUserId());
            params.setHp(nextHouseholder.getHp());

            // 2. TBR_IOT_DEVICE_GRP_INFO에서 세대주 ID 삭제
            deviceIdList = memberMapper.getDeviceIdFromRegist(params);

            if (!deviceIdList.isEmpty()) {
                inputList = new ArrayList<>();
                for (AuthServerDTO authServerDTO : deviceIdList) {
                    AuthServerDTO newDevice = new AuthServerDTO();
                    newDevice.setDeviceId(authServerDTO.getDeviceId());
                    newDevice.setUserId(userId);
                    // 리스트에 추가
                    inputList.add(newDevice);
                }
                memberMapper.deleteUserDevicePush(inputList);
                deviceMapper.deleteDeviceListGrpInfo(inputList);
            }

            // 2. 다음 세대주TBD_USER_INVITE_GROUP ID로 UPDATE
            memberMapper.updateNewHouseHolder(params);

            // 3. TBD_USER_INVITE_GROUP 에서 기존 세대주 삭제
            params.setDelUserId(userId);
            memberMapper.deleteUserInviteGroup(params);

            // 4. TBT_OPR_DEVICE_REGIST USER_ID, HP 업데이트
            memberMapper.updateDeviceRegist(params);

            // 5. TBR_OPR_USER_DEVICE USER_ID 업데이트
            memberMapper.updateUserDevice(params);

            // 7. TBR_OPR_USER_INVITE_STATUS 테이블에서 세대주 관련 초대 그록 삭제
            memberMapper.deleteInviteStatusByHouseholder(params);

            msg = "사용자(세대주) 탈퇴 성공";
            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            return new ResponseEntity<>(data, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 홈 IoT 서비스 회원 탈퇴
     */
    @Override
    public ResponseEntity<?> doWithdrawal(AuthServerDTO params)
            throws CustomException {

        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        String userId = params.getUserId();
        String userPassword = params.getUserPassword();
        AuthServerDTO account;

        List<AuthServerDTO> deviceIdList;
        try {

            account = memberMapper.getAccountByUserId(userId);
            if (!encoder.matches(userPassword, account.getUserPassword())) {
                msg = "PW 에러";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1003, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            // 서비스 탈퇴시 모든 기기또한 삭제 한다. deleteControllerMapping 호출
            deviceIdList = memberMapper.getGroupIdxByUserId(userId);
            for (AuthServerDTO authServerDTO : deviceIdList) {
                AuthServerDTO newDevice = new AuthServerDTO();
                newDevice.setDeviceId(authServerDTO.getDeviceId());
                newDevice.setUserId(userId);
                newDevice.setControlAuthKey("0000");
                memberMapper.deleteControllerMapping(newDevice);
            }

            /*
             * *
             * 홈IoT 서비스 탈퇴 시 삭제 Table
             * 1. TBR_OPR_ACCOUNT
             * 2. TBR_OPR_USER
             * 3. TBR_OPR_USER_DEVICE
             * 4. TBT_OPR_DEVICE_REGIST
             * 프로시져 사용 (deleteUserFromService)
             */
            if (!memberMapper.deleteMemberFromService(userId).equals("100")) {
                msg = "홈 IoT 서비스 회원 탈퇴 실패";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            }

            msg = "홈 IoT 서비스 회원 탈퇴 성공";

            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            log.info("data: {}", data);
            return new ResponseEntity<>(data, HttpStatus.OK);
        } catch (CustomException e) {
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 홈 IoT 컨트롤러 인증
     */
    @Override
    public ResponseEntity<?> doDeviceAuthCheck(AuthServerDTO params)
            throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        String msg;

        try {
            // 먼저 checkValveStatus로 각방 여부 확인 후 원래 로직 수행
            if (!deviceMapper.checkValveStatus(params).getDeviceCount().equals("0")) {
                msg = "홈 IoT 컨트롤러 인증 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                log.info("result: {}", result);
                return new ResponseEntity<>(result, HttpStatus.OK); // 즉시 종료
            }

            // checkDeviceStatus 실행
            if (deviceMapper.checkDeviceStatus(params) == null) {
                msg = "홈 IoT 컨트롤러 인증 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
            } else {
                msg = "홈 IoT 컨트롤러 인증 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            }

            log.info("result: {}", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 홈 IoT 최초 등록 인증
     */
    @Override
    public ResponseEntity<?> doFirstDeviceAuthCheck(AuthServerDTO params)
            throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject = "N";
        String msg;
        String userId = params.getUserId();
        String serialNumber = params.getSerialNumber();
        String deviceType = params.getDeviceType();
        String modelCode = params.getModelCode();

        MobiusResponse aeResult = null;
        MobiusResponse cntResult = null;
        MobiusResponse subResult = null;

        List<AuthServerDTO> groupMemberList;
        List<AuthServerDTO> valveInfoList = null;

        try {
            /*
             * 위 API 호출 마다 SerialNum, UserId 정보로 Cin 생성
             * 생성이 정상적으로 동작 후 201을 Return 한다면 해당 AE, CNT는 존재하므로 생성X
             * 하지만 201을 Return 하지 못하는 경우 AE, CNT가 없다 판단하여 신규 생성
             * GRP_ID 기준 모든 UserId에 대한 CNT, SUB 생성
             */

            // 각방의 경우 메인 벨브 ID로 Sub ID를 등록 해야 함.
            if (deviceType.equals("05")) {
                // DB TBR_OPR_VALVE_BOX_STATUS 테이블에서 SERIAL_NO와 DEVC_MODL_CD를 쿼리
                valveInfoList = deviceMapper.getSerialNumberAndModelCodeFromEachRoomInfo(serialNumber);
                for (AuthServerDTO authServerDTO : valveInfoList) {
                    aeResult = mobiusService.createAe("20202020" + common.stringToHex(authServerDTO.getSerialNumber()));
                    cntResult = mobiusService.createCnt("20202020" + common.stringToHex(authServerDTO.getSerialNumber()),
                            userId);
                    subResult = mobiusService.createSub("20202020" + common.stringToHex(authServerDTO.getSerialNumber()),
                            userId, "gw");
                }
            } else if(deviceType.equals("02")){
                groupMemberList = memberMapper.getGroupMemberByUserId(userId);
                if(modelCode.equals("DHR-260A")){
                    params.setSerialNumber("    " + params.getSerialNumber());
                    params.setModelCode("  " + params.getModelCode());
                    aeResult = mobiusService.createAe(common.stringToHex(params.getSerialNumber()));
                } else {
                    params.setSerialNumber("    " + params.getSerialNumber());
                    params.setModelCode("   " + params.getModelCode());
                    aeResult = mobiusService.createAe(common.stringToHex(params.getSerialNumber()));
                }
                for (AuthServerDTO authServerDTO : groupMemberList) {
                    cntResult = mobiusService.createCnt(common.stringToHex(params.getSerialNumber()),
                            authServerDTO.getUserId());
                    subResult = mobiusService.createSub(common.stringToHex(params.getSerialNumber()),
                            authServerDTO.getUserId(), "gw");
                }
            }else {
                groupMemberList = memberMapper.getGroupMemberByUserId(userId);

                params.setSerialNumber("    " + params.getSerialNumber());
                params.setModelCode(" " + params.getModelCode());
                aeResult = mobiusService.createAe(common.stringToHex(params.getSerialNumber()));

                // 아래 로직이 필요한지 확인. 기기 제어시 최초 등록자 ID로 제어하기 때문
                for (AuthServerDTO authServerDTO : groupMemberList) {
                    cntResult = mobiusService.createCnt(common.stringToHex(params.getSerialNumber()),
                            authServerDTO.getUserId());
                    subResult = mobiusService.createSub(common.stringToHex(params.getSerialNumber()),
                            authServerDTO.getUserId(), "gw");
                }
            }

            if (aeResult == null && cntResult == null && subResult == null) {
                msg = "홈 IoT 최초 등록 인증 실패";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
                new ResponseEntity<>(result, HttpStatus.OK);
            } else
                stringObject = "Y";

            if (stringObject.equals("Y") && deviceType.equals("05")) {
                result.setDeviceId(valveInfoList.get(0).getParentDevice());
                msg = "최초 인증 성공";
            } else if (stringObject.equals("Y")) {
                result.setDeviceId("0.2.481.1.1." + common.stringToHex(params.getModelCode()) + "."
                        + common.stringToHex(params.getSerialNumber()));
                msg = "최초 인증 성공";
            } else {
                msg = "최초 인증 실패";
            }

            result.setResult("Y".equalsIgnoreCase(stringObject) ? ApiResponse.ResponseType.HTTP_200
                    : ApiResponse.ResponseType.CUSTOM_1018, msg);

            log.info("result: {}", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * API 인증키 갱신
     */
    @Override
    public ResponseEntity<?> doAccessTokenVerification(AuthServerDTO params)
            throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject;
        String msg;
        String userId = params.getUserId();
        String token = params.getAccessToken();
        AuthServerDTO account;
        AuthServerDTO member;

        try {
            account = memberMapper.getAccountByUserId(userId);
            if (account == null) {
                msg = "계정이 존재하지 않습니다.";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1016, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            member = memberMapper.getUserByUserId(userId);
            if (member.getAccessToken().equals(token)) {
                stringObject = "Y";
                result.setTokenVerify(stringObject);
            } else {
                stringObject = "N";
                result.setTokenVerify(stringObject);
            }

            msg = "API인증키 갱신 성공";

            result.setResult(ApiResponse.ResponseType.HTTP_200, msg);

            log.info("result: {}", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (CustomException e) {
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 홈 IoT 컨트롤러 삭제(회원 매핑 삭제)
     */
    @Override
    public ResponseEntity<?> doUserDeviceDelete(AuthServerDTO params)
            throws CustomException {

        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        String userId = params.getUserId();
        List<String> deviceIdList = params.getDeviceIdList();

        try {

            /*
             * *
             * TBT_OPR_DEVICE_REGIST - 임시 단말 등록 정보
             * TBR_OPR_USER_DEVICE - 사용자 단말 정보
             * TBR_OPR_DEVICE_DETAIL - 단말정보상세
             * 프로시져
             */
            for (String deviceId : deviceIdList) {
                AuthServerDTO info = new AuthServerDTO();
                info.setUserId(userId);
                info.setDeviceId(deviceId);
                info.setControlAuthKey("0000");

                if (!memberMapper.deleteControllerMapping(info).equals("100")) {
                    msg = "기기 삭제(회원 매핑 삭제) 실패";
                    data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(data, HttpStatus.OK);
                }
            }

            msg = "기기 삭제(회원 매핑 삭제) 성공";

            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            log.info("data: {}", data);
            return new ResponseEntity<>(data, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 스마트알림 - PUSH 이력 조회
     */
    @Override
    public ResponseEntity<?> doViewPushHistory(AuthServerDTO params)
            throws CustomException {

        ApiResponse.Data data = new ApiResponse.Data();
        String msg;

        int pageNo = params.getPageNo();
        int numberOfRows = params.getNumOfRows();
        List<AuthServerDTO> member;
        AuthServerDTO lastIndex;
        List<Map<String, String>> pushInfoList = new ArrayList<>();

        try {
            if (pageNo > 1)
                params.setFrontRow((numberOfRows * pageNo) - numberOfRows);
            else
                params.setFrontRow(0);

            params.setSecondRow(numberOfRows);

            member = memberMapper.getPushInfoList(params);
            if (member == null) {
                msg = "계정이 존재하지 않습니다.";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1016, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            } else {
                params.setFrontRow(pageNo * numberOfRows);
                lastIndex = memberMapper.checkLastIndex(params);

                if (lastIndex == null)
                    data.setIsEnd("T");
                else if (lastIndex.getLastIndex().equals("0"))
                    data.setIsEnd("T");
                else
                    data.setIsEnd("F");

                for (AuthServerDTO authServerDTO : member) {
                    Map<String, String> map = new HashMap<>();
                    map.put("pushIdx", authServerDTO.getPushIdx());
                    map.put("pushTitle", authServerDTO.getPushTitle());
                    map.put("pushContent", authServerDTO.getPushContent());
                    map.put("pushType", authServerDTO.getPushType());
                    map.put("pushDatetime", authServerDTO.getPushDatetime());
                    map.put("deviceType", authServerDTO.getDeviceType());
                    map.put("deviceNickname", authServerDTO.getDeviceNickname());
                    map.put("groupName", authServerDTO.getGroupName());
                    map.put("groupIdx", authServerDTO.getGroupIdx());
                    pushInfoList.add(map);
                }
            }

            msg = "스마트알림 - PUSH 이력 조회 성공";
            data.setPushInfo(pushInfoList);
            data.setResult(ApiResponse.ResponseType.HTTP_200, msg);
//            log.info("data: " + data);  너무 길어서 주석 처리
            return new ResponseEntity<>(data, HttpStatus.OK);
        } catch (CustomException e) {
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 기기 별칭 수정
     */
    @Override
    public ResponseEntity<?> doDeviceNicknameChange(AuthServerDTO params)
            throws CustomException {

        ApiResponse.Data data = new ApiResponse.Data();
        String stringObject = "N";
        String msg;
        String deviceType = params.getDeviceType();
        String modelCode = params.getModelCode();
        try {
            // deviceNickname Input 대신 newDeviceNickname을 받아서 Setter 사용
            params.setDeviceNickname(params.getNewDeviceNickname());

            if (deviceType.equals("05") && !modelCode.equals("DR-300W")) {
                if (deviceMapper.changeEachRoomDeviceNickname(params) <= 0) {
                    msg = "기기 별칭 수정 실패";
                    data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    new ResponseEntity<>(data, HttpStatus.OK);
                } else
                    stringObject = "Y";
            } else {
                if (deviceMapper.changeDeviceNickname(params) <= 0) {
                    msg = "기기 별칭 수정 실패";
                    data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    new ResponseEntity<>(data, HttpStatus.OK);
                }
                if (deviceMapper.changeDeviceNicknameTemp(params) <= 0) {
                    msg = "기기 별칭 수정 실패";
                    data.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    new ResponseEntity<>(data, HttpStatus.OK);
                } else
                    stringObject = "Y";
            }

            if (stringObject.equals("Y"))
                msg = "기기 별칭 수정 성공";
            else
                msg = "기기 별칭 수정 실패";

            data.setResult("Y".equalsIgnoreCase(stringObject) ? ApiResponse.ResponseType.HTTP_200
                    : ApiResponse.ResponseType.CUSTOM_1018, msg);

            log.info("data: {},", data);
            return new ResponseEntity<>(data, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(data, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 기기 밝기 조절
     */
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

        try {
            firstDeviceUser = memberMapper.getFirstDeviceUser(deviceId);
            userId = firstDeviceUser.getUserId();

            serialNumber = deviceMapper.getSingleSerialNumberBydeviceId(params.getDeviceId());
            if (serialNumber.getSerialNumber() == null) {
                msg = "기기 제어 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1009, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

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
            if (!mobiusResponse.getResponseCode().equals("201")) {
                msg = "중계서버 오류";
                result.setResult(ApiResponse.ResponseType.HTTP_404, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            try {
                // 메시징 시스템을 통해 응답 메시지 대기
                responseMessage = gwMessagingSystem.waitForResponse("blCf" + uuId, TIME_OUT, TimeUnit.SECONDS);
                if (responseMessage == null) {
                    msg = "기기 응답이 없습니다. 잠시후 다시 시도하십시오";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1015, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);

                } else if (!responseMessage.equals("0")) {
                    msg = "기기 네트워크 연결 오류. 잠시후 다시 시도하십시오";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1014, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }
            } catch (InterruptedException e) {
                // 대기 중 인터럽트 처리
                log.error("", e);
            }

            gwMessagingSystem.removeMessageQueue("blCf" + uuId);
            redisCommand.deleteValues(uuId);

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

            for (int i = 0; i < userIds.size(); ++i) {
                if (memberMapper.getUserLoginoutStatus(userIds.get(i).getUserId()).getLoginoutStatus().equals("Y")) {
                    conMap1.put("pushYn", pushYnList.get(i).getFPushYn());
                    conMap1.put("modelCode", common.getModelCodeFromDeviceId(deviceId).replaceAll(" ", ""));
                    conMap1.put("targetToken", memberMapper.getPushTokenByUserId(userIds.get(i).getUserId()).getPushToken());
                    conMap1.put("userNickname", userNickname.getUserNickname());
                    conMap1.put("deviceNick", common.returnDeviceNickname(deviceId));
                    conMap1.put("title", "blCf");
                    conMap1.put("deviceId", deviceId);
                    conMap1.put("id", "Brightness Control ID");

                    String jsonString1 = objectMapper.writeValueAsString(conMap1);

                    mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString1);
                }
            }

            common.insertHistory(
                    "1",
                    "BrightnessControl",
                    "blCf",
                    "밝기 조절",
                    "0",
                    deviceId,
                    params.getUserId(),
                    "밝기 조절",
                    params.getBrightnessLevel(),
                    "01");

            log.info("result: {}", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 공지사항 조회
     */
    @Override
    public ResponseEntity<?> doNotice(AuthServerDTO params) throws CustomException {

        ApiResponse.Data data = new ApiResponse.Data();
        String msg;
        List<AuthServerDTO> notice;
        List<Map<String, String>> noticeList = new ArrayList<>();
        try {
            notice = memberMapper.getNoticeList(params);
            if (notice.isEmpty()) {
                msg = "공지사항 목록이 없습니다.";
                data.setResult(ApiResponse.ResponseType.CUSTOM_1016, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);
            } else {
                for (AuthServerDTO authServerDTO : notice) {
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
            log.info("data: {}",data);
            return new ResponseEntity<>(data, HttpStatus.OK);
        } catch (CustomException e) {
            log.error("", e);
        }
        return null;
    }

    /**
     * 기기 설치 위치 별칭 수정
     */
    @Override
    public ResponseEntity<?> doUpdateDeviceLocationNickname(AuthServerDTO params) throws CustomException {

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject = "N";
        String msg;

        try {

            if (memberMapper.updateDeviceLocationNicknameDeviceRegist(params) <= 0) {
                msg = "기기 설치 위치 별칭 수정 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                new ResponseEntity<>(result, HttpStatus.OK);
            } else
                stringObject = "Y";

            if (stringObject.equals("Y"))
                msg = "기기 설치 위치 별칭 수정 성공";
            else
                msg = "기기 설치 위치 별칭 수정 실패";

            result.setResult(ApiResponse.ResponseType.HTTP_200, msg);

            log.info("result: {}", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 임시 저장키 생성
     */
    @Override
    public ResponseEntity<?> dogenerateTempKey(String userId) throws Exception {

        ApiResponse.Data result = new ApiResponse.Data();
        String msg;
        try {
            result.setTmpRegistKey(userId + common.getCurrentDateTime());
            log.info("result: {}", result);

            msg = "임시저장키 생성 성공";
            result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 안전안심 알람 설정
     */
    @Override
    public ResponseEntity<?> doSafeAlarmSet(AuthServerDTO params) throws Exception {

        ApiResponse.Data result = new ApiResponse.Data();
        String msg;
        AuthServerDTO safeAlarmInfo;

        try {

            // 먼저 같은 USER_ID, DEVC_ID 가 있는지 확인 하고 있으면 UPDATE 없으면 INSERT를 한다.
            safeAlarmInfo = memberMapper.checkSafeAlarmSet(params);
            // 이미 존재 하는 경우
            if (safeAlarmInfo.getLastIndex().equals("1")) {
                if (memberMapper.updateSafeAlarm(params) <= 0) {
                    msg = "안전안심 알람 설정 실패";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }
            } else {
                if (memberMapper.InsertSafeAlarmSet(params) <= 0) {
                    msg = "안전안심 알람 설정 실패";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }
            }

            msg = "안전안심 알람 설정 성공";

            result.setResult(ApiResponse.ResponseType.HTTP_200, msg);

            log.info("result: {}", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 빠른온수 예약 정보 조회
     */
    @Override
    public ResponseEntity<?> doGetFastHotWaterInfo(AuthServerDTO params) throws Exception {
        ApiResponse.Data result = new ApiResponse.Data();
        String msg;
        String deviceId = params.getDeviceId();

        AuthServerDTO fwhInfo;
        try {
            fwhInfo = memberMapper.getFwhInfo(deviceId);

            if (fwhInfo == null) {
                msg = "빠른온수 예약 정보 없음";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1016, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else {
                result.setAwakeList(fwhInfo.getFastHotWater());
                msg = "빠른온수 예약 정보 조회 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            }

            log.info("result: {}", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 환기 필터 잔여 수명 정보 조회
     */
    @Override
    public ResponseEntity<?> doGetFanLifeStatus(AuthServerDTO params) throws Exception {
        ApiResponse.Data result = new ApiResponse.Data();
        String msg;
        String deviceId = params.getDeviceId();

        AuthServerDTO fanInfo;
        try {
            fanInfo = memberMapper.getFanLifeStatus(deviceId);

            if (fanInfo == null) {
                msg = "환기 필터 잔여 수명 정보 없음";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1016, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            } else {
                result.setVentFanLifeStatus(fanInfo.getVentFanLifeStatus());
                msg = "환기 필터 잔여 수명 정보 조회 성공";
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            }

            log.info("result: {}", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 안전 안심 알람 정보 조회
     */
    @Override
    public ResponseEntity<?> doGetSafeAlarmSetInfo(AuthServerDTO params) throws Exception {

        ApiResponse.Data result = new ApiResponse.Data();
        String msg;

        AuthServerDTO safeAlarmCount;
        AuthServerDTO safeAlarmInfo;

        try {

            safeAlarmCount = memberMapper.getSafeAlarmInfoCount(params);
            if (safeAlarmCount.getSafeAlarmCount().equals("0")) {
                msg = "안전 안심 알람 정보 조회 성공";
                result.setSafeAlarmTime("0000");
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            } else {
                msg = "안전 안심 알람 정보 조회 성공";
                safeAlarmInfo = memberMapper.getSafeAlarmInfo(params);
                result.setSafeAlarmTime(safeAlarmInfo.getSafeAlarmTime());
                result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            }

            log.info("result: {}", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 외출/귀가 모드 정보 추가
     * */
    @Override
    public ResponseEntity<?> doUpsertAwayHomeMode(AuthServerDTO params) throws Exception {

        ApiResponse.Data result = new ApiResponse.Data();
        String msg = "";
        String registYn = params.getRegistYn();

        String userNickname = params.getUserNickname();
        String deviceNickname = params.getDeviceNickname();
        String groupIdx = params.getGroupIdx();
        String groupName = params.getGroupName();
        String deviceId = params.getDeviceId();
        String requestUserId = params.getUserId();
        String latitude = params.getLatitude();
        String longitude = params.getLongitude();
        String locationRadius = params.getLocationRadius();

        String awayStatus = params.getAwayStatus();
        String awayMode = params.getAwayMode();
        String awayValue = params.getAwayValue();
        String homeStatus = params.getHomeStatus();
        String homeMode = params.getHomeMode();
        String homeValue = params.getHomeValue();

        List<AuthServerDTO> userIds;

        Map<String, String> conMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        try {

            // awayStatus, awayMode, awayValue, homeStatus, homeMode, homeValue 값이 "0" 이면 "" 로 치환
            if ("0".equals(awayStatus)) {
                params.setAwayStatus("");
            }
            if ("0".equals(awayMode)) {
                params.setAwayMode("");
            }
            if ("0".equals(awayValue)) {
                params.setAwayValue("");
            }
            if ("0".equals(homeStatus)) {
                params.setHomeStatus("");
            }
            if ("0".equals(homeMode)) {
                params.setHomeMode("");
            }
            if ("0".equals(homeValue)) {
                params.setHomeValue("");
            }

            if(registYn.equals("Y")) {
                if (memberMapper.insertAwayHomeMode(params) <= 0 ){
                    msg = "외출/귀가 모드 정보 추가 실패";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }
                msg = "외출/귀가 모드 정보 등록 성공";
            } else if(registYn.equals("N")) {
                if (memberMapper.updateAwayHomeMode(params) <= 0 ){
                    msg = "외출/귀가 모드 정보 수정 실패";
                    result.setResult(ApiResponse.ResponseType.CUSTOM_1018, msg);
                    return new ResponseEntity<>(result, HttpStatus.OK);
                }
                msg = "외출/귀가 모드 정보 수정 성공";
            }

            userIds = memberMapper.getUserIdsByDeviceId(params);

            // 등록 및 수정 시 요청자 별칭을 기반으로 모든 세대원에게 푸시 전송
            for(AuthServerDTO userId : userIds) {
                conMap.put("targetToken", memberMapper.getPushTokenByUserId(userId.getUserId()).getPushToken());
                conMap.put("title", "HOME_AWAY");
                conMap.put("userId", requestUserId);
                conMap.put("userNickname", common.stringToHex(userNickname));
                conMap.put("deviceNick", common.stringToHex(deviceNickname));
                conMap.put("deviceId", deviceId);
                conMap.put("groupIdx", groupIdx);
                conMap.put("groupName", common.stringToHex(groupName));
                conMap.put("longitude", longitude);
                conMap.put("latitude", latitude);
                conMap.put("locationRadius", locationRadius);
                conMap.put("pushYn", "Y");

                String jsonString = objectMapper.writeValueAsString(conMap);
                mobiusService.createCin("ToPushServer", "ToPushServerCnt", jsonString);
            }

            result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            log.info("result: {}", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 외출/귀가 모드 정보 조회
     * */
    @Override
    public ResponseEntity<?> doViewAwayHomeMode(AuthServerDTO params) throws Exception {

        ApiResponse.Data result = new ApiResponse.Data();
        String msg;

        AuthServerDTO modeInfo;

        try {

            modeInfo = memberMapper.getAwayHomeModeInfo(params);
            if(modeInfo == null){
                msg = "외출/귀가 모드 정보 조회 실패";
                result.setResult(ApiResponse.ResponseType.CUSTOM_1016, msg);
                return new ResponseEntity<>(result, HttpStatus.OK);
            }

            result.setGroupIdx(modeInfo.getGroupIdx());
            result.setLocationRadius(modeInfo.getLocationRadius());
            result.setLongitude(modeInfo.getLongitude());
            result.setLatitude(modeInfo.getLatitude());
            result.setAwayStatus(modeInfo.getAwayStatus());
            result.setAwayMode(modeInfo.getAwayMode());
            result.setAwayValue(modeInfo.getAwayValue());
            result.setHomeStatus(modeInfo.getHomeStatus());
            result.setHomeMode(modeInfo.getHomeMode());
            result.setHomeValue(modeInfo.getHomeValue());

            msg = "외출/귀가 모드 정보 조회 성공";
            result.setResult(ApiResponse.ResponseType.HTTP_200, msg);

            log.info("result: {}", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 외출/귀가 모드 기기 제어
     * */
    @Override
    public ResponseEntity<?> doControlAwayHomeMode(AuthServerDTO params) throws Exception {

        ApiResponse.Data result = new ApiResponse.Data();
        PowerOnOff powerOnOff = new PowerOnOff();
        ModeChange modeChange = new ModeChange();
        TemperatureSet temperatureSet = new TemperatureSet();
        BoiledWaterTempertureSet boiledWaterTempertureSet = new BoiledWaterTempertureSet();
        VentilationFanSpeedSet fanSpeedSet = new VentilationFanSpeedSet();

        String msg;
        String userId = params.getUserId();
        String deviceId = params.getDeviceId();
        String modeCode = params.getModeCode();
        String temperature = params.getTemperture();
        String fanSpeed = params.getFanSpeed();
        String modelCode = common.getModelCodeFromDeviceId(deviceId);
        String deviceType = common.getModelCode(modelCode.trim());
        String serialNumber = common.getHexSerialNumberFromDeviceId(deviceId);
        String redisValue;

        try {

            powerOnOff.setUserId(userId);
            powerOnOff.setDeviceId(deviceId);
            powerOnOff.setControlAuthKey("9987");
            powerOnOff.setDeviceType(deviceType);
            powerOnOff.setModelCode(common.stringToHex(modelCode));
            powerOnOff.setPowerStatus("on");
            powerOnOff.setFunctionId("powr");
            powerOnOff.setUuId(common.getTransactionId());

            redisValue = userId + "," + powerOnOff.getFunctionId();
            redisCommand.setValues(powerOnOff.getUuId(), redisValue);

            mobiusService.createCin(serialNumber, userId, JSON.toJson(powerOnOff));
            redisCommand.deleteValues(powerOnOff.getUuId());

            // 1초 지연
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 인터럽트 상태 복구
                log.error("", e);
            }

            modeChange.setUserId(userId);
            modeChange.setDeviceId(deviceId);
            modeChange.setControlAuthKey("9987");
            modeChange.setModelCode(common.stringToHex(modelCode));
            modeChange.setModeCode(modeCode);
            modeChange.setFunctionId("opMd");
            modeChange.setUuId(common.getTransactionId());

            redisValue = userId + "," + modeChange.getFunctionId();
            redisCommand.setValues(modeChange.getUuId(), redisValue);

            mobiusService.createCin(serialNumber, userId, JSON.toJson(modeChange));
            redisCommand.deleteValues(modeChange.getUuId());

            // 1초 지연
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 인터럽트 상태 복구
                log.error("", e);
            }

            if(deviceType.equals("01")){
                if(modeCode.equals("01")){ // 실내 난방 / 실내 온도 설정 htTp
                    temperatureSet.setUserId(userId);
                    temperatureSet.setDeviceId(deviceId);
                    temperatureSet.setControlAuthKey("9987");
                    temperatureSet.setTemperature(temperature);
                    temperatureSet.setFunctionId("htTp");
                    temperatureSet.setUuId(common.getTransactionId());

                    redisValue = userId + "," + temperatureSet.getFunctionId();
                    redisCommand.setValues(temperatureSet.getUuId(), redisValue);

                    mobiusService.createCin(serialNumber, userId, JSON.toJson(temperatureSet));
                    redisCommand.deleteValues(temperatureSet.getUuId());

                } else if(modeCode.equals("02")){
                    boiledWaterTempertureSet.setUserId(userId);
                    boiledWaterTempertureSet.setDeviceId(deviceId);
                    boiledWaterTempertureSet.setControlAuthKey("9987");
                    boiledWaterTempertureSet.setTemperature(temperature);
                    boiledWaterTempertureSet.setFunctionId("wtTp");
                    boiledWaterTempertureSet.setUuId(common.getTransactionId());

                    redisValue = userId + "," + boiledWaterTempertureSet.getFunctionId();
                    redisCommand.setValues(boiledWaterTempertureSet.getUuId(), redisValue);

                    mobiusService.createCin(serialNumber, userId, JSON.toJson(boiledWaterTempertureSet));
                    redisCommand.deleteValues(boiledWaterTempertureSet.getUuId());
                }

            } else if(deviceType.equals("07")){
                fanSpeedSet.setUserId(userId);
                fanSpeedSet.setDeviceId(deviceId);
                fanSpeedSet.setControlAuthKey("9987");
                fanSpeedSet.setModelCode(common.stringToHex(modelCode));
                fanSpeedSet.setFanSpeed(fanSpeed);
                fanSpeedSet.setFunctionId("vtSp");
                fanSpeedSet.setUuId(common.getTransactionId());

                redisValue = userId + "," + fanSpeedSet.getFunctionId();
                redisCommand.setValues(fanSpeedSet.getUuId(), redisValue);

                mobiusService.createCin(serialNumber, userId, JSON.toJson(fanSpeedSet));
                redisCommand.deleteValues(fanSpeedSet.getUuId());
            }

            msg = "외출/귀가 모드 기기 제어 성공.";
            result.setResult(ApiResponse.ResponseType.HTTP_200, msg);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e){
            log.error("", e);
        }
        return null;
    }

    /**
     * WIFI 정보 저장
     * */
    @Override
    public ResponseEntity<?> doInsertWifiInfo(AuthServerDTO params) throws Exception {

        ApiResponse.Data result = new ApiResponse.Data();
        String msg;

        try {

            memberMapper.insertUserWifiInfo(params);
            msg = "WIFI 정보 저장";
            result.setResult(ApiResponse.ResponseType.HTTP_200, msg);

            log.info("result: {}", result);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            log.error("", e);
            return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
        }
    }
}
