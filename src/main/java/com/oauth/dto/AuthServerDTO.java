package com.oauth.dto;

import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.json.JSONObject;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.*;

/**
 * 회원 관련 데이터 클래스
 *
 * Spring Boot Security UserDetails 구현 방법
 * https://parandol.tistory.com/6?category=823695
 */
@Data
@Getter
@Setter
public class AuthServerDTO implements Serializable {

    private static final long serialVersionUID = 54436712726576487L;

    private String lastIndex;

    private Integer pageNo = 1;
    private Integer numOfRows = 1;
    private Integer sRow = 0;

    private Integer frontRow = 0;
    private Integer secondRow = 0;

    private String mn;
    private String phoneId;
    private Long idx;
    private String newId;
    private String houseLeaderFlag;
    private String hpCount;
    private String oldId;
    private String nextUserId;
    private String groupIdx;
    private String groupIdxList;
    private String authority;                    // 권한
    private String hp;                           // 사용자 전화번호
    private String userId;                       // 사용자 아이디
    private String userNickname;                 // 사용자 닉네임
    private String userPassword;                 // 사용자 비밀번호
    private String registrationDatetime;         // 사용자 등록일시
    private String lastLoginDatetime;            // 최종 로그인 일시
    private String accessToken;                  // API 인증키
    private String accessTokenCreateTime;        // API 인증키 생성 일시
    private String controlAuthKey;               // 기기 제어 키
    private String deviceId;                     // 기기 아이디
    private String deviceNickname;               // 기기 별칭
    private String newDeviceNickname;            // 신규 기기 별칭
    private String regSort;                      // 기기 등록 순서
    private String deviceType;                   // 기기 타입
    private String modelCode;                    // 기기 모델 코드
    private String oldHp;                        // 이전 전화번호
    private String newHp;                        // 신규 전화번호
    private String oldPassword;                  // 이전 비밀번호
    private String newPassword;                  // 신규 비밀번호
    private String householder;                  // 세대주 여부
    private String inviteStartDate;              // 사용자 초대 시작 시간
    private String inviteEndDate;                // 사용자 초대 만료 시간
    private String inviteAcceptYn;               // 사용자 초대 수락 여부
    private String invitationIdx;                // 초대장 Idx
    private String requestUserId;                // 요청 회원 ID
    private String responseUserId;               // 응답 회원 ID
    private String requestUserNick;              // 요청 회원 별칭
    private String responseUserNick;             // 응답 회원 별칭
    private String responseHp;                   // 응답 회원 전화번호
    private String authenticationDatetime;       // 기기 인증 일시
    private String responseNickname;             // 응답 회원 별칭
    private List<String> pushCd;
    private List<String> pushYn;
    private String fPushYn;                      // 상태변경 알림 수신
    private String sPushYn;                      // 에러 알림 수신
    private String tPushYn;                      // 맞춤 알림 수신
    private String powerStatus;                  // 전원On/Off (on/of)
    private String pushIdx;                      // PUSH IDX
    private String pushTitle;                    // PUSH 제목
    private String pushContent;                  // PUSH 내용
    private String pushType;                     // PUSH 타입
    private String pushDatetime;                 // PUSH 전송 시간
    private String brightnessLevel;              // 밝기 단계 (0~5, 5 제일 높음)
    private String noticeIdx;                    // 공지 번호
    private String noticeTitle;                  // 공지 제목
    private String noticeType;                   // 공지 타입 (00: ALL 01: 보일러 05: 각방 07: 환기)
    private String noticeContent;                // 공지 내용
    private String noticeStartDate;              // 공지 시작일
    private String noticeEndDate;                // 공지 종료일
    private String registYn;                     // Y: 등록 N: 수정
    private String tmpRegistKey;                 // 임시 저장키
    private String serialNumber;                 // 기기 시리얼 번호
    private String zipCode;                      // 우편번호 (신 우편번호 5자리)
    private String oldAddr;                      // 지번 주소
    private String newAddr;                      // 도로명 주소
    private String addrDetail;                   // 상세 주소
    private String addrNickname;                 // 설치 장소명
    private String latitude;                     // 위도
    private String longitude;                    // 경도
    private String deviceStatusJson;             // RC주기보고 JSON 전문
    /**
     * 01: 실내난방
     * 02: 온돌난방
     * 03: 외출
     * 04: 자동
     * 05: 절약난방
     * 06: 취침
     * 07: 온수전용
     * 08: 온수-빠른온수
     * 09: 귀가
     * 10: 예약난방 - 24시간
     * 11: 예약난방 - 반복(12시간)
     * 12: 예약난방 - 주간
     * */
    private String modeCode;
    private String sleepCode;                    // 01: Comfort 02: Normal 03: Warm
    private String temperture;                   // 온도 [10.0 ~ 40.0 (°C)], 소수점 1자리
    private String temperature;                   // 온도 [10.0 ~ 40.0 (°C)], 소수점 1자리
    private String lockSet;                      // 잠금 모드: on/of
    private String hours;                        // 예약 시간 리스트 (00시~23시, 총 24건)
    private String type24h;                      // 01: 표준형 02: 맞벌이형 03: 절약형 04: 맞춤형1 05: 맞춤형2
    private String onOffFlag;                    // on: 단말 제어 of: 시간정보만 서버저장
    private String workPeriod;                   // 가동주기(시), "00"~"12"
    private String workTime;                     // 가동시간(분), "01"~"60"
    private String msDt;
    private String awakeList;                    // 기상모드 설정 리스트 아이템
    private String[][] ws;
    private String[] hr;
    // private String[] mn;
    private String weekList;                     // 요일: 0,1,2,3,4,5,6 (일,월,화,수,목,금,토)
    private String[] dayWeek;                    // 주간예약 요일별 가동시간
    private String pushToken;                    // 푸시 토큰
    private String startDatetime;                // 통계 시작일(YYYYMMDD)
    private String startDate;                    // 통계 시작일(YYYYMMDD)
    private String endDatetime;                  // 통계 종료일(YYYYMMDD)
    private String endDate;                      // 통계 종료일(YYYYMMDD)
    private String insertDatetime;               // 등록일시
    private String workDate;
    private String functionId;                   // Command ID
    private String newDeviceLocNickname;

