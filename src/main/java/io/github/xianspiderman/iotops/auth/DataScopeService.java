package io.github.xianspiderman.iotops.auth;

import io.github.xianspiderman.iotops.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DataScopeService {
    private final RbacMapper rbacMapper;

    public DataScope forUser(Long userId) {
        if (rbacMapper.countAllScope(userId) > 0) {
            return DataScope.all();
        }
        return new DataScope(false, rbacMapper.selectProjectIdsByUser(userId));
    }

    public void requireProject(Long userId, Long projectId) {
        if (!forUser(userId).permits(projectId)) {
            throw new BusinessException("DATA_SCOPE_DENIED", "Project is outside the current user's data scope");
        }
    }
}
