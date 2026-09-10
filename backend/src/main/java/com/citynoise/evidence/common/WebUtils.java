package com.citynoise.evidence.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Web 辅助工具。
 */
public final class WebUtils {

    private WebUtils() {
    }

    public static String clientIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            HttpServletRequest request = attrs.getRequest();
            String ip = request.getHeader("X-Forwarded-For");
            if (ip != null && !ip.isBlank()) {
                return ip.split(",")[0].trim();
            }
            ip = request.getHeader("X-Real-IP");
            return ip != null && !ip.isBlank() ? ip : request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}