    private String searchFlag;                   // 00:단건 01:전체
    private String responseDeviceCount;          // 00: 0개(등록한 기기), 01: 1개(등록한 기기) 이상
    private String registUserType;

    private String safeAlarmTime;                // 안전안심 시간 (예: 0723 = 7일 23시간)
    private String safeAlarmStatus;              // 안전안심 알람 사용 여부 (Y/N)

    private String groupId;
    private String groupName;
    private String targetId;
    private String delUserId;

    private String errorCode;
    private String errorMessage;
    private String errorVersion;
    private String errorDateTime;

    private String codeType;                     // 코드 구분 (0 = 모드코드, 1 = 변경상태정보코드)
    private String commandId;                    // 명령어 (예: modeChange)
    private String controlCode;                  // 제어코드 (예: 01)
    private String controlCodeName;              // 제어코드명 (예: 실내난방)
    private String commandFlow;                  // 명령방향: 0 = APP -> 제어기기, 1 = 제어기기 -> APP

    private String fanSpeed;                     // 단수 [1~3], 0단은 자동 모드 변경 시 설정됨
    private String onHour;                       // 켜짐 시간
    private String onMinute;                     // 켜짐 분
    private String offHour;                      // 꺼짐 시간
    private String offMinute;                    // 꺼짐 분

    private String waitHour;                     // 대기 시간
    private String waitMinute;                   // 대기 분
    private String newControlAuthKey;            // 신규 RKey
    private String deviceCount;
    private String userCount;
    private String inviteCount;
    private String safeAlarmCount;
    private String timeCheckCount;
    private String valveCount;

    private String deviceInfo;                   // Y/N 기기 존재 여부

    private String fastHotWater;                 // 빠른온수 예약 정보 조회용 변수
    private String ventFanLifeStatus;

    private List<String> deviceIdList;

    private String groupNameCount;
    private String loginoutStatus;

    private String pm25;
    private String co2;
    private String pm10;
    private String indoorTemp;
    private String indoorHumi;
    private String timeInfo;

    private String prId;
    private String psYn;
    private String dvNm;
    private String parentDevice;
    private String valveStatus;

    private String forcedDefrost;

    private String locationRadius;

    private String awayStatus;
    private String awayMode;
    private String awayValue;
    private String homeStatus;
    private String homeMode;
    private String homeValue;

    private String ipAddress;
    private String wifiSsid;
    private String wifiBssid;
    private String wifiSecurity;

    private String clickUrl; // 외부 접속 URL
    private String imageUrl; // 이미지 경로 URL

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthServerDTO that = (AuthServerDTO) o;
        return Objects.equals(userId, that.userId) &&
                Objects.equals(groupIdx, that.groupIdx);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, groupIdx);
    }
}