package vn.ghtk.loyalty.util;

import org.springframework.security.core.Authentication;

public class SecurityUtil {

    /**
     * extract user ID from JWT token.
     */
    public static Long getUserIdFromAuthentication(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof String) {
            return Long.parseLong((String) authentication.getPrincipal());
        }
        return 0L;
    }
}

