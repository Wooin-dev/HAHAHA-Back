package com.wooin.hahahaback.common.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wooin.hahahaback.common.dto.ApiResponseDto;
import com.wooin.hahahaback.common.exception.NotFoundException;
import com.wooin.hahahaback.common.refreshtoken.dto.TokenDto;
import com.wooin.hahahaback.common.refreshtoken.repository.RefreshTokenRepository;
import com.wooin.hahahaback.common.security.UserDetailsServiceImpl;
import com.wooin.hahahaback.user.entity.UserRoleEnum;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j(topic = "JWT 검증 및 인가")
@RequiredArgsConstructor
public class JwtAuthorizationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;
    private final RefreshTokenRepository refreshTokenRepository;


    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain filterChain) throws ServletException, IOException {

        String accessToken = jwtUtil.getAccessTokenFromHeader(req);
        String refreshToken = jwtUtil.getRefreshTokenFromHeader(req);

        if (StringUtils.hasText(accessToken)) {

            if (!jwtUtil.validateToken(accessToken)) {
                if (jwtUtil.validateToken(refreshToken)) {
                    //refreshToken은 살아있을 때 AccessToken과 RefreshToken 재발급 후 해당 Jwt로 인증인가 진행

                    log.info("accessToken 재발급");

                    Claims infoFromRefreshToken = jwtUtil.getUserInfoFromToken(refreshToken);

                    String username = infoFromRefreshToken.getSubject();
                    Long userId = (Long) infoFromRefreshToken.get(JwtUtil.USER_ID_KEY);
                    TokenDto foundTokenDto = refreshTokenRepository.findById(userId).orElseThrow(()
                            -> new NotFoundException("Redis서버에서 RefreshToken 정보를 찾을 수 없습니다."));

                    if (foundTokenDto.getRefreshToken().substring(7).equals(refreshToken)) {
                        //재발급

                        // "USER" 로 저장되는 이유..? ROLE_USER가 아니라. 그냥 jwt에 담길때 이름만 String값으로 변환되어서 저장되서 그런가
                        String roleStr = (String) infoFromRefreshToken.get(JwtUtil.AUTHORIZATION_KEY);
                        UserRoleEnum role = UserRoleEnum.valueOfRole(roleStr);

                        accessToken = jwtUtil.createToken(username, role);
                        refreshToken = jwtUtil.createRefreshToken(username, role, userId);

                        res.addHeader(JwtUtil.AUTHORIZATION_HEADER, accessToken);
                        res.addHeader(JwtUtil.REFRESH_TOKEN_HEADER, refreshToken);

                        refreshTokenRepository.save(new TokenDto(userId, refreshToken, accessToken));
                    }
                } else {
                    res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    res.setContentType("application/json");
                    String result = new ObjectMapper().writeValueAsString(new ApiResponseDto(HttpStatus.BAD_REQUEST.value(), "INVALID_TOKEN"));

                    res.getOutputStream().print(result);
                    return;
                }
            }

            Claims info;
            try {
                info = jwtUtil.getUserInfoFromToken(accessToken);
            } catch (Exception e) {
                res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                res.setContentType("application/json");
                String result = new ObjectMapper().writeValueAsString(new ApiResponseDto(HttpStatus.BAD_REQUEST.value(), "INVALID_TOKEN"));

                res.getOutputStream().print(result);
                return;
            }

            try {
                setAuthentication(info.getSubject());
            } catch (Exception e) {
                log.error(e.getMessage());
                return;
            }
        }

        filterChain.doFilter(req, res);
    }

    // 인증 처리
    public void setAuthentication(String username) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        Authentication authentication = createAuthentication(username);
        context.setAuthentication(authentication);

        SecurityContextHolder.setContext(context);
    }

    // 인증 객체 생성
    private Authentication createAuthentication(String username) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }
}