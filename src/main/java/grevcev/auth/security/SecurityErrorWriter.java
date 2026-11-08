package grevcev.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import grevcev.exception.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
public class SecurityErrorWriter {
    private final ObjectMapper mapper;

    public SecurityErrorWriter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), new ApiError(status, message, LocalDateTime.now(), null));
    }
}
