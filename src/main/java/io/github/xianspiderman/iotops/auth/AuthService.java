package io.github.xianspiderman.iotops.auth;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.xianspiderman.iotops.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public LoginResult login(String username, String password) {
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
        if (user == null || !"ENABLED".equals(user.getStatus())
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException("LOGIN_FAILED", "Invalid username or password");
        }
        StpUtil.login(user.getId());
        SaTokenInfo token = StpUtil.getTokenInfo();
        return new LoginResult(token.tokenName, token.tokenValue, user.getId(), user.getUsername(), user.getDisplayName());
    }

    public CurrentUser currentUser() {
        Long userId = StpUtil.getLoginIdAsLong();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "Current user no longer exists");
        }
        return new CurrentUser(user.getId(), user.getUsername(), user.getDisplayName());
    }

    public record LoginResult(String tokenName, String tokenValue, Long userId, String username, String displayName) {
    }

    public record CurrentUser(Long userId, String username, String displayName) {
    }
}

