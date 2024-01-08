package com.oauth.dto.member;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * 회원 관련 데이터 클래스
 *
 * Spring Boot Security UserDetails 구현 방법
 * https://parandol.tistory.com/6?category=823695
 */
@Data
@Getter
@Setter
public class MemberDTO implements UserDetails, Serializable {

    private static final long serialVersionUID = 54436712726576487L;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String[] roles = null;
        if (authority != null) {
            authority = authority.trim();
            if (!authority.isEmpty()) {
                roles = authority.split(",");
            }
        }
        if (roles == null) {
            roles = new String[] {};
        }
        // 방법1
        ArrayList<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>();
        for (String authority : roles) {
            authorities.add(new SimpleGrantedAuthority(authority));
        }
        return authorities;
    }

    @Getter
    @RequiredArgsConstructor
    public enum Role {
        USER("ROLE_USER"),
        ADMIN("ROLE_ADMIN");
        private final String value;
    }

    private String authority;                    // 권한
    private String hp;                           // 사용자 전화번호
    private String userId;                       // 사용자 아이디
    private String userNickname;                 // 사용자 닉네임
    private String userPassword;                 // 사용자 비밀번호
    private String registrationDatetime;         // 사용자 등록일시
    private String lastLoginDatetime;            // 최종 로그인 일시
    private String accessToken;                  // API 인증키
    private String accessTokenCreateTime;        // API 인증키 생성 일시
    private String controlAuthKey;               // 기기 제어 키
    private String deviceId;                     // 기기 아이디
    private String deviceNickname;               // 기기 별칭
    private String regSort;                      // 기기 등록 순서

    private Role role = Role.USER;

    private boolean accountNonExpired = true;
    private boolean accountNonLocked = true;
    private boolean credentialsNonExpired = true;
    private boolean enabled = true;

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return null;
    }

    @Override
    public boolean isAccountNonExpired() {
        return false;
    }

    @Override
    public boolean isAccountNonLocked() {
        return false;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}

@SuppressWarnings("rawtypes")
class CustomAuthorityDeserializer extends JsonDeserializer {
    @Override
    public Object deserialize(JsonParser p, DeserializationContext context) throws IOException, JsonProcessingException {

        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        JsonNode jsonNode = mapper.readTree(p);
        LinkedList<GrantedAuthority> grantedAuthorities = new LinkedList<>();
        Iterator<JsonNode> elements = jsonNode.elements();
        while (elements.hasNext()) {
            JsonNode next = elements.next();
            JsonNode authority = next.get("authority");
            grantedAuthorities.add(new SimpleGrantedAuthority(authority.asText()));
        }
        return grantedAuthorities;
    }
}