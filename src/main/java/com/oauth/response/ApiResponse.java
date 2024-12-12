package com.oauth.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class ApiResponse {
    /**
     * API 요청 결과에 대한 코드와 메세지 정의
     */
    @Getter
    public enum ResponseType {
        HTTP_200("200", "성공"),
        HTTP_400("400", "Bad Request - field validation 실패"),
        HTTP_401("401", "Unauthorized - API 인증 실패"),
        HTTP_404("404", "MOBIUS_SERVER_ERROR"),
        HTTP_500("500", "Internal Server Error"),
        CUSTOM_1001("1001", "사용자ID 중복"),
        CUSTOM_1002("1002", "R/C제어 인증 오류"),
        CUSTOM_1003("1003", "비밀번호 오류"),
        CUSTOM_1004("1004", "사용자ID 미존재"),
        CUSTOM_1005("1005", "사용자 전화번호 중복"),
        CUSTOM_1006("1006", "사용자 전화번호 미존재"),
        CUSTOM_1007("1007", "전화번호 중복"),
        CUSTOM_1008("1008", "디바이스 제어 요청 실패"),
        CUSTOM_1009("1009", "디바이스 미존재"),
        CUSTOM_1010("1010", "사용자 별칭 미존재"),
        CUSTOM_1011("1011", "비밀번호 질문/답변 미일치"),
        CUSTOM_1012("1012", "비밀번호 질문코드 미존재"),
        CUSTOM_1013("1013", "PUSH 코드 미존재"),
        CUSTOM_1014("1014", "실내온도조절기 접속 불가 상태(R/C 네트워크 접속 오류)"),
        CUSTOM_1015("1015", "디바이스 응답시간 초과"),
        CUSTOM_1016("1016", "쿼리 결과 없음"),
        CUSTOM_1017("1017", "API 입력항목 검증 실패"),
        CUSTOM_1018("1018", "API 요청 실패 (DB 쿼리 오류)"),
        CUSTOM_1019("1019", "제어 요청 Key 미정의(서버오류)"),
        CUSTOM_1020("1020", "제어 요청 Value 미정의(서버오류)"),
        CUSTOM_1021("1021", "제어 요청 Key-Value 항목 불일치(서버오류)"),
        CUSTOM_1022("1022", "하위 디바이스 정보 미존재"),
        CUSTOM_2001("2001", "사용자 초대 횟수 초과"),
        CUSTOM_2002("2002", "중복 사용자 초대"),
        CUSTOM_2003("2003", "API 인증키 만료"),
        CUSTOM_2004("2003", "FCM 갱신 오류"),
        CUSTOM_9999("2003", "정의되지 않은 오류");


        private String code;
        private String msg;

        ResponseType(String code, String msg) {
            this.code = code;
            this.msg = msg;
        }
    }

    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Data {
        private String resultCode;
        private String resultMsg;
        private String accessToken;
        private String tokenVerify;
        private String userId;
        private String userNickname;
        private String hp;
        private Object device;
        private Object user;
        private Object invitation;
        private List<String> userIdList;
        private Object pushCodes;
        private Object deviceStatusInfo;
        private Object pushInfo;
        private Object noticeInfo;
        private Object groupInfo;
        private Object errorInfo;
        private String mobiusResponseCode;
        private String latitude;
        private String longitude;
        private String tmpRegistKey;
        private String duplicationYn;
        private Object homeViewValue;
        private String householder;
        private String registUserType;
        // GW에서 받은 값을 던지는 시험용 변수
        private Object testVariable;
        private String awakeList;
        private String deviceId;
        private String errorCode;
        private String errorName;
        private String errorMessage;
        private String deviceInfo;

        public void setResult(ResponseType responseType, String msg) {
            String code = responseType.getCode();
            setResultCode(code);
            setResultMsg(msg);
        }

        @Getter
        @Setter
        public static class DeviceInfoSearchList {
            private String modelCode;
            private String deviceNickname;
            private String addrNickname;
            private String zipCode;
            private String oldAddr;
            private String newAddr;
            private String addrDetail;
            private String latitude;
            private String longitude;
            private String regSort;
            private String deviceId;
            private String controlAuthKey;
        }

        @Getter
        @Setter
        public static class Device {
            private String deviceId;
            private String controlAuthKey;
            private String deviceNickname;
            private String regSort;
            private String tmpRegistKey;
            private String latitude;
            private String longitude;
        }

        @Getter
        @Setter
        public static class User {
            private String userNickname;
            private String userId;
            private String householder;
        }

        @Getter
        @Setter
        public static class Invitation {
            private String invitationIdx;
            private String inviteAcceptYn;
            private String requestUserId;
            private String requestUserNick;
            private String responseUserId;
//            private String responseUserNick;
            private String responseHp;
            private String inviteStartDate;
            private String inviteEndDate;
        }

        @Getter
        @Setter
        public static class PushCodes {
            private String pushCd;
            private String pushYn;
        }

        @Getter
        @Setter
        public static class PushInfo {
            private String pushIdx;                      // PUSH IDX
            private String pushTitle;                    // PUSH 제목
            private String pushContent;                  // PUSH 내용
            private String pushType;                     // PUSH 타입
            private String pushDatetime;                 // PUSH 전송 시간
        }

        @Getter
        @Setter
        public static class NoticeInfo {
            private String noticeIdx;
            private String noticeTitle;
            private String noticeContent;
            private String noticeType;
            private String noticeStartDate;
            private String noticeEndDate;
        }

        @Getter
        @Setter
        public static class ErrorInfo {
            private String errorMessage;
            private String errorCode;
            private String errorDateTime;
            private String serialNumber;
        }
        @Override
        public String toString() {
            try {
                ObjectMapper mapper = new ObjectMapper();
                return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(this);
            } catch (Exception e) {
                return "Error converting to JSON: " + e.getMessage();
            }
        }
    }
}
