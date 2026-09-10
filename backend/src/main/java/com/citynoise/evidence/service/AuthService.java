package com.citynoise.evidence.service;

import com.citynoise.evidence.common.BusinessException;
import com.citynoise.evidence.common.ErrorCode;
import com.citynoise.evidence.dto.LoginRequest;
import com.citynoise.evidence.dto.LoginVO;
import com.citynoise.evidence.entity.SysUser;
import com.citynoise.evidence.mapper.SysUserMapper;
import com.citynoise.evidence.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public LoginVO login(LoginRequest request) {
        SysUser user = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, request.getUsername()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getEnabled() == null || user.getEnabled() != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }
        String token = tokenProvider.createToken(user.getId(), user.getUsername(), user.getRole());
        LoginVO.UserInfo info = new LoginVO.UserInfo(
                user.getId(), user.getUsername(), user.getRealName(), user.getRole());
        return new LoginVO(token, "Bearer", tokenProvider.getExpireSeconds(), info);
    }
}
