package com.citynoise.evidence.security;

import com.citynoise.evidence.common.BusinessException;
import com.citynoise.evidence.common.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从 SecurityContext 获取当前操作人。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static LoginUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof LoginUser user)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    public static String currentUsername() {
        try {
            return currentUser().getUsername();
        } catch (BusinessException e) {
            // 定时任务/初始化等无登录上下文场景
            return "system";
        }
    }
}
