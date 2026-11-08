package grevcev.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import grevcev.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {
    @Test
    void databaseFailureReturns503InsteadOfInvalidToken() throws Exception {
        JwtService jwt = mock(JwtService.class);
        ReservationUserDetailsService users = mock(ReservationUserDetailsService.class);
        Claims claims = mock(Claims.class);
        when(jwt.parseToken("valid")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("user@test.com");
        when(users.loadUserByUsername("user@test.com"))
                .thenThrow(new DataAccessResourceFailureException("Database unavailable"));
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var filter = new JwtAuthenticationFilter(jwt, users, new SecurityErrorWriter(mapper));
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(mapper.readTree(response.getContentAsString()).get("status").asInt()).isEqualTo(503);
        verifyNoInteractions(chain);
    }
}
