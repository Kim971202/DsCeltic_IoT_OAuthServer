package com.oauth.config;

import com.oauth.constants.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.firewall.HttpFirewall;

import javax.servlet.http.HttpServletResponse;

@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SpringSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf()
                .disable()
                .headers()
                .frameOptions().sameOrigin()
                .and()
                .authorizeRequests()
                .anyRequest().permitAll()
                .and()
                .httpBasic()
                .disable() // 기본 인증 로그인 폼 안뜨도록 처리
                .formLogin()
                .loginPage(Constants.LOGIN_URI) // 기본 제공되는 form 말고, 커스텀 로그인 폼
                .and()
                .oauth2Login() // OAuth2 로그인 설정 시작점
                .failureHandler((request, response, exception) -> {
                    // TODO: Exception 처리 필요
                    if (exception instanceof Exception) {
                        response.sendRedirect("/finish");
                    } else {
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, exception.getMessage());
                    }
                })
                .userInfoEndpoint(); // OAuth2 로그인 성공 이후 사용자 정보를 가져올 때 설정 담당
        // .userService(customOAuth2UserService); // OAuth2 로그인 성공 시, 후 작업을 진행할 UserService 인터페이스 구현체 등록

        return http.build();
    }

    @Bean
    public HttpFirewall allowSemicolonHttpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowSemicolon(true);
        return firewall;
    }

    /**
     * Spring Security에서 무시해야할 경로
     */
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> {
            log.info("WebSecurityConfig configure(WebSecurity web) CALLED");
            web.httpFirewall(allowSemicolonHttpFirewall());
            web.ignoring()
                    .antMatchers("/", "/www/**", "/*.html", "/**/*.html", "/**/*.css", "/**/*.js", "/**/*.png", "/**/*.jpg", "/**/*.gif", "/**/*.svg", "/**/*.ico", "/**/*.ttf", "/**/*.woff", "/**/*.otf"); // allow anonymous resource requests
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}