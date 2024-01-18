package com.oauth.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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
        HTTP_404("404", "Not found"),
        HTTP_500("500", "Internal Server Error"),
        CUSTOM_1001("1001", "사용자ID 중복"),
        CUSTOM_1002("1002", "R/C제어 인증 오류"),
        CUSTOM_1003("1003", "비밀번호 오류"),
        CUSTOM_1004("1004", "사용자ID 미존재"),
        CUSTOM_1005("1005", "사용자 전화번호 중복"),
        CUSTOM_1006("1006", "사용자 전화번호 미존재"),
        CUSTOM_1007("1007", "디바이스ID 중복"),
        CUSTOM_1008("1008", "디바이스 제어 요청 실패"),
        CUSTOM_1009("1009", "디바이스 통계 미존재"),
        CUSTOM_1010("1010", "사용자 별칭 미존재"),
        CUSTOM_1011("1011", "비밀번호 질문/답변 미일치"),
        CUSTOM_1012("1012", "비밀번호 질문코드 미존재"),
        CUSTOM_1013("1013", "PUSH 코드 미존재"),
        CUSTOM_1014("1014", "실내온도조절기 접속 불가 상태(R/C 네트워크 접속 오류)"),
        CUSTOM_1015("1015", "디바이스 응답시간 초과"),
        CUSTOM_1016("1016", "데이터 미존재"),
        CUSTOM_1017("1017", "API 입력항목 검증 실패"),
        CUSTOM_1018("1018", "존재하지 않는 제어 요청"),
        CUSTOM_1019("1019", "제어 요청 Key 미정의(서버오류)"),
        CUSTOM_1020("1020", "제어 요청 Value 미정의(서버오류)"),
        CUSTOM_1021("1021", "제어 요청 Key-Value 항목 불일치(서버오류)"),
        CUSTOM_1022("1022", "하위 디바이스 정보 미존재"),
        CUSTOM_2001("2001", "API 인증키 미존재"),
        CUSTOM_2002("2002", "잘못된 API 인증키"),
        CUSTOM_2003("2003", "API 인증키 만료"),
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
        private String userId;
        private String userNickname;
        private String hp;
        private Object device;
        private Object user;
        private Object invitation;
        private List<String> userIdList;
        public void setResult(ResponseType responseType, String msg) {
            String code = responseType.getCode();
            setResultCode(code);
//          String msg = responseType.getMsg();
            setResultMsg(msg);
        }

        @Getter
        @Setter
        public static class Device{
            private String deviceId;
            private String controlAuthKey;
            private String deviceNickname;
            private String regSort;
        }

        @Getter
        @Setter
        public static class User{
            private String userNickname;
            private String userId;
            private String householder;
        }

        @Getter
        @Setter
        public static class Invitation{
            private String invitationIdx;
            private String inviteAcceptYn;
            private String requestUserId;
            private String requestUserNick;
            private String responseUserId;
            private String responseUserNick;
            private String responseHp;
            private String inviteStartDate;
            private String inviteEndDate;
        }

    }
}
