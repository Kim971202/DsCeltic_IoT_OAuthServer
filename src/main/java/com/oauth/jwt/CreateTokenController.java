package com.oauth.jwt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Token 생성을 시험 하는 Sample Class
@Slf4j
@RequiredArgsConstructor
@RestController
public class CreateTokenController {

    private String serverId = "serverId";

    private final ApiTokenUtils apiTokenUtils;
    private final ServerUtils serverUtils;


    @GetMapping("/token/jwt")
    public String createJWS() throws Exception {

        TokenMaterial tokenMaterial = TokenMaterial.builder()
                .header(TokenMaterial.Header.builder()
                        .userId("myId")
                        .contentType("myType")
                        .build())
                .payload(TokenMaterial.Payload.builder()
                        .functionId("myFunctionId")
                        .build())
                .build();


        String token = apiTokenUtils.createJWT(tokenMaterial);
        System.out.println("token: " + token);
        return token;
    }
}
