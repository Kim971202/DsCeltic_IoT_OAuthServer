package com.oauth.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oauth.constants.MobiusResponse;
import com.oauth.dto.AuthServerDTO;
import com.oauth.mapper.DeviceMapper;
import com.oauth.mapper.MemberMapper;
import com.oauth.response.ApiResponse;
import com.oauth.service.mapper.UserService;
import com.oauth.utils.Common;
import com.oauth.utils.CustomException;
import com.oauth.utils.JSON;
import com.oauth.utils.RedisCommand;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

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

    public static final List<String> oldModels = Arrays.asList("oldModel1", "oldModel2");

    /** 회원 로그인 */
    @Override
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

        String msg = null;

        try {
            AuthServerDTO member = memberMapper.getUserByUserId(userId);
            if(member == null) {
                stringObject = "N";
            } else {
                userNickname = member.getUserNickname();
                accessToken = member.getAccessToken();
            }

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

            if(stringObject.equals("Y")) msg = "로그인 성공";
            else msg = "로그인 실패";

            result.setResult("Y".equalsIgnoreCase(stringObject)
                    ? ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);

            result.setDevice(data);
            return new ResponseEntity<>(result, HttpStatus.OK);
        }catch (CustomException e){
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /** 회원가입 */
    @Override
    public ResponseEntity<?> doRegist(AuthServerDTO params) throws CustomException {

        String stringObject = null;
        ApiResponse.Data data = new ApiResponse.Data();

        String hp = params.getHp();
        String userNickname = params.getUserNickname();
        String userId = params.getUserId();
        String userPassword = params.getUserPassword();

        String msg = null;

        try{

            userPassword = encoder.encode(userPassword);
            params.setUserPassword(userPassword);

            int result1 = memberMapper.insertAccount(params);
            int result2 = memberMapper.insertMember(params);
            if(result1 > 0 && result2 > 0) stringObject = "Y";
            else stringObject = "N";

            if(stringObject.equals("Y")) msg = "회원가입 성공";
            else msg = "회원가입 실패";

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

    /** 회원중복 체크 */
    @Override
    public ResponseEntity<?> doDuplicationCheck(AuthServerDTO params) throws CustomException {

        String stringObject = null;
        ApiResponse.Data data = new ApiResponse.Data();
        String userId = params.getUserId();
        String msg = null;

        try{
            AuthServerDTO member = memberMapper.getUserByUserId(userId);

            if(member == null) stringObject = "N";
            else stringObject = "Y";

            if(stringObject.equals("Y")) msg = "중복 ID";
            else msg = "중복 되지 않는 ID ";

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

    /** ID 찾기 */
    @Override
    public ResponseEntity<?> doIdFind(AuthServerDTO params) throws CustomException {

        String stringObject = null;
        ApiResponse.Data data = new ApiResponse.Data();

        String userHp = params.getHp();
        String deviceType = params.getDeviceType();
        String modelCode = params.getModelCode();
        String deviceId = params.getDeviceId();

        String msg = null;

        List<AuthServerDTO> member = null;

        try {

            // 구형 모델의 경우
            if(modelCode.equals(oldModels.get(0)) || modelCode.equals(oldModels.get(1))) member = memberMapper.getUserByHp(userHp);
            else member = memberMapper.getUserByDeviceId(deviceId);

            if(member.isEmpty()) stringObject = "N";
            else stringObject = "Y";

            List<String> userId = Common.extractJson(member.toString(), "userId");
            if(userId.isEmpty()) userId = null;

            if(stringObject.equals("Y")) msg = "ID 찾기 성공";
            else msg = "입력한 아이디와 일치하는 회원정보가 없습니다.";


            data.setUserIdList(userId);
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

    /** 비밀번호 찾기 - 초기화 */
    @Override
    public ResponseEntity<?> doResetPassword(AuthServerDTO params) throws CustomException {
        String stringObject = null;
        ApiResponse.Data data = new ApiResponse.Data();

        String userId = params.getUserId();
        String userHp = params.getHp();
        String deviceType = params.getDeviceType();
        String modelCode = params.getModelCode();
        String deviceId = params.getDeviceId();

        AuthServerDTO member = null;

        String msg = null;

        try {

            // 구형 모델의 경우
            if(modelCode.equals(oldModels.get(0)) || modelCode.equals(oldModels.get(1))){
                System.out.println("getUserByUserIdAndHp Called");
                member = memberMapper.getUserByUserIdAndHp(params);
            } else {
                System.out.println("getUserByUserIdAndHpAndDeviceId Called");
                member = memberMapper.getUserByUserIdAndHpAndDeviceId(params);
            }

            if(member == null) stringObject = "N";
            else stringObject = "Y";

            if(stringObject.equals("Y")) msg = "비밀번호 찾기 - 초기화 성공";
            else msg = "비밀번호 찾기 - 초기화 실패";

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

    /** 비밀번호 변경 - 생성 */
    @Override
    public ResponseEntity<?> doChangePassword(AuthServerDTO params) throws CustomException {
        String stringObject = null;
        ApiResponse.Data data = new ApiResponse.Data();

        String msg = null;

        try{

            Common.updateMemberDTOList(params, "userPassword", encoder.encode(params.getUserPassword()));

            int result = memberMapper.updatePassword(params);

            if(result > 0) stringObject = "Y";
            else stringObject = "N";

            if(stringObject.equals("Y")) msg = "비밀번호 변경 - 생성 성공";
            else msg = "비밀번호 변경 - 생성 실패";

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

    /** 사용자정보 조회 */
    @Override
    public ResponseEntity<?> doSearch(AuthServerDTO params) throws CustomException {
        String stringObject = null;
        ApiResponse.Data data = new ApiResponse.Data();
        String msg = null;
        String userId = params.getUserId();

        try{
            AuthServerDTO member = memberMapper.getUserByUserId(userId);

            if (member != null) {
                data.setUserId(member.getUserId());
                data.setUserNickname(member.getUserNickname());
                data.setHp(member.getHp());
                stringObject = "Y";
            } else stringObject = "N";

            if(stringObject.equals("Y")) msg = "사용자정보 조회 성공";
            else msg = "사용자정보 조회 실패";

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

    /** 회원 별칭(이름) 및 전화번호 변경 */
    @Override
    public ResponseEntity<?> doUpdateUserNicknameHp(AuthServerDTO params) throws CustomException{
        String stringObject = null;
        ApiResponse.Data data = new ApiResponse.Data();
        String msg = null;

        String userId = params.getUserId();
        String userPassword = params.getUserPassword();
        String oldHp = params.getOldHp();
        String newHp = params.getNewHp();
        String userNickname = params.getUserNickname();

        try{

            AuthServerDTO pwCheck = memberMapper.passwordCheck(userPassword);
            System.out.println("pwCheck: " + pwCheck);
            // TODO: 예외처리 하여 불일치 PW 알림
            if(pwCheck == null) return null;

            if(newHp == null) params.setNewHp(oldHp);
            else if(oldHp.equals(newHp)) return null; // TODO: 예외처리 하여 동일한 전화번호 변경 알림

            int result = memberMapper.updateUserNicknameAndHp(params);
            System.out.println("result: " + result);
            if(result > 0) stringObject = "Y";
            else stringObject = "N";

            if(stringObject.equals("Y")) msg = "회원 별칭(이름) 및 전화번호 변경 성공";
            else msg = "회원 별칭(이름) 및 전화번호 변경 실패";

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

    /** 비밀번호 변경 - 로그인시 */
    @Override
    public ResponseEntity<?> doUpdatePassword(AuthServerDTO params) throws CustomException{

        String stringObject = null;
        ApiResponse.Data data = new ApiResponse.Data();

        String accessToken = params.getAccessToken();
        String userId = params.getUserId();
        String oldPassword = params.getOldPassword();
        String newPassword = params.getNewPassword();

        String msg = null;

        try{
            params.setUserPassword(newPassword);

            AuthServerDTO pwCheck = memberMapper.passwordCheck(oldPassword);

            // TODO: 예외처리 하여 불일치 PW 알림
            if(pwCheck == null) return null;

            // AccessToken 검증
            boolean result = common.tokenVerify(params);
            System.out.println("result: " + result);


            if(!result) stringObject = "N";
            else {
                int pwChangeResult = memberMapper.updatePassword(params);
                if(pwChangeResult > 0)  stringObject = "Y";
                else stringObject = "N";
            }

            if(stringObject.equals("Y")) msg = "비밀번호 변경 - 로그인시 성공";
            else msg = "비밀번호 변경 - 로그인시 실패";

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

    /** 사용자(세대원) 정보 조회 */
    @Override
    public ResponseEntity<?> doViewHouseholdMemebers(AuthServerDTO params) throws CustomException{
        String stringObject = null;
        ApiResponse.Data data = new ApiResponse.Data();
        String msg = null;
        try {

            // AccessToken 검증
            boolean result = common.tokenVerify(params);
            System.out.println("result: " + result);

            if(!result) stringObject = "N";
            else stringObject = "Y";

            String accessToken = params.getAccessToken();
            String userId = params.getUserId();

            // Device Set 생성
            Set<String> userIds = new HashSet<>();
            List<ApiResponse.Data.User> user = new ArrayList<>();

            List<AuthServerDTO> deviceIds = memberMapper.getDeviceIdByUserId(userId);
            if(!deviceIds.isEmpty()){
                stringObject = "Y";
                List<AuthServerDTO> members = memberMapper.getHouseMembersByUserId(deviceIds);

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

        String stringObject = null;
        ApiResponse.Data data = new ApiResponse.Data();
        String msg = null;

        String accessToken = params.getAccessToken();
        String requestUserId = params.getRequestUserId();
        String responseUserId = params.getResponseUserId();
        String responseHp = params.getResponseHp();
        String inviteStartDate = params.getInviteStartDate();

        try {

            int result = memberMapper.inviteHouseMember(params);

            if(result <= 0) stringObject = "N";
            else stringObject = "Y";

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

    /** 사용자 초대 - 수락여부 */
    @Override
    public ResponseEntity<?> doInviteStatus(AuthServerDTO params)
            throws CustomException{

        String stringObject = null;
        ApiResponse.Data data = new ApiResponse.Data();

        String msg = null;

        String accessToken = params.getAccessToken();
        String requestUserId = params.getRequestUserId();
        String responseHp = params.getResponseHp();
        String responseUserId = params.getResponseUserId();
        String inviteAcceptYn = params.getInviteAcceptYn();
        try {

            /**
             * 수락시: responseUserId 사용자는 requestUserId 사용자가 가진 DeviceId에 자동으로 mapping된다.
             * 거부시: TBR_OPR_USER_INVITE_STATUS 수락여부 항목 N UPDATE
             * 수락시 쿼리 흐름:
             * 1. TBR_OPR_USER_INVITE_STATUS
             * 2. TBR_OPR_USER_DEVICE에서 requestUserId 검색
             * 3. 2번 출력값으로 TBR_OPR_USER_DEVICE에 responseUserId INSERT
             * */

            List<AuthServerDTO> member = null;
            int insertNewHouseMemberResult;
            int acceptInviteResult;
            if(inviteAcceptYn.equals("Y")){

                acceptInviteResult = memberMapper.acceptInvite(params);

                member = memberMapper.getDeviceIdByUserId(requestUserId);

                Common.updateMemberDTOList(member, "responseUserId", responseUserId);
                Common.updateMemberDTOList(member, "householder", "N");

                System.out.println(member);
                insertNewHouseMemberResult = memberMapper.insertNewHouseMember(member);

                if(insertNewHouseMemberResult > 0 && acceptInviteResult > 0) stringObject = "Y";
                else stringObject = "N";

            } else if(inviteAcceptYn.equals("N")){

                acceptInviteResult = memberMapper.acceptInvite(params);
                if(acceptInviteResult >0) stringObject = "Y";
                else stringObject = "N";

            } else {
                // TODO: UNKNOWN ERROR 추가
                return null;
            }

            if(stringObject.equals("Y")) msg = "사용자 초대 - 수락여부 성공";
            else msg = "사용자 초대 - 수락여부 실패";

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

    /** 사용자 초대 - 목록 조회 */
    @Override
    public ResponseEntity<?> doInviteListView(AuthServerDTO params)
            throws CustomException{

        String stringObject = null;
        ApiResponse.Data data = new ApiResponse.Data();
        String msg = null;
        try {

            String accessToken = params.getAccessToken();
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

        String stringObject = null;
        ApiResponse.Data data = new ApiResponse.Data();

        String msg = null;

        String userHp = params.getHp();
        String userNickname = params.getUserNickname();
        String accessToken = params.getAccessToken();
        String userId = params.getUserId();

        try {
            int result = memberMapper.delHouseMember(userId);

            if(result > 0) stringObject = "Y";
            else stringObject = "N";

            if(stringObject.equals("Y")) msg = "사용자(세대원) - 강제탈퇴 성공";
            else msg = "사용자(세대원) - 강제탈퇴 실패";

            data.setResult("Y".equalsIgnoreCase(stringObject) ?
                    ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);
            return new ResponseEntity<>(data, HttpStatus.OK);
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    /** 홈 IoT 컨트롤러 알림 설정 */
    @Override
    public ResponseEntity<?> doPushSet(AuthServerDTO params, HashMap<String, String> controlMap)
            throws CustomException{

        String stringObject = null;
        ApiResponse.Data data = new ApiResponse.Data();

        String msg = null;

        String userNickname = params.getUserNickname();
        String hp = params.getHp();
        String accessToken = params.getAccessToken();
        String userId = params.getUserId();
        String deviceId = params.getDeviceId();
        String controlAuthKey = params.getControlAuthKey();
        String deviceType = params.getDeviceType();
        String modelCode = params.getModelCode();

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
    @Override
    public HashMap<String, Object> doSearchPushSet(AuthServerDTO params)
            throws CustomException{

        String stringObject = null;
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
    @Override
    public ResponseEntity<?> doDelHouseholder(AuthServerDTO params)
            throws CustomException{

        String stringObject = null;
        ApiResponse.Data data = new ApiResponse.Data();
        String msg = null;
        AuthServerDTO member = null;
        String nextHouseholdId = null;
        int result = 0;
        int result1 = 0;
        int result2 = 0;
            try {

                List<AuthServerDTO> deviceIds = memberMapper.getDeviceIdByUserId(params.getUserId());
                List<AuthServerDTO> members = memberMapper.getHouseMembersByUserId(deviceIds);

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

                    result = memberMapper.delHouseMember(params.getUserId());
                    if(result <= 0) stringObject = "N";

                    result1 = memberMapper.updateHouseholdTbrOprUser(nextHouseholdId);
                    result2 = memberMapper.updateHouseholdTbrOprUserDevice(nextHouseholdId);

                } else stringObject = "N";
                
                if(stringObject.equals("Y")) msg = "사용자(세대주) 탈퇴  성공";
                else msg = "사용자(세대주) 탈퇴  실패";

                data.setResult("Y".equalsIgnoreCase(stringObject) ?
                        ApiResponse.ResponseType.HTTP_200 :
                        ApiResponse.ResponseType.CUSTOM_1003, msg);
                return new ResponseEntity<>(data, HttpStatus.OK);

            } catch (CustomException e){
                System.out.println(e.getMessage());
                e.printStackTrace();
            }
        return null;
    }

    /** 홈IoT 서비스 회원 탈퇴 */
    @Override
    public ResponseEntity<?> doWirhdrawal(AuthServerDTO params)
            throws CustomException{

        String stringObject = null;
        ApiResponse.Data data = new ApiResponse.Data();
        String msg = null;
        String userId = params.getUserId();
        String member = null;
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
        String stringObject = "N";
        String msg = null;

        String userId = params.getUserId();

        List<AuthServerDTO> deviceIdAndAuthKey = null;
        List<AuthServerDTO> deviceAuthCheck = null;

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
            if(stringObject.equals("Y")) msg = "홈 IoT 컨트롤러 인증 성공";
            else msg = "홈 IoT 컨트롤러 인증 실패";

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

    /** 홈 IoT 최초 등록 인증 */
    @Override
    public ResponseEntity<?> doFirstDeviceAuthCheck(AuthServerDTO params)
            throws CustomException{

        ApiResponse.Data result = new ApiResponse.Data();
        String stringObject = "Y";
        String msg = null;

        String userId = params.getUserId();

        List<AuthServerDTO> deviceIdAndAuthKey = null;
        List<AuthServerDTO> deviceAuthCheck = null;
        AuthServerDTO deviceTempAuthCheck = null;

        try{
            deviceIdAndAuthKey = deviceMapper.getDeviceAuthCheckValuesByUserId(userId);
            if(deviceIdAndAuthKey.isEmpty()){
                stringObject = "N";
            }else {
                deviceAuthCheck = deviceMapper.deviceAuthCheck(deviceIdAndAuthKey);
                if(deviceAuthCheck.isEmpty()) {
                    stringObject = "N";
                } else {
                    deviceTempAuthCheck = deviceMapper.deviceTempAuthCheck(deviceIdAndAuthKey);
                    if(deviceTempAuthCheck == null){
                        stringObject = "N";
                    } else stringObject = "Y";
                }
            }

            if(stringObject.equals("Y")) msg = "홈 IoT 최초 등록 인증 성공";
            else msg = "홈 IoT 최초 등록 인증 실패";

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

        String stringObject = null;
        ApiResponse.Data data = new ApiResponse.Data();
        String msg = null;
        String member = null;

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

            if(stringObject.equals("Y")) msg = "홈 IoT 컨트롤러 삭제(회원 매핑 삭제) 성공";
            else msg = "홈 IoT 컨트롤러 삭제(회원 매핑 삭제) 실패";

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
        String stringObject = null;
        String msg = null;
        int result = 0;
        try {

            result = deviceMapper.changeDeviceNickname(params);

            if(result <= 0) stringObject = "N";
            else stringObject = "Y";

            if(stringObject.equals("Y")) msg = "기기 별칭 수정 성공";
            else msg = "기기 별칭 수정 실패";

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

    /** 기기 밝기 조절 */
    @Override
    public ResponseEntity<?> doBrightnessControl(AuthServerDTO params) throws CustomException {

        ApiResponse.Data data = new ApiResponse.Data();
        String stringObject = null;
        String msg = null;
        AuthServerDTO serialNumber = null;
        Map<String, Object> conMap = new HashMap<>();
        MobiusResponse mobiusResponse = null;
        try{
            serialNumber = deviceMapper.getSerialNumberBydeviceId(params.getDeviceId());

            conMap.put("controlAuthKey", params.getControlAuthKey());
            conMap.put("deviceId", params.getDeviceId());
            conMap.put("deviceType", params.getDeviceType());
            conMap.put("modelCode", params.getModelCode());
            conMap.put("brightnessLevel", params.getBrightnessLevel());

            ObjectMapper objectMapper = new ObjectMapper();
            String jsonString = objectMapper.writeValueAsString(conMap);

            mobiusResponse = mobiusService.createCin(serialNumber.getSerialNumber(), params.getUserId(), jsonString);
            if (Objects.equals(mobiusResponse.getResponseCode(), "201")) stringObject = "Y";
            else stringObject = "N";

            if(stringObject.equals("Y")) msg = "기기 별칭 수정 성공";
            else msg = "기기 별칭 수정 실패";

            data.setMobiusResponseCode((mobiusResponse.getResponseCode()));
            data.setResult("Y".equalsIgnoreCase(stringObject) ?
                    ApiResponse.ResponseType.HTTP_200 :
                    ApiResponse.ResponseType.CUSTOM_1003, msg);
            return new ResponseEntity<>(data, HttpStatus.OK);
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
}
