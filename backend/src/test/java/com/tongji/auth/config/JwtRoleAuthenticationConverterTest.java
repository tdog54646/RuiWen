package com.tongji.auth.config;

import com.tongji.auth.token.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtRoleAuthenticationConverterTest {

    @Test
    void rejectsRefreshTokenAsResourceServerCredential() {
        JwtService jwtService = mock(JwtService.class);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("1")
                .build();
        when(jwtService.extractTokenType(jwt)).thenReturn("refresh");

        JwtRoleAuthenticationConverter converter = new JwtRoleAuthenticationConverter(jwtService);

        assertThrows(OAuth2AuthenticationException.class, () -> converter.convert(jwt));
    }
}
