package com.oauth.service.mapper;

import com.oauth.dto.AuthServerDTO;
import com.oauth.utils.CustomException;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;

public interface UserService {

    /** 회원 로그인 */
    ResponseEntity<?> doLogin(String userId, String userPassword) throws CustomException;

    /** 회원가입 */
    ResponseEntity<?> doRegist(AuthServerDTO params) throws CustomException;

    /** 회원중복 체크 */
    ResponseEntity<?> doDuplicationCheck(AuthServerDTO params) throws CustomException;

    /** ID 찾기 */
    ResponseEntity<?> doIdFind(AuthServerDTO params) throws CustomException;

    /** 비밀번호 찾기 - 초기화 */
    ResponseEntity<?> doResetPassword(AuthServerDTO params) throws CustomException;

    /** 비밀번호 변경 - 생성 */
    ResponseEntity<?> doChangePassword(AuthServerDTO params) throws CustomException;

    /** 사용자정보 조회 */
    ResponseEntity<?> doSearch(AuthServerDTO params) throws CustomException;

    /** 회원 별칭(이름) 및 전화번호 변경 */
    ResponseEntity<?> doUpdateUserNicknameHp(AuthServerDTO params) throws CustomException;

    /** 비밀번호 변경 - 로그인시 */
    ResponseEntity<?> doUpdatePassword(AuthServerDTO params) throws CustomException;

    /** 사용자(세대원) 정보 조회 */
    ResponseEntity<?> doViewHouseholdMemebers(AuthServerDTO params) throws CustomException;

    /** 사용자 추가 - 초대 */
    ResponseEntity<?> doAddUser(AuthServerDTO params) throws CustomException;

    /** 사용자 초대 - 수락여부 */
    ResponseEntity<?> doInviteStatus(AuthServerDTO params) throws CustomException;

    /** 사용자 초대 - 목록 조회 */
    ResponseEntity<?> doInviteListView(AuthServerDTO params) throws CustomException;

    /** 사용자(세대원) - 강제탈퇴 */
    ResponseEntity<?> doDelHouseholdMembers(AuthServerDTO params) throws CustomException;

    /** 홈 IoT 컨트롤러 알림 설정 */
    ResponseEntity<?> doPushSet(AuthServerDTO params, HashMap<String, String> controlMap) throws CustomException;

    /** 홈 IoT 컨트롤러 알림 정보 조회 */
    HashMap<String, Object> doSearchPushSet(AuthServerDTO params) throws CustomException;

    /** 사용자(세대주) 탈퇴 */
    ResponseEntity<?> doDelHouseholder(AuthServerDTO params) throws CustomException;

    /** 홈IoT 서비스 회원 탈퇴 */
    ResponseEntity<?> doWirhdrawal(AuthServerDTO params) throws CustomException;

    /** 홈 IoT 컨트롤러 인증 */
    ResponseEntity<?> doDeviceAuthCheck(AuthServerDTO params) throws CustomException;

    /** 홈 IoT 최초 등록 인증 */
    ResponseEntity<?> doFirstDeviceAuthCheck(AuthServerDTO params) throws CustomException;

    /** API인증키 갱신 */
    ResponseEntity<?> doAccessTokenRenewal(AuthServerDTO params) throws CustomException;

    /** 홈 IoT 컨트롤러 삭제(회원 매핑 삭제) */
    ResponseEntity<?> doUserDeviceDelete(AuthServerDTO params) throws CustomException;

    /** 스마트알림 - PUSH 이력 조회 */
    ResponseEntity<?> doViewPushHistory(AuthServerDTO params) throws CustomException;

    /** 기기 별칭 수정 */
    ResponseEntity<?> doDeviceNicknameChange(AuthServerDTO params) throws CustomException;

    /** 기기 밝기 조절 */
    ResponseEntity<?> doBrightnessControl(AuthServerDTO params) throws CustomException;

    /** 공지사항 조회 */
    ResponseEntity<?> doNotice(AuthServerDTO params) throws CustomException;
}
