package com.oauth.controller;

import com.oauth.dto.AuthServerDTO;
import com.oauth.dto.gw.DeviceStatusInfo;
import com.oauth.mapper.DeviceMapper;
import com.oauth.mapper.MemberMapper;
import com.oauth.service.impl.MobiusService;
import com.oauth.service.impl.UserServiceImpl;
import com.oauth.utils.Common;
import com.oauth.utils.CustomException;
import com.oauth.utils.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@RequestMapping("/users/v1")
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class UserController {

    @Autowired
    private MemberMapper memberMapper;
    @Autowired
    private DeviceMapper deviceMapper;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private Common common;
    @Autowired
    private UserServiceImpl userService;
    @Autowired
    private MobiusService mobiusService;

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
            throws Exception {

        log.info("[회원 로그인]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId()) || Validator.isNullOrEmpty(params.getUserPassword())) {
            throw new CustomException("404", "회원 로그인 입력 값 오류");
        }

        return userService.doLogin(params.getUserId(), params.getUserPassword(), params.getPushToken());
    }

    /** 회원 로그아웃 */
    @PostMapping(value = "/logout")
    @ResponseBody
    public ResponseEntity<?> doLogout(HttpSession session, HttpServletRequest request, @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws Exception {

        log.info("[회원 로그아웃]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId())) {
            throw new CustomException("404", "회원 로그아웃 입력 값 오류");
        }

        return userService.doLogout(params.getUserId(), params.getPushToken());
    }

    /** 회원가입 */
    @PostMapping(value = "/regist")
    @ResponseBody
    public ResponseEntity<?> doRegist(HttpSession session, HttpServletRequest request, @ModelAttribute AuthServerDTO params, HttpServletResponse response)
            throws CustomException {

        log.info("[회원 가입]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getHp()) ||
                Validator.isNullOrEmpty(params.getUserNickname()) ||
                Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getUserPassword()) ||
                Validator.isNullOrEmpty(params.getRegistUserType())){
            throw new CustomException("404", "회원 가입 입력 값 오류");
        }
        return userService.doRegist(params);
    }

    /** 회원중복 체크 */
    @PostMapping(value = "/duplicationCheck")
    @ResponseBody
    public ResponseEntity<?> doDuplicationCheck(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException{

        log.info("[ID 중복 확인]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId())){
            throw new CustomException("404", "ID 중복 확인 입력 값 오류");
        }

        return userService.doDuplicationCheck(params);
    }

    /** ID 찾기 */
    @PostMapping(value = "/idFind")
    @ResponseBody
    public ResponseEntity<?> doIdFind(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException{

        log.info("[ID 찾기]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getHp()) ||
           Validator.isNullOrEmpty(params.getDeviceType()) ||
           Validator.isNullOrEmpty(params.getModelCode())){
            throw new CustomException("404", "ID 찾기 확인 입력 값 오류");
        }

        return userService.doIdFind(params);

    }

    /** 비밀번호 찾기 - 초기화 */
    @PostMapping(value = "/resetPassword")
    @ResponseBody
    public ResponseEntity<?> doResetPassword(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException{

        log.info("[비밀번호 찾기 - 초기화]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getHp()) ||
                Validator.isNullOrEmpty(params.getDeviceType()) ||
                Validator.isNullOrEmpty(params.getModelCode())){
            throw new CustomException("404", "비밀번호 찾기 - 초기화 입력 값 오류");
        }

        return userService.doResetPassword(params);
    }

    /** 비밀번호 변경 - 생성 */
    @PostMapping(value = "/changePassword")
    @ResponseBody
    public ResponseEntity<?> doChangePassword(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException{

        log.info("[비밀번호 변경 - 생성]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId()) || Validator.isNullOrEmpty(params.getUserPassword())){
            throw new CustomException("404", "비밀번호 변경 - 생성 입력 값 오류");
        }
        return userService.doChangePassword(params);
    }

    /** 사용자정보 조회 */
    @PostMapping(value = "/search")
    @ResponseBody
    public ResponseEntity<?> doSearch(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException{

        log.info("[사용자정보 조회]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId())){
            throw new CustomException("404", "사용자정보 조회 입력 값 오류");
        }

        return userService.doSearch(params);
    }

    /** 사용자 그룹 정보 조회 */
    @PostMapping(value = "/searchGroupInfo")
    @ResponseBody
    public ResponseEntity<?> doSearchGroupInfo(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException{

        log.info("[사용자정보 조회]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId()) || Validator.isNullOrEmpty(params.getHouseLeaderFlag())){
            throw new CustomException("404", "사용자정보 조회 입력 값 오류");
        }

        return userService.doSearchGroupInfo(params);
    }

    /** 사용자 그룹 삭제 */
    @PostMapping(value = "/deleteGroup")
    @ResponseBody
    public ResponseEntity<?> doDeleteGroup(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException{

        log.info("[사용자정보 조회]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getGroupIdx())){
            throw new CustomException("404", "사용자정보 조회 입력 값 오류");
        }

        return userService.doDeleteGroup(params);
    }

    /** 사용자 그룹 명칭 변경 */
    @PostMapping(value = "/changeGroupName")
    @ResponseBody
    public ResponseEntity<?> doChangeGroupName(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException{

        log.info("[사용자정보 조회]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getGroupIdx()) ||
                Validator.isNullOrEmpty(params.getGroupName()) ||
                Validator.isNullOrEmpty(params.getUserId())){
            throw new CustomException("404", "사용자정보 조회 입력 값 오류");
        }

        return userService.doChangeGroupName(params);
    }

    /** 사용자 그룹 생성 */
    @PostMapping(value = "/createNewGroup")
    @ResponseBody
    public ResponseEntity<?> doCreateNewGroup(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException{

        log.info("[사용자 그룹 생성]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId()) || Validator.isNullOrEmpty(params.getGroupName())){
            throw new CustomException("404", "사용자 그룹 생성 입력 값 오류");
        }

        return userService.doCreateNewGroup(params);
    }

    /** 회원 별칭(이름) 및 전화번호 변경 */
    @PostMapping(value = "/updateUserNicknameHp")
    @ResponseBody
    public ResponseEntity<?> doUpdateUserNicknameHp(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException{

        log.info("[회원 별칭(이름) 및 전화번호 변경]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getUserPassword()) ||
                Validator.isNullOrEmpty(params.getUserNickname())){
            throw new CustomException("404", "회원 별칭(이름) 및 전화번호 변경 값 오류");
        }

        return userService.doUpdateUserNicknameHp(params);
    }

    /** 비밀번호 변경 - 로그인시 */
    @PostMapping(value = "/updatePassword")
    @ResponseBody
    public ResponseEntity<?> doUpdatePassword(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException{

        log.info("[회원 별칭(이름) 및 전화번호 변경]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getOldPassword()) ||
                Validator.isNullOrEmpty(params.getNewPassword())){
            throw new CustomException("404", "회원 별칭(이름) 및 전화번호 변경 값 오류");
        }

        return userService.doUpdatePassword(params);

    }

    /** 사용자(세대원) 정보 조회 */
    @PostMapping(value = "/viewHouseholdMembers")
    @ResponseBody
    public ResponseEntity<?> doViewHouseholdMemebers(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException{

        log.info("[사용자(세대원) 정보 조회]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId()) || Validator.isNullOrEmpty(params.getGroupIdxList())){
            throw new CustomException("404", "사용자(세대원) 정보 조회 값 오류");
        }
        return userService.doViewHouseholdMemebers(params);
    }

    /** 사용자 추가 - 초대 */
    @PostMapping(value = "/addUser")
    @ResponseBody
    public ResponseEntity<?> doAddUser(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException{

        log.info("[사용자 추가 - 초대]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getRequestUserId()) ||
                Validator.isNullOrEmpty(params.getRequestUserNick()) ||
                Validator.isNullOrEmpty(params.getResponseHp()) ||
                Validator.isNullOrEmpty(params.getResponseUserId()) ||
                Validator.isNullOrEmpty(params.getInviteStartDate()) ||
                Validator.isNullOrEmpty(params.getGroupIdx()) ||
                Validator.isNullOrEmpty(params.getGroupName())){
            throw new CustomException("404", "사용자 추가 - 초대 값 오류");
        }

        return userService.doAddUser(params);
    }

    /** 사용자 초대 - 수락여부 */
    @PostMapping(value = "/inviteStatus")
    @ResponseBody
    public ResponseEntity<?> doInviteStatus(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException{

        log.info("[사용자 초대 - 수락여부]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getRequestUserId()) ||
                Validator.isNullOrEmpty(params.getResponseHp()) ||
                Validator.isNullOrEmpty(params.getResponseUserId()) ||
                Validator.isNullOrEmpty(params.getResponseNickname()) ||
                Validator.isNullOrEmpty(params.getInviteAcceptYn()) ||
                Validator.isNullOrEmpty(params.getGroupIdx()) ||
                Validator.isNullOrEmpty(params.getInvitationIdx()) ||
                Validator.isNullOrEmpty(params.getGroupName())){
            throw new CustomException("404", "사용자 초대 - 수락여부 값 오류");
        }

        return userService.doInviteStatus(params);
    }

    /** 사용자 초대 - 목록 조회 */
    @PostMapping(value = "/inviteListView")
    @ResponseBody
    public ResponseEntity<?> doInviteListView(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException{

        log.info("[사용자 초대 - 목록 조회]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId())){
            throw new CustomException("404", "회원 별칭(이름) 및 전화번호 변경 값 오류");
        }

        return userService.doInviteListView(params);
    }

    /** 사용자(세대원) - 강제탈퇴 */
    @PostMapping(value = "/delHouseholdMembers")
    @ResponseBody
    public ResponseEntity<?> doDelHouseholdMembers(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException{

        log.info("[사용자(세대원) - 강제탈퇴경]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getHp()) ||
                Validator.isNullOrEmpty(params.getDelUserId()) ||
                Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getGroupIdx())){
            throw new CustomException("404", "사용자(세대원) - 강제탈퇴 값 오류");
        }
        return userService.doDelHouseholdMembers(params);
    }

    /** 홈 IoT 컨트롤러 알림 설정 */
    @PostMapping(value = "/pushSet")
    @ResponseBody
    public ResponseEntity<?> doPushSet(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException{

        log.info("[홈 IoT 컨트롤러 알림 설정]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserNickname()) ||
                Validator.isNullOrEmpty(params.getHp()) ||
                Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceIdList()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getDeviceType()) ||
                Validator.isNullOrEmpty(params.getModelCode()) ||
                Validator.isNullOrEmpty(params.getPushCd()) ||
                Validator.isNullOrEmpty(params.getPushYn())){
            throw new CustomException("404", "홈 IoT 컨트롤러 알림 설정 값 오류");
        }

        return userService.doPushSet(params);
    }

    /**
     * 홈 IoT 컨트롤러 알림 정보 조회
     */
    @PostMapping(value = "/searchPushSet")
    @ResponseBody
    public HashMap<String, Object> doSearchPushSet(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException{

        log.info("[홈 IoT 컨트롤러 알림 정보 조회]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getHp()) ||
                Validator.isNullOrEmpty(params.getSearchFlag())){
            throw new CustomException("404", "홈 IoT 컨트롤러 알림 정보 조회 값 오류");
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

        log.info("[사용자(세대주) 탈퇴]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId())|| Validator.isNullOrEmpty(params.getGroupIdx())){
            throw new CustomException("404", "사용자(세대주) 탈퇴 값 오류");
        }
        return userService.doDelHouseholder(params);
    }

    /**
     * 홈IoT 서비스 회원 탈퇴
     */
    @PostMapping(value = "/withdrawal")
    @ResponseBody
    public ResponseEntity<?> doWithdrawal(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException {

        log.info("[홈IoT 서비스 회원 탈퇴]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId()) || Validator.isNullOrEmpty(params.getUserPassword())){
            throw new CustomException("404", "홈IoT 서비스 회원 탈퇴 값 오류");
        }
        return userService.doWithdrawal(params);
    }

    /**
     * 홈 IoT 컨트롤러 인증
     */
    @PostMapping(value = "/deviceAuthCheck")
    @ResponseBody
    public ResponseEntity<?> doDeviceAuthCheck(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException {

        log.info("[홈 IoT 컨트롤러 인증]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey())){
            throw new CustomException("404", "홈 IoT 컨트롤러 인증 값 오류");
        }

        return userService.doDeviceAuthCheck(params);
    }

    /**
     * API인증키 검증
     */
    @PostMapping(value = "/accessTokenVerification")
    @ResponseBody
    public ResponseEntity<?> doAccessTokenVerification(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException {

        log.info("[API인증키 갱신]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId()) || Validator.isNullOrEmpty(params.getAccessToken())){
            throw new CustomException("404", "API인증키 갱신 값 오류");
        }
        return userService.doAccessTokenVerification(params);
    }

    /**
     * 홈 IoT 최초 등록 인증
     */
    @PostMapping(value = "/firstDeviceAuthCheck")
    @ResponseBody
    public ResponseEntity<?> doFirstDeviceAuthCheck(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws CustomException {

        log.info("[홈 IoT 최초 등록 인증]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getSerialNumber()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getTmpRegistKey()) ||
                Validator.isNullOrEmpty(params.getModelCode())){
            throw new CustomException("404", "홈 IoT 최초 등록 인증 값 오류");
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

        log.info("[홈 IoT 컨트롤러 삭제(회원 매핑 삭제)]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceIdList()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getDeviceType()) ||
                Validator.isNullOrEmpty(params.getModelCode())){
            throw new CustomException("404", "홈 IoT 컨트롤러 삭제(회원 매핑 삭제) 값 오류");
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

        log.info("[스마트알림 - PUSH 이력 조회]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceType()) ||
                Validator.isNullOrEmpty(params.getPageNo()) ||
                Validator.isNullOrEmpty(params.getNumOfRows())){
            throw new CustomException("404", "스마트알림 - PUSH 이력 조회 값 오류");
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

        log.info("[기기 별칭 수정]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getDeviceType()) ||
                Validator.isNullOrEmpty(params.getModelCode()) ||
                Validator.isNullOrEmpty(params.getNewDeviceNickname())){
            throw new CustomException("404", "기기 별칭 수정 값 오류");
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

        log.info("[기기 밝기 조절]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getControlAuthKey()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getDeviceType()) ||
                Validator.isNullOrEmpty(params.getModelCode()) ||
                Validator.isNullOrEmpty(params.getBrightnessLevel())
                ){
            throw new CustomException("404", "기기 밝기 조절 값 오류");
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

        log.info("[공지사항 조회]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId())){
            throw new CustomException("404", "공지사항 조회 값 오류");
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

        log.info("[기기 설치 위치 별칭 수정]");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceType()) ||
                Validator.isNullOrEmpty(params.getDeviceId()) ||
                Validator.isNullOrEmpty(params.getNewDeviceLocNickname()) ||
                Validator.isNullOrEmpty(params.getPushToken())){
            throw new CustomException("404", "기기 설치 위치 별칭 수정 값 오류");
        }
        return userService.doUpdateDeviceLocationNickname(params);
    }

    /**
     * 임시 저장키 생성
     * */
    @PostMapping(value = "/generateTempKey")
    @ResponseBody
    public ResponseEntity<?> dogenerateTempKey(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws Exception {

        log.info("임시 저장키 생성");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId())){
            throw new CustomException("404", "임시 저장키 생성 오류");
        }
        return userService.dogenerateTempKey(params.getUserId());
    }

    /**
     * 안전안심 알람 설정
     * */
    @PostMapping(value = "/safeAlarmSet")
    @ResponseBody
    public ResponseEntity<?> doSafeAlarmSet(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws Exception {

        log.info("안전안심 알람 설정");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId()) ||
            Validator.isNullOrEmpty(params.getSafeAlarmTime()) ||
            Validator.isNullOrEmpty(params.getSafeAlarmStatus()) ||
            Validator.isNullOrEmpty(params.getDeviceId())){
            throw new CustomException("404", "안전안심 알람 설정 값 오류");
        }
        return userService.doSafeAlarmSet(params);
    }

    /**
     * 빠른온수 예약 정보 조회
     * */
    @PostMapping(value = "/getFastHotWaterInfo")
    @ResponseBody
    public ResponseEntity<?> doGetFastHotWaterInfo(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws Exception {

        log.info("빠른온수 예약 정보 조회");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId())){
            throw new CustomException("404", "빠른온수 예약 정보 조회 오류");
        }
        return userService.doGetFastHotWaterInfo(params);
    }

    /**
     * 환기 필터 잔여 수명 정보 조회
     * */
    @PostMapping(value = "/getFanLifeStatus")
    @ResponseBody
    public ResponseEntity<?> doGetFanLifeStatus(HttpServletRequest request, @ModelAttribute AuthServerDTO params)
            throws Exception {

        log.info("환기 필터 잔여 수명 정보 조회");
        common.logParams(params);

        if(Validator.isNullOrEmpty(params.getUserId()) ||
                Validator.isNullOrEmpty(params.getDeviceId())){
            throw new CustomException("404", "환기 필터 잔여 수명 정보 조회 오류");
        }
        return userService.doGetFanLifeStatus(params);
    }

    @PostMapping(value = "/test")
    public String test(String on) throws Exception {

        AuthServerDTO params = new AuthServerDTO();
        params.setRequestUserId("yohan1202");
        params.setResponseUserId("yohan971202");

//        memberMapper.getInviteCount(params).getInviteCount();

        System.out.println(Integer.parseInt(memberMapper.getInviteCount(params).getInviteCount()) <= 5);

        return null;
    }

}
