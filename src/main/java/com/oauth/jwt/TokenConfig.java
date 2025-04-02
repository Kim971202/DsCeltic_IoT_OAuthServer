package com.oauth.jwt;

import com.nimbusds.jose.JWEDecrypter;
import com.nimbusds.jose.JWEEncrypter;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.crypto.DirectEncrypter;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.oauth.service.impl.MobiusService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.crypto.SecretKey;

@Slf4j
@Getter
@Setter
@RequiredArgsConstructor
@Configuration
@ConfigurationProperties(prefix = "app.token")
public class TokenConfig {
    //현재 서버의 privateKey
    private String pathPrivateKey = "apikeys/private-key";
    //현재 서버의 publicKey
    private String pathPublicKey = "apikeys/public-key";
    //토큰 JWE 암호화에 사용되는 대칭키
    private String pathEncryptKey = "apikeys/encrypt-key";
    //XXX 서버간 토큰 유효기간(단위: 분)
    private long expirationTime = 525600; //1년

    //토큰 관련 암호화 키 init 처리 여부
    private boolean configSkip;

    //RSA
    private JWSVerifier jwsVerifier;
    private RSASSASigner rsaSigner;

    //AES
    private SecretKey aesKey;
    private JWEEncrypter jweEncrypter;
    private JWEDecrypter jweDecrypter;

    //서버의 publicKey
    /**
     * 플랫폼 퍼블릭 키는 플랫폼과 통신이 필요한 모든 서버가 가지고 있어야 하는 값이다.
     * 해당 값에 대한 정의는 .yml 파일안에 다음과 같은 형식으로 존재해야 한다.
     *
     * custom:
     *   token:
     *     path_platform_public_key: apikeys/platform-public-key
     */
    private String pathAppPublicKey = "apikeys/app-public-key";

    //서버의 privateKey
    private String pathAppPrivateKey = "apikeys/app-private-key";

    //서버의 토큰검증객체
    private JWSVerifier jwsVerifierApp;

    private final KeyStoreUtils keyStoreUtils;

    @PostConstruct
    public void init() {

        try {
            if( configSkip ) return;

            this.jwsVerifier = new RSASSAVerifier(keyStoreUtils.readPublicKey(this.getPathPublicKey()));
            this.rsaSigner = new RSASSASigner(keyStoreUtils.readPrivateKey(this.getPathPrivateKey()));

            this.aesKey = keyStoreUtils.getAesEncryptKey(this.getPathEncryptKey());
            this.jweEncrypter = new DirectEncrypter(aesKey);
            this.jweDecrypter = new DirectDecrypter(aesKey);

            setJwsVerifierApp();
        } catch(Exception e) {
            log.error("", e);
        }
    }

    private void setJwsVerifierApp() {
        if (StringUtils.hasText(this.pathAppPublicKey)){
            try {
                this.jwsVerifierApp = new RSASSAVerifier(keyStoreUtils.readPublicKey(this.pathAppPublicKey));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}