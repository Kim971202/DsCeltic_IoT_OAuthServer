package com.oauth.config;

import com.oauth.jwt.ApiTokenInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfigure implements WebMvcConfigurer {

    /** 토큰 검증 인터셉터 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        // 토큰 인터셉터 추가
        registry.addInterceptor(apiTokenInterceptor())
                .excludePathPatterns("/users/v1/generateTempKey")
                .addPathPatterns("/users/v1/**")
                .addPathPatterns("/devices/v1/**")
                .addPathPatterns("/reservation/v1/**");
    }

    /** 토큰검증 인터셉터 빈 등록
     * @return
     */
    @Bean
    public ApiTokenInterceptor apiTokenInterceptor(){
        return new ApiTokenInterceptor();
    }
}
