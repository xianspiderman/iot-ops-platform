package io.github.xianspiderman.iotops.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BootstrapPasswordInitializer implements ApplicationRunner {
    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, "admin"));
        if (user != null && "{BOOTSTRAP}".equals(user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode("Admin@123"));
            userMapper.updateById(user);
        }
        SysUser operator = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, "operator"));
        if (operator != null && "{BOOTSTRAP_OPERATOR}".equals(operator.getPasswordHash())) {
            operator.setPasswordHash(passwordEncoder.encode("Operator@123"));
            userMapper.updateById(operator);
        }
    }
}
