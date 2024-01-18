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
import org.springframework.web.bind.annotation.ModelAttribute;

import javax.servlet.http.HttpServletRequest;
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

    public static final List<String> oldModels = Arrays.asList("oldModel1", "oldModel2");

    /** 회원 로그인 */
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
    public ResponseEntity<?> doRegist(MemberDTO params) throws CustomException {

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
    public ResponseEntity<?> doDuplicationCheck(MemberDTO params) throws CustomException {

        String stringObject = null;
        ApiResponse.Data data = new ApiResponse.Data();
        String userId = params.getUserId();
        String msg = null;

        try{
            MemberDTO member = memberMapper.getUserByUserId(userId);

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
    public ResponseEntity<?> doIdFind(MemberDTO params) throws CustomException {

        String stringObject = null;
        ApiResponse.Data data = new ApiResponse.Data();

        String userHp = params.getHp();
        String deviceType = params.getDeviceType();
        String modelCode = params.getModelCode();
        String deviceId = params.getDeviceId();

        String msg = null;

        List<MemberDTO> member = null;

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
    public ResponseEntity<?> doResetPassword(MemberDTO params) throws CustomException {
        String stringObject = null;
        ApiResponse.Data data = new ApiResponse.Data();

        String userId = params.getUserId();
        String userHp = params.getHp();
        String deviceType = params.getDeviceType();
        String modelCode = params.getModelCode();
        String deviceId = params.getDeviceId();

        MemberDTO member = null;

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
    public ResponseEntity<?> doChangePassword(MemberDTO params) throws CustomException {
        String stringObject = null;
        ApiResponse.Data data = new ApiResponse.Data();

        String msg = null;

        try{
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
    public ResponseEntity<?> doSearch(MemberDTO params) throws CustomException {
        String stringObject = null;
        ApiResponse.Data data = new ApiResponse.Data();
        String msg = null;
        String userId = params.getUserId();

        try{
            MemberDTO member = memberMapper.getUserByUserId(userId);

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
    public ResponseEntity<?> doUpdateUserNicknameHp(MemberDTO params) throws CustomException{
        String stringObject = null;
        ApiResponse.Data data = new ApiResponse.Data();
        String msg = null;

        String userId = params.getUserId();
        String userPassword = params.getUserPassword();
        String oldHp = params.getOldHp();
        String newHp = params.getNewHp();
        String userNickname = params.getUserNickname();

        try{

            MemberDTO pwCheck = memberMapper.passwordCheck(userPassword);
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
    public ResponseEntity<?> doUpdatePassword(MemberDTO params) throws CustomException{

        String stringObject = null;
        ApiResponse.Data data = new ApiResponse.Data();

        String accessToken = params.getAccessToken();
        String userId = params.getUserId();
        String oldPassword = params.getOldPassword();
        String newPassword = params.getNewPassword();

        String msg = null;

        try{
            params.setUserPassword(newPassword);

            MemberDTO pwCheck = memberMapper.passwordCheck(oldPassword);

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
    public ResponseEntity<?> doViewHouseholdMemebers(MemberDTO params) throws CustomException{
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

            List<MemberDTO> deviceIds = memberMapper.getDeviceIdByUserId(userId);
            List<MemberDTO> members = memberMapper.getHouseMembersByUserId(deviceIds);

            // List에서 요청자 userId 제거
//            members.stream()
//                    .filter(x -> x.getUserId()
//                            .equals(userId))
//                    .collect(Collectors.toList())
//                    .forEach(members::remove);


            List<MemberDTO> memberStream = Common.deduplication(members, MemberDTO::getUserId);

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
    public ResponseEntity<?> doAddUser(MemberDTO params)
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
    public ResponseEntity<?> doInviteStatus(MemberDTO params)
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

            List<MemberDTO> member = null;
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
    public ResponseEntity<?> doInviteListView(MemberDTO params)
            throws CustomException{

        String stringObject = null;
        ApiResponse.Data data = new ApiResponse.Data();
        String msg = null;
        try {

            String accessToken = params.getAccessToken();
            String userId = params.getUserId();

            List<MemberDTO> invitationInfo = memberMapper.getInvitationList(userId);

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
    public ResponseEntity<?> doDelHouseholdMembers(MemberDTO params)
            throws CustomException{

        String stringObject = null;
        ApiResponse.Data data = new ApiResponse.Data();

        String msg = null;

        String userHp = params.getHp();
        String userNickname = params.getUserNickname();
        String accessToken = params.getAccessToken();
        String userId = params.getUserId();

        try {
            int result = memberMapper.delHouseMember(params);

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
}
