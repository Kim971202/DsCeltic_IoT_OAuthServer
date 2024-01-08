package com.oauth.config;

import com.oauth.constants.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.firewall.StrictHttpFirewall;

import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

@SuppressWarnings("deprecation")
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SpringSecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .csrf()
                .and()
                .headers()
                .frameOptions().sameOrigin()
                .and()
                .csrf()
                .disable()
                .authorizeRequests()
                .anyRequest().permitAll()
                .and();

        http
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
    }

    /**
     * Spring Security에서 무시해야할 경로
     */
    @Override
    public void configure(WebSecurity web) {

        System.out.println("WebSecurityConfig configure(WebSecurity web) CALLED");
        /**
         * org.springframework.security.web.firewall.RequestRejectedException: The request was rejected because the URL contained a potentially malicious String "%3B" 에러 처리
         * https://blog.csdn.net/qq_42483257/article/details/122426009
         * https://www.cnblogs.com/hetutu-5238/p/12145379.html
         */
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowSemicolon(true);
        web.httpFirewall(firewall);
        web.ignoring()
                .antMatchers("/", "/www/**", "/*.html", "/**/*.html", "/**/*.css", "/**/*.js", "/**/*.png", "/**/*.jpg", "/**/*.gif", "/**/*.svg", "/**/*.ico", "/**/*.ttf", "/**/*.woff", "/**/*.otf"); // allow anonymous resource requests
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
