package com.oauth.config;

import com.oauth.jwt.ApiTokenInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

public class WebConfigure implements WebMvcConfigurer {

    /** 토큰 검증 인터셉터 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        // 토큰 인터셉터 추가
        registry.addInterceptor(apiTokenInterceptor())
                .addPathPatterns("s");
    }

    /** 토큰검증 인터셉터 빈 등록
     * @return
     */
    @Bean
    public ApiTokenInterceptor apiTokenInterceptor(){
        return new ApiTokenInterceptor();
    }
}
