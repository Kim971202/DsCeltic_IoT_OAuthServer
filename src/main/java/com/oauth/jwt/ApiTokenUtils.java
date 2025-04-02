package com.oauth.jwt;

import com.nimbusds.jose.*;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.oauth.utils.Publics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiTokenUtils {
    private boolean isTokenSkip = false;

    private String serverId = "serverId";

    private final TokenConfig tokenConfig;
    private final ServerUtils serverUtils;

    public String getTransactionId(){
        return UUID.randomUUID().toString();
    }

    public TokenContentType getTokenType(String token){
        int count = StringUtils.countOccurrencesOf(token, ".");
        if (count == 4){
            return TokenContentType.JWE;
        }
        return TokenContentType.JWT;
    }

    public TokenMaterial verify(String token){
        try {
            if (TokenContentType.JWE == getTokenType(token)){
                token = decryptJWE(token);
            }
            if (isTokenSkip){
                return getTokenMaterial(SignedJWT.parse(token));
            } else if (verifyJWT(token)){
                return getTokenMaterial(SignedJWT.parse(token));
            }
        } catch (Exception e){
            log.error("", e);
        }
        return null;
    }

    /* *
     * JWE. 토큰 복호화 처리
     *
     * @param token
     * @oaram encKeyPath
     * @return 복호화 성공시 JWT 토큰 String. 그외 null
     * */
    public String decryptJWE(String token){
        try {
            JWEObject jweObject = JWEObject.parse(token);
            jweObject.decrypt(tokenConfig.getJweDecrypter());

            return jweObject.getPayload().toSignedJWT().serialize();
        } catch (Exception e) {
            log.error("", e);
        }
        return null;
    }

    public TokenMaterial getTokenMaterial(SignedJWT signedJWT) throws Exception {
        return TokenMaterial.builder()
                .header(TokenMaterial.Header.builder()
                .userId(signedJWT.getHeader().getCustomParam(TokenHeaderCustomParam.SID.getKey()).toString())
                .build()).build();
    }

    /* *
    * JWT, 토큰 Sign 검증 + 유효기간 체크 결과
    *
    * @param token
    * @return
    */
    public boolean verifyJWT(String token){
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);

            if (verifySignedJWT(signedJWT, tokenConfig.getJwsVerifierApp()) && verifyExpiredJWT(signedJWT)){
                return true;
            }
        } catch (Exception e){
            log.error("", e);
        }
        return false;
    }

    /* *
     * JWT, 토큰 Sign 검증
     *
     * @param token
     * @return
     */
    public boolean verifySignedJWT(SignedJWT signedJWT, JWSVerifier jwsVerifier){
        try {
            log.info("signedJWT.verify(jwsVerifier): " + signedJWT.verify(jwsVerifier));
            if (signedJWT.verify(jwsVerifier)){
                return true;
            }
        } catch (Exception e){
            log.error("", e);
        }
        log.info("verifySignedJWT false. ");
        return false;
    }

    /* *
     * JWT, 토큰 유효기간 검증
     * @param token
     * @param pubKeyPath
     * @return 정상 true, 그외 false
     */
    public boolean verifyExpiredJWT(SignedJWT signedJWT){
        try {
            Date exp = signedJWT.getJWTClaimsSet().getExpirationTime();
            Date now = new Date();

            if (!now.after(exp)) return true;
        } catch (Exception e){
            log.error("", e);
        }
        return false;
    }

    public String createJWT(TokenMaterial tokenMaterial){
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .customParam(TokenHeaderCustomParam.SID.getKey(), tokenMaterial.getHeader().getUserId())
                .contentType(tokenMaterial.getHeader().getContentType())
                .build();

        LocalDateTime timeStamp = serverUtils.getTimeAsiaSeoulNow();
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .issueTime(new Date())
                .expirationTime(Date.from(timeStamp.plusMinutes(tokenConfig.getExpirationTime()).atZone(ZoneId.systemDefault()).toInstant()))
                .claim(TokenPayload.FID.getKey(), tokenMaterial.getPayload().getFunctionId()) //function ID
                .build();

        return createJWT(header, claimsSet);
    }

    public String createJWT(JWSHeader header, JWTClaimsSet claimsSet){
        try {
            SignedJWT signedJWT = new SignedJWT(header, claimsSet);
            signedJWT.sign(tokenConfig.getRsaSigner());

            return signedJWT.serialize();
        } catch (Exception e){
            log.error("", e);
        }
        return null;
    }
}
