package com.oauth.config;

import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

public class WebConfigure implements WebMvcConfigurer {

    /** 토큰 검증 인터셉터 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        // 토큰 인터셉터 추가
        registry.addInterceptor(null)
                .addPathPatterns("s");
    }
}
