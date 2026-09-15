package io.github.xianspiderman.iotops.auth;

import cn.dev33.satoken.stp.StpInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SaTokenPermissionProvider implements StpInterface {
    private final PermissionCacheService cacheService;
    private final RbacMapper rbacMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return cacheService.permissions(Long.valueOf(loginId.toString()));
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return rbacMapper.selectRoleIdsByUser(Long.valueOf(loginId.toString())).stream().map(String::valueOf).toList();
    }
}
