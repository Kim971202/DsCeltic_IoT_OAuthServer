package com.oauth.jwt;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Builder
@Getter
@Setter
@ToString
public class TokenMaterial {

    private Header header;
    private Payload payload;

    @Builder
    @Getter
    @Setter
    @ToString
    public static class Header{
        // 요청자 사용자 ID
        private String userId;
        // JWT or JWE
        private String contentType;
    }

    @Builder
    @Getter
    @Setter
    @ToString
    public static class Payload{
        // FunctionId
        private String functionId;
    }
}
