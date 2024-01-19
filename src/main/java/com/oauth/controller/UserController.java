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
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
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
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
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
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }

        return userService.doDuplicationCheck(params);
    }

    /** ID 찾기 */
    @PostMapping(value = "/idFind")
    @ResponseBody
    public ResponseEntity<?> doIdFind(HttpServletRequest request, @ModelAttribute MemberDTO params)
            throws CustomException{

        String logStep = "[ID 찾기]";

        if(Validator.isNullOrEmpty(params.getHp()) ||
           Validator.isNullOrEmpty(params.getDeviceType()) ||
           Validator.isNullOrEmpty(params.getModelCode()) ||
           Validator.isNullOrEmpty(params.getDeviceId())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }

        return userService.doIdFind(params);

    }

    /** 비밀번호 찾기 - 초기화 */
    @PostMapping(value = "/resetPassword")
    @ResponseBody
    public ResponseEntity<?> doResetPassword(HttpServletRequest request, @ModelAttribute MemberDTO params)
            throws CustomException{

        String logStep = "[비밀번호 찾기 - 초기화]";

        if(Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getHp()) ||
                Validator.isNullOrEmpty(params.getDeviceType()) ||
                Validator.isNullOrEmpty(params.getModelCode())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }

        return userService.doResetPassword(params);
    }

    /** 비밀번호 변경 - 생성 */
    @PostMapping(value = "/changePassword")
    @ResponseBody
    public ResponseEntity<?> doChangePassword(HttpServletRequest request, @ModelAttribute MemberDTO params)
            throws CustomException{

        String logStep = "[비밀번호 변경 - 생성]";

        if(Validator.isNullOrEmpty(params.getUserId()) || Validator.isNullOrEmpty(params.getUserPassword())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }
        return userService.doChangePassword(params);
    }

    /** 사용자정보 조회 */
    @PostMapping(value = "/search")
    @ResponseBody
    public ResponseEntity<?> doSearch(HttpServletRequest request, @ModelAttribute MemberDTO params)
            throws CustomException{

        String logStep = "[비밀번호 변경 - 생성]";

        if(Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getHp()) ||
                Validator.isNullOrEmpty(params.getDeviceType()) ||
                Validator.isNullOrEmpty(params.getModelCode())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }

        return userService.doSearch(params);
    }

    /** 회원 별칭(이름) 및 전화번호 변경 */
    @PostMapping(value = "/updateUserNicknameHp")
    @ResponseBody
    public ResponseEntity<?> doUpdateUserNicknameHp(HttpServletRequest request, @ModelAttribute MemberDTO params)
            throws CustomException{

        String logStep = "[회원 별칭(이름) 및 전화번호 변경]";

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getUserPassword()) ||
                Validator.isNullOrEmpty(params.getOldHp()) ||
                Validator.isNullOrEmpty(params.getUserNickname())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }

        return userService.doUpdateUserNicknameHp(params);
    }

    /** 비밀번호 변경 - 로그인시 */
    @PostMapping(value = "/updatePassword")
    @ResponseBody
    public ResponseEntity<?> doUpdatePassword(HttpServletRequest request, @ModelAttribute MemberDTO params)
            throws CustomException{

        String logStep = "[회원 별칭(이름) 및 전화번호 변경]";

        if(Validator.isNullOrEmpty(params.getAccessToken()) ||
                Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getOldPassword()) ||
                Validator.isNullOrEmpty(params.getNewPassword())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }

        return userService.doUpdatePassword(params);

    }

    /** 사용자(세대원) 정보 조회 */
    @PostMapping(value = "/viewHouseholdMemebers")
    @ResponseBody
    public ResponseEntity<?> doViewHouseholdMemebers(HttpServletRequest request, @ModelAttribute MemberDTO params)
            throws CustomException{

        String logStep = "[사용자(세대원) 정보 조회]";

        if(Validator.isNullOrEmpty(params.getAccessToken()) || Validator.isNullOrEmpty(params.getUserId())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }

        return userService.doViewHouseholdMemebers(params);
    }

    /** 사용자 추가 - 초대 */
    @PostMapping(value = "/addUser")
    @ResponseBody
    public ResponseEntity<?> doAddUser(HttpServletRequest request, @ModelAttribute MemberDTO params)
            throws CustomException{

        String logStep = "[사용자 추가 - 초대]";

        if(Validator.isNullOrEmpty(params.getAccessToken()) ||
                Validator.isNullOrEmpty(params.getRequestUserId()) ||
                Validator.isNullOrEmpty(params.getResponseHp()) ||
                Validator.isNullOrEmpty(params.getResponseUserId()) ||
                Validator.isNullOrEmpty(params.getInviteStartDate())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }

        return userService.doAddUser(params);
    }

    /** 사용자 초대 - 수락여부 */
    @PostMapping(value = "/inviteStatus")
    @ResponseBody
    public ResponseEntity<?> doInviteStatus(HttpServletRequest request, @ModelAttribute MemberDTO params)
            throws CustomException{

        String logStep = "[사용자 초대 - 수락여부]";

        if(Validator.isNullOrEmpty(params.getAccessToken()) ||
                Validator.isNullOrEmpty(params.getRequestUserId()) ||
                Validator.isNullOrEmpty(params.getResponseHp()) ||
                Validator.isNullOrEmpty(params.getResponseUserId()) ||
                Validator.isNullOrEmpty(params.getResponseUserNick()) ||
                Validator.isNullOrEmpty(params.getInviteAcceptYn()) ||
                Validator.isNullOrEmpty(params.getInvitationIdx())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }

        return userService.doInviteStatus(params);
    }

    /** 사용자 초대 - 목록 조회 */
    @PostMapping(value = "/inviteListView")
    @ResponseBody
    public ResponseEntity<?> doInviteListView(HttpServletRequest request, @ModelAttribute MemberDTO params)
            throws CustomException{

        String logStep = "[회원 별칭(이름) 및 전화번호 변경]";

        if(Validator.isNullOrEmpty(params.getAccessToken()) || Validator.isNullOrEmpty(params.getUserId())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }

        return userService.doInviteListView(params);
    }

    /** 사용자(세대원) - 강제탈퇴 */
    @PostMapping(value = "/delHouseholdMembers")
    @ResponseBody
    public ResponseEntity<?> doDelHouseholdMembers(HttpServletRequest request, @ModelAttribute MemberDTO params)
            throws Exception{

        String logStep = "[사용자(세대원) - 강제탈퇴]";

        if(Validator.isNullOrEmpty(params.getHp()) ||
                Validator.isNullOrEmpty(params.getUserNickname()) ||
                Validator.isNullOrEmpty(params.getAccessToken()) ||
                Validator.isNullOrEmpty(params.getUserId())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }
        return userService.doDelHouseholdMembers(params);
    }

}
