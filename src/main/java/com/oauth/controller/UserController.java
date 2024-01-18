package com.oauth.controller;

import com.oauth.dto.member.MemberDTO;
import com.oauth.mapper.MemberMapper;
import com.oauth.response.ApiResponse;
import com.oauth.service.UserService;
import com.oauth.utils.Common;
import com.oauth.utils.CustomException;
import com.oauth.utils.Validator;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.swing.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;

@RequestMapping("/users/v1")
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class UserController {

    @Autowired
    private MemberMapper memberMapper;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private Common common;
    @Autowired
    private UserService userService;

    public static final List<String> oldModels = Arrays.asList("oldModel1", "oldModel2");

    /**
     * 인증 서버 활성화 체크
     */
    @ResponseBody
    @RequestMapping(value="/auth/healthCheck", method={RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_JSON_VALUE)
    public String check(HttpServletRequest request, HttpServletResponse response) throws Exception {
        return "{\"resultCode\":\"OK\"}";
    }

    /** 회원 로그인 */
    @PostMapping(value = "/login")
    @ResponseBody
    public ResponseEntity<?> doLogin(HttpSession session, HttpServletRequest request, @ModelAttribute MemberDTO params, HttpServletResponse response)
        throws CustomException {

        String logStep = "[회원 로그인]";

        if(Validator.isNullOrEmpty(params.getUserId()) || Validator.isNullOrEmpty(params.getUserPassword())) {
            System.out.println(logStep);
            throw new CustomException("UserId OR UserPassword is NULL");
        }

        return userService.doLogin(params.getUserId(), params.getUserPassword());
    }

    /** 회원가입 */
    @PostMapping(value = "/regist")
    @ResponseBody
    public ResponseEntity<?> doRegist(HttpSession session, HttpServletRequest request, @ModelAttribute MemberDTO params, HttpServletResponse response)
        throws CustomException {

        String logStep = "[회원 가입]";

        if(Validator.isNullOrEmpty(params.getHp()) ||
                Validator.isNullOrEmpty(params.getUserNickname()) ||
                Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getUserPassword())){
            System.out.println(logStep);
            throw new CustomException("HP, UserNickname, UserId, Password NULL OR EMPTY ERROR");
        }
        return userService.doRegist(params);
    }

    /** 회원중복 체크 */
    @PostMapping(value = "/duplicationCheck")
    @ResponseBody
    public ResponseEntity<?> doDuplicationCheck(HttpServletRequest request, @ModelAttribute MemberDTO params)
            throws CustomException{

        String logStep = "[ID 중복 확인]";

        if(Validator.isNullOrEmpty(params.getUserId())){
            System.out.println(logStep);
            throw new CustomException("UserId NULL OR EMPTY ERROR");
        }

        return userService.doDuplicationCheck(params);
    }
//
//    /** ID 찾기 */
//    @PostMapping(value = "/idFind")
//    @ResponseBody
//    public ResponseEntity<?> doIdFind(HttpServletRequest request, @ModelAttribute MemberDTO params)
//            throws Exception{
//
//        String stringObject = null;
//        ApiResponse.Data data = new ApiResponse.Data();
//
//        try{
//            String userHp = params.getHp();
//            String deviceType = params.getDeviceType();
//            String modelCode = params.getModelCode();
//            String deviceId = params.getDeviceId();
//
//            List<MemberDTO> member = null;
//
//
//            // 구형 모델의 경우
//            if(modelCode.equals(oldModels.get(0)) || modelCode.equals(oldModels.get(1))){
//                member = memberMapper.getUserByHp(userHp);
//            } else {
//                member = memberMapper.getUserByDeviceId(deviceId);
//
//            }
//
//            if(member.isEmpty()) stringObject = "N";
//            else stringObject = "Y";
//
//            List<String> userId = Common.extractJson(member.toString(), "userId");
//            if(userId.isEmpty()) userId = null;
//
//            data.setUserIdList(userId);
//            data.setResult("Y".equalsIgnoreCase(stringObject)
//                    ? ApiResponse.ResponseType.HTTP_200
//                    : ApiResponse.ResponseType.CUSTOM_2002);
//            return new ResponseEntity<>(data, HttpStatus.OK);
//        }catch (Exception e){
//            e.printStackTrace();
//        }
//        return null;
//    }
//
//    /** 비밀번호 찾기 - 초기화 */
//    @PostMapping(value = "/resetPassword")
//    @ResponseBody
//    public ResponseEntity<?> doResetPassword(HttpServletRequest request, @ModelAttribute MemberDTO params)
//            throws Exception{
//
//        String stringObject = null;
//        ApiResponse.Data data = new ApiResponse.Data();
//
//        try{
//            String userId = params.getUserId();
//            String userHp = params.getHp();
//            String deviceType = params.getDeviceType();
//            String modelCode = params.getModelCode();
//            String deviceId = params.getDeviceId();
//
//            MemberDTO member = null;
//
//            // 구형 모델의 경우
//            if(modelCode.equals(oldModels.get(0)) || modelCode.equals(oldModels.get(1))){
//                System.out.println("getUserByUserIdAndHp Called");
//                member = memberMapper.getUserByUserIdAndHp(params);
//            } else {
//                System.out.println("getUserByUserIdAndHpAndDeviceId Called");
//                member = memberMapper.getUserByUserIdAndHpAndDeviceId(params);
//            }
//
//            if(member == null) stringObject = "N";
//            else stringObject = "Y";
//
//            System.out.println("stringObject: " + stringObject);
//            data.setResult("Y".equalsIgnoreCase(stringObject)
//                    ? ApiResponse.ResponseType.HTTP_200
//                    : ApiResponse.ResponseType.CUSTOM_2002);
//            return new ResponseEntity<>(data, HttpStatus.OK);
//
//        }catch (Exception e){
//            e.printStackTrace();
//        }
//        return null;
//    }
//
//    /** 비밀번호 변경 - 생성 */
//    @PostMapping(value = "/changePassword")
//    @ResponseBody
//    public ResponseEntity<?> doChangePassword(HttpServletRequest request, @ModelAttribute MemberDTO params)
//            throws Exception{
//
//        String stringObject = null;
//        ApiResponse.Data data = new ApiResponse.Data();
//
//        try{
//            int result = memberMapper.updatePassword(params);
//
//            if(result > 0) stringObject = "Y";
//            else stringObject = "N";
//
//            data.setResult("Y".equalsIgnoreCase(stringObject)
//                    ? ApiResponse.ResponseType.HTTP_200
//                    : ApiResponse.ResponseType.CUSTOM_2002);
//
//            return new ResponseEntity<>(data, HttpStatus.OK);
//        }catch (Exception e){
//            e.printStackTrace();
//        }
//        return null;
//    }
//
//    /** 사용자정보 조회 */
//    @PostMapping(value = "/search")
//    @ResponseBody
//    public ResponseEntity<?> doSearch(HttpServletRequest request, @ModelAttribute MemberDTO params)
//            throws Exception{
//
//        String stringObject = null;
//        ApiResponse.Data data = new ApiResponse.Data();
//
//        try{
//            String userId = params.getUserId();
//            MemberDTO member = memberMapper.getUserByUserId(userId);
//
//            if (member != null) {
//                data.setUserId(member.getUserId());
//                data.setUserNickname(member.getUserNickname());
//                data.setHp(member.getHp());
//                stringObject = "Y";
//            } else stringObject = "N";
//
//            data.setResult("Y".equalsIgnoreCase(stringObject)
//                    ? ApiResponse.ResponseType.HTTP_200
//                    : ApiResponse.ResponseType.CUSTOM_2002);
//            return new ResponseEntity<>(data, HttpStatus.OK);
//
//        }catch (Exception e){
//            e.printStackTrace();
//        }
//        return null;
//    }
//
//    /** 회원 별칭(이름) 및 전화번호 변경 */
//    @PostMapping(value = "/updateUserNicknameHp")
//    @ResponseBody
//    public ResponseEntity<?> doUpdateUserNicknameHp(HttpServletRequest request, @ModelAttribute MemberDTO params)
//            throws Exception{
//
//        String stringObject = null;
//        ApiResponse.Data data = new ApiResponse.Data();
//
//        try{
//            String userId = params.getUserId();
//            String userPassword = params.getUserPassword();
//            String oldHp = params.getOldHp();
//            String newHp = params.getNewHp();
//            String userNickname = params.getUserNickname();
//
//            System.out.println("userId: " + userId);
//            System.out.println("userPassword: " + userId);
//            System.out.println("oldHp: " + oldHp);
//            System.out.println("newHp: " + newHp);
//            System.out.println("userNickname: " + userNickname);
//
//            MemberDTO pwCheck = memberMapper.passwordCheck(userPassword);
//            System.out.println("pwCheck: " + pwCheck);
//            // TODO: 예외처리 하여 불일치 PW 알림
//            if(pwCheck == null) return null;
//
//            if(newHp == null) params.setNewHp(oldHp);
//            else if(oldHp.equals(newHp)) return null; // TODO: 예외처리 하여 동일한 전화번호 변경 알림
//
//            int result = memberMapper.updateUserNicknameAndHp(params);
//            System.out.println("result: " + result);
//            if(result > 0) stringObject = "Y";
//            else stringObject = "N";
//
//            data.setResult("Y".equalsIgnoreCase(stringObject)
//                    ? ApiResponse.ResponseType.HTTP_200
//                    : ApiResponse.ResponseType.CUSTOM_2002);
//
//            return new ResponseEntity<>(data, HttpStatus.OK);
//        }catch (Exception e){
//            e.printStackTrace();
//        }
//        return null;
//    }
//
//    /** 비밀번호 변경 - 로그인시 */
//    @PostMapping(value = "/updatePassword")
//    @ResponseBody
//    public ResponseEntity<?> doUpdatePassword(HttpServletRequest request, @ModelAttribute MemberDTO params)
//            throws Exception{
//
//        String stringObject = null;
//        ApiResponse.Data data = new ApiResponse.Data();
//
//        try{
//
//            String accessToken = params.getAccessToken();
//            String userId = params.getUserId();
//            String oldPassword = params.getOldPassword();
//            String newPassword = params.getNewPassword();
//
//            System.out.println("accessToken: " + accessToken);
//            System.out.println("userId: " + userId);
//            System.out.println("oldPassword: " + oldPassword);
//            System.out.println("newPassword: " + newPassword);
//
//            params.setUserPassword(newPassword);
//
//            MemberDTO pwCheck = memberMapper.passwordCheck(oldPassword);
//            // TODO: 예외처리 하여 불일치 PW 알림
//            if(pwCheck == null) return null;
//
//            // AccessToken 검증
//            boolean result = common.tokenVerify(params);
//            System.out.println("result: " + result);
//
//
//            if(!result) stringObject = "N";
//            else {
//                int pwChangeResult = memberMapper.updatePassword(params);
//                if(pwChangeResult > 0)  stringObject = "Y";
//                else stringObject = "N";
//            }
//
//            data.setResult("Y".equalsIgnoreCase(stringObject)
//                    ? ApiResponse.ResponseType.HTTP_200
//                    : ApiResponse.ResponseType.CUSTOM_2002);
//
//            return new ResponseEntity<>(data, HttpStatus.OK);
//        }catch (Exception e){
//            e.printStackTrace();
//        }
//        return null;
//    }
//
//    /** 사용자(세대원) 정보 조회 */
//    @PostMapping(value = "/viewHouseholdMemebers")
//    @ResponseBody
//    public ResponseEntity<?> doViewHouseholdMemebers(HttpServletRequest request, @ModelAttribute MemberDTO params)
//            throws Exception{
//
//        String stringObject = null;
//        ApiResponse.Data data = new ApiResponse.Data();
//        try {
//
//            // AccessToken 검증
//            boolean result = common.tokenVerify(params);
//            System.out.println("result: " + result);
//
//            if(!result) stringObject = "N";
//            else stringObject = "Y";
//
//
//            String accessToken = params.getAccessToken();
//            String userId = params.getUserId();
//
//            // Device Set 생성
//            Set<String> userIds = new HashSet<>();
//            List<ApiResponse.Data.User> user = new ArrayList<>();
//
//            List<MemberDTO> deviceIds = memberMapper.getDeviceIdByUserId(userId);
//            List<MemberDTO> members = memberMapper.getHouseMembersByUserId(deviceIds);
//
//            // List에서 요청자 userId 제거
////            members.stream()
////                    .filter(x -> x.getUserId()
////                            .equals(userId))
////                    .collect(Collectors.toList())
////                    .forEach(members::remove);
//
//
//            List<MemberDTO> memberStream = Common.deduplication(members, MemberDTO::getUserId);
//
//            List<String> userIdList = Common.extractJson(memberStream.toString(), "userId");
//            List<String> userNicknameList = Common.extractJson(memberStream.toString(), "userNickname");
//            List<String> householderdList = Common.extractJson(memberStream.toString(), "householder");
//
//            // Mapper실행 후 사용자가 가지고 있는 Member 개수
//            int numMembers = memberStream.size();
//
//            if(userIdList != null && userNicknameList != null && householderdList != null){
//                // Member 추가
//                for (int i = 0; i < numMembers; i++) {
//                    ApiResponse.Data.User users = Common.createUsers(
//                            userIdList.get(i),
//                            userNicknameList.get(i),
//                            householderdList.get(i),
//                            userIds);
//                    user.add(users);
//                }
//            }
//
//            data.setUser(user);
//            data.setResult("Y".equalsIgnoreCase(stringObject)
//                    ? ApiResponse.ResponseType.HTTP_200
//                    : ApiResponse.ResponseType.CUSTOM_2002);
//
//            return new ResponseEntity<>(data, HttpStatus.OK);
//        }catch (Exception e){
//            e.printStackTrace();
//        }
//        return null;
//    }
//
//    /** 사용자 추가 - 초대 */
//    @PostMapping(value = "/addUser")
//    @ResponseBody
//    public ResponseEntity<?> doAddUser(HttpServletRequest request, @ModelAttribute MemberDTO params)
//            throws Exception{
//
//        String stringObject = null;
//        ApiResponse.Data data = new ApiResponse.Data();
//
//        try {
//            String accessToken = params.getAccessToken();
//            String requestUserId = params.getRequestUserId();
//            String responseUserId = params.getResponseUserId();
//            String responseHp = params.getResponseHp();
//            String inviteStartDate = params.getInviteStartDate();
//
//            int result = memberMapper.inviteHouseMember(params);
//
//            if(result <= 0) stringObject = "N";
//            else stringObject = "Y";
//
//            data.setResult("Y".equalsIgnoreCase(stringObject)
//                    ? ApiResponse.ResponseType.HTTP_200
//                    : ApiResponse.ResponseType.CUSTOM_2002);
//
//            return new ResponseEntity<>(data, HttpStatus.OK);
//
//        }catch (Exception e){
//            e.printStackTrace();
//        }
//        return null;
//    }
//
//    /** 사용자 초대 - 수락여부 */
//    @PostMapping(value = "/inviteStatus")
//    @ResponseBody
//    public ResponseEntity<?> doInviteStatus(HttpServletRequest request, @ModelAttribute MemberDTO params)
//            throws Exception{
//
//        String stringObject = null;
//        ApiResponse.Data data = new ApiResponse.Data();
//
//        try {
//
//            String accessToken = params.getAccessToken();
//            String requestUserId = params.getRequestUserId();
//            String responseHp = params.getResponseHp();
//            String responseUserId = params.getResponseUserId();
//            String inviteAcceptYn = params.getInviteAcceptYn();
//
//            /**
//             * 수락시: responseUserId 사용자는 requestUserId 사용자가 가진 DeviceId에 자동으로 mapping된다.
//             * 거부시: TBR_OPR_USER_INVITE_STATUS 수락여부 항목 N UPDATE
//             * 수락시 쿼리 흐름:
//             * 1. TBR_OPR_USER_INVITE_STATUS
//             * 2. TBR_OPR_USER_DEVICE에서 requestUserId 검색
//             * 3. 2번 출력값으로 TBR_OPR_USER_DEVICE에 responseUserId INSERT
//             * */
//
//            List<MemberDTO> member = null;
//            int insertNewHouseMemberResult;
//            int acceptInviteResult;
//            if(inviteAcceptYn.equals("Y")){
//
//                acceptInviteResult = memberMapper.acceptInvite(params);
//
//                member = memberMapper.getDeviceIdByUserId(requestUserId);
//
//                Common.updateMemberDTOList(member, "responseUserId", responseUserId);
//                Common.updateMemberDTOList(member, "householder", "N");
//
//                System.out.println(member);
//                insertNewHouseMemberResult = memberMapper.insertNewHouseMember(member);
//
//                if(insertNewHouseMemberResult > 0 && acceptInviteResult > 0) stringObject = "Y";
//                else stringObject = "N";
//
//            } else if(inviteAcceptYn.equals("N")){
//
//                acceptInviteResult = memberMapper.acceptInvite(params);
//                if(acceptInviteResult >0) stringObject = "Y";
//                else stringObject = "N";
//
//            } else {
//                // TODO: UNKNOWN ERROR 추가
//                return null;
//            }
//
//            data.setResult("Y".equalsIgnoreCase(stringObject)
//                    ? ApiResponse.ResponseType.HTTP_200
//                    : ApiResponse.ResponseType.CUSTOM_2002);
//
//            return new ResponseEntity<>(data, HttpStatus.OK);
//        }catch (Exception e){
//            e.printStackTrace();
//        }
//        return null;
//    }
//
//    /** 사용자 초대 - 기록 조회 */
//    @PostMapping(value = "/inviteListView")
//    @ResponseBody
//    public ResponseEntity<?> doInviteListView(HttpServletRequest request, @ModelAttribute MemberDTO params)
//            throws Exception{
//
//        String stringObject = null;
//        ApiResponse.Data data = new ApiResponse.Data();
//
//        try {
//
//            String accessToken = params.getAccessToken();
//            String userId = params.getUserId();
//
//            List<MemberDTO> invitationInfo = memberMapper.getInvitationList(userId);
//
//            if(invitationInfo.isEmpty()) stringObject = "N";
//            else stringObject = "Y";
//
//            // Device Set 생성
//            Set<String> invitationIds = new HashSet<>();
//            List<ApiResponse.Data.Invitation> inv = new ArrayList<>();
//
//            List<String> invitationIdxList = Common.extractJson(invitationInfo.toString(), "invitationIdx");
//            List<String> inviteAcceptYnList = Common.extractJson(invitationInfo.toString(), "inviteAcceptYn");
//            List<String> requestUserIdList = Common.extractJson(invitationInfo.toString(), "requestUserId");
//            List<String> requestUserNickList = Common.extractJson(invitationInfo.toString(), "requestUserNick");
//            List<String> responseUserIdList = Common.extractJson(invitationInfo.toString(), "responseUserId");
//            List<String> responseUserNickList = Common.extractJson(invitationInfo.toString(), "responseUserNick");
//            List<String> responseHpList = Common.extractJson(invitationInfo.toString(), "responseHp");
//            List<String> inviteStartDateList = Common.extractJson(invitationInfo.toString(), "inviteStartDate");
//            List<String> inviteEndDateList = Common.extractJson(invitationInfo.toString(), "inviteEndDate");
//
//            // Mapper실행 후 사용자가 가지고 있는 Invitation 개수
//            int numInvitations = invitationInfo.size();
//
//            if(invitationIdxList != null
//                    && inviteAcceptYnList != null
//                    && requestUserIdList != null
//                    && requestUserNickList != null
//                    && responseUserIdList != null
//                    && responseUserNickList != null
//                    && responseHpList != null
//                    && inviteStartDateList != null
//                    && inviteEndDateList != null
//                    ){
//                // Member 추가
//                for (int i = 0; i < numInvitations; i++) {
//                    ApiResponse.Data.Invitation invitations = Common.createInvitations(
//                            invitationIdxList.get(i),
//                            inviteAcceptYnList.get(i),
//                            requestUserIdList.get(i),
//                            requestUserNickList.get(i),
//                            responseUserIdList.get(i),
//                            responseUserNickList.get(i),
//                            responseHpList.get(i),
//                            inviteStartDateList.get(i),
//                            inviteEndDateList.get(i),
//                            invitationIds);
//                    inv.add(invitations);
//                }
//            }
//
//            data.setResult("Y".equalsIgnoreCase(stringObject) ? ApiResponse.ResponseType.HTTP_200 : ApiResponse.ResponseType.CUSTOM_1003);
//            data.setInvitation(inv);
//            return new ResponseEntity<>(data, HttpStatus.OK);
//        }catch (Exception e){
//            e.printStackTrace();
//        }
//        return null;
//    }
//
//    /** 사용자(세대원) - 강제탈퇴 */
//    @PostMapping(value = "/delHouseholdMembers")
//    @ResponseBody
//    public ResponseEntity<?> doDelHouseholdMembers(HttpServletRequest request, @ModelAttribute MemberDTO params)
//            throws Exception{
//
//        String stringObject = null;
//        ApiResponse.Data data = new ApiResponse.Data();
//
//        try {
//            String userHp = params.getHp();
//            String userNickname = params.getUserNickname();
//            String accessToken = params.getAccessToken();
//            String userId = params.getUserId();
//
//            int result = memberMapper.delHouseMember(params);
//
//            if(result > 0) stringObject = "Y";
//            else stringObject = "N";
//
//            data.setResult("Y".equalsIgnoreCase(stringObject) ? ApiResponse.ResponseType.HTTP_200 : ApiResponse.ResponseType.CUSTOM_1003);
//            return new ResponseEntity<>(data, HttpStatus.OK);
//        }catch (Exception e){
//            e.printStackTrace();
//        }
//        return null;
//    }

}
