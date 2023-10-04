package com.wooin.hahahaback.common.jwt;

import com.wooin.hahahaback.user.entity.UserRoleEnum;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Key;
import java.util.Base64;
import java.util.Date;

@Slf4j(topic = "JwtUtil")
@Component
public class JwtUtil {
    // Header KEY 값
    private final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;

    //access Token 관련 멤버
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String AUTHORIZATION_KEY = "auth";
    public static final String BEARER_PREFIX = "Bearer ";
    private final long TOKEN_TIME = 2 * 60 * 60 * 1000L; //2시간으로 세팅. 테스트시 임의로 수정해야함


    //Refresh Token 관련 멤버
    public static final String REFRESH_TOKEN_HEADER = "Refresh-Token";
    public static final String USER_ID_KEY = "userId";
    private final long REFRESH_TOKEN_TIME = 14 * 24 * 60 * 60 * 1000L; // 14일 세팅


    @Value("${jwt.secret.key}")
    private String secretKey;
    private Key key;


    @PostConstruct
    public void init() {
        byte[] bytes = Base64.getDecoder().decode(secretKey);
        key = Keys.hmacShaKeyFor(bytes);
    }

    public String createToken(String username, UserRoleEnum role) {
        Date date = new Date();

        return BEARER_PREFIX +
                Jwts.builder()
                        .setSubject(username)
                        .claim(AUTHORIZATION_KEY, role)
                        .setExpiration(new Date(date.getTime() + TOKEN_TIME))
                        .setIssuedAt(date)
                        .signWith(key, signatureAlgorithm)
                        .compact();
    }


    public String createRefreshToken(String username, UserRoleEnum role, Long userID) {
        Date date = new Date();

        return BEARER_PREFIX +
                Jwts.builder()
                        .setSubject(username)
                        .claim(AUTHORIZATION_KEY, role)
                        .claim(USER_ID_KEY, userID)
                        .setExpiration(new Date(date.getTime() + REFRESH_TOKEN_TIME))
                        .setIssuedAt(date)
                        .signWith(key, signatureAlgorithm)
                        .compact();
    }


    public String getAccessTokenFromHeader(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(7);
        }
        return null;
    }

    public String getRefreshTokenFromHeader(HttpServletRequest request) {
        String bearerToken = request.getHeader(REFRESH_TOKEN_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(7);
        }
        return null;
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.error("유효하지 않은 JWT 서명입니다.");
        } catch (UnsupportedJwtException e) {
            log.error("이 토큰은 지원되지 않는 JWT 토큰입니다.");
        } catch (IllegalArgumentException e) {
            log.error("잘못된 JWT 토큰입니다.");
        } catch (ExpiredJwtException e) {
            log.error("이 토큰은 만료된 JWT 토큰입니다.");
        }
        return false;
    }

    public Claims getUserInfoFromToken(String token) {
        if (StringUtils.hasText(token) && token.startsWith(BEARER_PREFIX)) {
            token = token.substring(7);
        }
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
    }

}