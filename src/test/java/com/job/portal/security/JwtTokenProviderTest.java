package com.job.portal.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest {

    @InjectMocks
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private Authentication authentication;

    private String jwtSecret = "VGhpcyBpcyBhIHZlcnkgbG9uZyBzZWNyZXQga2V5IGZvciBKV1QgdG9rZW4gZ2VuZXJhdGlvbiBhbmQgdmFsaWRhdGlvbg==";
    private long jwtExpirationDate = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", jwtSecret);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationDate", jwtExpirationDate);
    }

    @Test
    void generateToken_ShouldCreateValidToken() {
        when(authentication.getName()).thenReturn("testuser");

        String token = jwtTokenProvider.generateToken(authentication);

        assertNotNull(token);
        assertTrue(jwtTokenProvider.validateToken(token));
        assertEquals("testuser", jwtTokenProvider.getUsername(token));
    }

    @Test
    void validateToken_WithValidToken_ShouldReturnTrue() {
        when(authentication.getName()).thenReturn("testuser");
        String token = jwtTokenProvider.generateToken(authentication);

        assertTrue(jwtTokenProvider.validateToken(token));
    }

    @Test
    void validateToken_WithInvalidToken_ShouldReturnFalse() {
        assertFalse(jwtTokenProvider.validateToken("invalid-token"));
    }

    @Test
    void validateToken_WithExpiredToken_ShouldReturnFalse() {
        // Create an expired token manually
        Date now = new Date();
        Date pastDate = new Date(now.getTime() - 10000); // 10 seconds ago
        SecretKey key = Keys.hmacShaKeyFor(io.jsonwebtoken.io.Decoders.BASE64.decode(jwtSecret));

        String expiredToken = Jwts.builder()
                .subject("testuser")
                .issuedAt(new Date(now.getTime() - 20000))
                .expiration(pastDate)
                .signWith(key)
                .compact();

        assertFalse(jwtTokenProvider.validateToken(expiredToken));
    }

    @Test
    void getUsername_ShouldReturnCorrectUsername() {
        when(authentication.getName()).thenReturn("testuser");
        String token = jwtTokenProvider.generateToken(authentication);

        assertEquals("testuser", jwtTokenProvider.getUsername(token));
    }
}
