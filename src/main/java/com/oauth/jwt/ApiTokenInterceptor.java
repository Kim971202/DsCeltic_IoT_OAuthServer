package com.oauth.jwt;

import com.oauth.utils.Publics;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
public class ApiTokenInterceptor implements HandlerInterceptor {

    @Autowired
    private ApiTokenUtils apiTokenUtils;

    @Override
    public boolean preHandle (HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        log.info("ApiTokenInterceptor -> preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)");

        // 서버 토큰 정보 송/수신은 request header Authorization key 값을 사용
        String accessToken = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (accessToken == null){
            response.sendError(HttpStatus.UNAUTHORIZED.value());
            return false;
        } else {
            accessToken = accessToken.replace("Bearer ", "");
            log.info("수신.accessToken: {}", accessToken);

            TokenMaterial token = apiTokenUtils.verify(accessToken);

            // sign 검증 실패시 or 유효기간 지난 경우
            if (token == null){
                response.sendError(HttpStatus.UNAUTHORIZED.value());
                return false;
            } else {
                request.setAttribute(Publics.KEY_API_ACCESS_TOKEN, token);

            }
        }
        return true;
    }

}




































