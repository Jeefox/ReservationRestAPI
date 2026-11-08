package grevcev.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import grevcev.auth.service.JwtService;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final SecurityErrorWriter errors;
    private final JwtService jwtService;
    private final ReservationUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, ReservationUserDetailsService userDetailsService, SecurityErrorWriter errors) {
        this.errors = errors;
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 1. Читаем заголовок Authorization
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);  // токен не нашли — идем дальше
            return;
        }
        // 2. Вытаскиваем сам токен
        String token = authHeader.substring(7);

        try {
            // 3. Парсим — jjwt кинет, если токен подделан/просрочен
            Claims claims = jwtService.parseToken(token);
            String email = claims.getSubject();

            // 4. Загружаем юзера из БД (чтобы Security видел UserDetails с ролями)
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            // 5. Создаём Authentication-объект и кладём в SecurityContext
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ex) {
            SecurityContextHolder.clearContext();
            errors.write(response, 401, "Invalid or expired token");
            return;
        } catch (DataAccessException ex) {
            SecurityContextHolder.clearContext();
            log.error("Database failure during JWT authentication", ex);
            errors.write(response, 503, "Authentication service unavailable");
            return;
        }

        // Продолжаем цепочку после успешной аутентификации.
        filterChain.doFilter(request, response);
    }
}
