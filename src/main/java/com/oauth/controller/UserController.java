package com.oauth.controller;

import com.oauth.dto.AuthServerDTO;
import com.oauth.mapper.MemberMapper;
import com.oauth.service.impl.UserServiceImpl;
import com.oauth.utils.Common;
import com.oauth.utils.CustomException;
import com.oauth.utils.Validator;
import lombok.RequiredArgsConstructor;
import net.minidev.json.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.sql.SQLException;
import java.util.*;

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
    private UserServiceImpl userService;

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
    public ResponseEntity<?> doLogin(HttpSession session, HttpServletRequest request, @ModelAttribute AuthServerDTO params, HttpServletResponse response)
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
    public ResponseEntity<?> doRegist(HttpSession session, HttpServletRequest request, @ModelAttribute AuthServerDTO params, HttpServletResponse response)
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
    public ResponseEntity<?> doDuplicationCheck(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
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
    public ResponseEntity<?> doIdFind(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
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
    public ResponseEntity<?> doResetPassword(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
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
    public ResponseEntity<?> doChangePassword(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
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
    public ResponseEntity<?> doSearch(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
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
    public ResponseEntity<?> doUpdateUserNicknameHp(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
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
    public ResponseEntity<?> doUpdatePassword(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException{

        String logStep = "[회원 별칭(이름) 및 전화번호 변경]";

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getOldPassword()) ||
                Validator.isNullOrEmpty(params.getNewPassword())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }

        return userService.doUpdatePassword(params);

    }

    /** 사용자(세대원) 정보 조회 */
    @PostMapping(value = "/viewHouseholdMemebers")
    @ResponseBody
    public ResponseEntity<?> doViewHouseholdMemebers(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException{

        String logStep = "[사용자(세대원) 정보 조회]";

        if(Validator.isNullOrEmpty(params.getUserId())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }

        return userService.doViewHouseholdMemebers(params);
    }

    /** 사용자 추가 - 초대 */
    @PostMapping(value = "/addUser")
    @ResponseBody
    public ResponseEntity<?> doAddUser(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException{

        String logStep = "[사용자 추가 - 초대]";

        if(Validator.isNullOrEmpty(params.getRequestUserId()) ||
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
    public ResponseEntity<?> doInviteStatus(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException{

        String logStep = "[사용자 초대 - 수락여부]";

        if(Validator.isNullOrEmpty(params.getRequestUserId()) ||
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
    public ResponseEntity<?> doInviteListView(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException{

        String logStep = "[회원 별칭(이름) 및 전화번호 변경]";

        if(Validator.isNullOrEmpty(params.getUserId())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }

        return userService.doInviteListView(params);
    }

    /** 사용자(세대원) - 강제탈퇴 */
    @PostMapping(value = "/delHouseholdMembers")
    @ResponseBody
    public ResponseEntity<?> doDelHouseholdMembers(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException{

        String logStep = "[사용자(세대원) - 강제탈퇴]";

        if(Validator.isNullOrEmpty(params.getHp()) ||
                Validator.isNullOrEmpty(params.getUserNickname()) ||
                Validator.isNullOrEmpty(params.getUserId())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }
        return userService.doDelHouseholdMembers(params);
    }

    /** 홈 IoT 컨트롤러 알림 설정 */
    @PostMapping(value = "/pushSet")
    @ResponseBody
    public ResponseEntity<?> doPushSet(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException{

        String logStep = "[홈 IoT 컨트롤러 알림 설정]";

        if(Validator.isNullOrEmpty(params.getUserNickname()) ||
                Validator.isNullOrEmpty(params.getHp()) ||
                Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getDeviceType()) ||
                Validator.isNullOrEmpty(params.getModelCode())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }
        HashMap<String, String> memberMap = new HashMap<String, String>();

        for(int i = 0; i < params.getPushCd().size(); i++){
            memberMap.put(params.getPushCd().get(i), params.getPushYn().get(i));
        }
        return userService.doPushSet(params, memberMap);
    }

    /**
     * 홈 IoT 컨트롤러 알림 정보 조회
     */
    @PostMapping(value = "/searchPushSet")
    @ResponseBody
    public HashMap<String, Object> doSearchPushSet(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException{

        String logStep = "[홈 IoT 컨트롤러 알림 정보 조회]";

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getHp()) ||
                Validator.isNullOrEmpty(params.getDeviceType()) ||
                Validator.isNullOrEmpty(params.getModelCode())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }
        return userService.doSearchPushSet(params);
    }

    /**
     * 사용자(세대주) 탈퇴
     */
    @PostMapping(value = "/delHouseholder")
    @ResponseBody
    public ResponseEntity<?> doDelHouseholder(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException {

        String logStep = "[사용자(세대주) 탈퇴]";

        if(Validator.isNullOrEmpty(params.getUserId())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }
        return userService.doDelHouseholder(params);
    }

    /**
     * 홈IoT 서비스 회원 탈퇴
     */
    @PostMapping(value = "/wirhdrawal")
    @ResponseBody
    public ResponseEntity<?> doWirhdrawal(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException {

        String logStep = "[홈IoT 서비스 회원 탈퇴]";

        if(Validator.isNullOrEmpty(params.getUserId()) || Validator.isNullOrEmpty(params.getUserPassword())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }
        return userService.doWirhdrawal(params);
    }

    /**
     * 홈 IoT 컨트롤러 인증
     */
    @PostMapping(value = "/deviceAuthCheck")
    @ResponseBody
    public ResponseEntity<?> doDeviceAuthCheck(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException {

        String logStep = "[홈 IoT 컨트롤러 인증]";

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceIdList()) ||
                Validator.isNullOrEmpty(params.getControlAuthKeyList()) ||
                Validator.isNullOrEmpty(params.getDeviceTypeList()) ||
                Validator.isNullOrEmpty(params.getModelCodeList())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }

        return userService.doDeviceAuthCheck(params);
    }

    /**
     * API인증키 갱신
     */
    @PostMapping(value = "/accessTokenRenewal")
    @ResponseBody
    public ResponseEntity<?> doAccessTokenRenewal(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException {

        String logStep = "[API인증키 갱신]";

        if(Validator.isNullOrEmpty(params.getUserId()) || Validator.isNullOrEmpty(params.getUserPassword())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }
        return userService.doAccessTokenRenewal(params);
    }

    /**
     * 홈 IoT 최초 등록 인증
     */
    @PostMapping(value = "/firstDeviceAuthCheck")
    @ResponseBody
    public ResponseEntity<?> doFirstDeviceAuthCheck(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException {

        String logStep = "[홈 IoT 최초 등록 인증]";

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getSerialNumber()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getTmpRegistKey()) ||
                Validator.isNullOrEmpty(params.getModelCode()) ||
                Validator.isNullOrEmpty(params.getDeviceId())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }
        return userService.doFirstDeviceAuthCheck(params);
    }

    /**
     * 홈 IoT 컨트롤러 삭제(회원 매핑 삭제)
     */
    @PostMapping(value = "/userDeviceDelete")
    @ResponseBody
    public ResponseEntity<?> doUserDeviceDelete(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException {

        String logStep = "[홈 IoT 컨트롤러 삭제(회원 매핑 삭제)]";

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getDeviceType()) ||
                Validator.isNullOrEmpty(params.getModelCode())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }
        return userService.doUserDeviceDelete(params);
    }

    /**
     * 스마트알림 - PUSH 이력 조회
     * */
    @PostMapping(value = "/viewPushHistory")
    @ResponseBody
    public ResponseEntity<?> doViewPushHistory(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException {

        String logStep = "[스마트알림 - PUSH 이력 조회]";

        if(Validator.isNullOrEmpty(params.getUserId()) || Validator.isNullOrEmpty(params.getDeviceType())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }
        return userService.doViewPushHistory(params);

    }

    /**
     * 기기 별칭 수정
     * */
    @PostMapping(value = "/deviceNicknameChange")
    @ResponseBody
    public ResponseEntity<?> doDeviceNicknameChange(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException {

        String logStep = "[기기 별칭 수정]";

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getDeviceType()) ||
                Validator.isNullOrEmpty(params.getModelCode()) ||
                Validator.isNullOrEmpty(params.getNewDeviceNickname())){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }
        return userService.doDeviceNicknameChange(params);
    }

    /**
     * 기기 밝기 조절
     * */
    @PostMapping(value = "/brightnessControl")
    @ResponseBody
    public ResponseEntity<?> doBrightnessControl(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException {

        String logStep = "[기기 밝기 조절]";

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getDeviceType()) ||
                Validator.isNullOrEmpty(params.getModelCode()) ||
                Validator.isNullOrEmpty(params.getBrightnessLevel())
                ){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }
        return userService.doBrightnessControl(params);
    }

    /**
     * 공지사항 조회
     * */
    @PostMapping(value = "/notice")
    @ResponseBody
    public ResponseEntity<?> doNotice(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException {

        String logStep = "[공지사항 조회]";

        if(Validator.isNullOrEmpty(params.getUserId()) || Validator.isNullOrEmpty(params.getDeviceType())
        ){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }
        return userService.doNotice(params);
    }

    /**
     * 기기 설치 위치 별칭 수정
     * */
    @PostMapping(value = "/updateDeviceLocationNickname")
    @ResponseBody
    public ResponseEntity<?> doUpdateDeviceLocationNickname(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException {

        String logStep = "[기기 설치 위치 별칭 수정]";

        if(Validator.isNullOrEmpty(params.getUserId()) || Validator.isNullOrEmpty(params.getDeviceType())
        ){
            throw new CustomException(logStep + ": NULL OR EMPTY ERROR");
        }
        return userService.doNotice(params);
    }
}
