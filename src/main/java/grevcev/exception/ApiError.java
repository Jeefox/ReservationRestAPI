package grevcev.exception;

import java.util.*;
import java.time.LocalDateTime;

public record ApiError(
        int status,
        String message,
        LocalDateTime timestamp,
        Map<String, String> details
) {
}
