package io.github.xianspiderman.iotops.auth;

import java.util.List;

public record DataScope(boolean allProjects, List<Long> projectIds) {
    public static DataScope all() {
        return new DataScope(true, List.of());
    }

    public boolean permits(Long projectId) {
        return allProjects || projectIds.contains(projectId);
    }
}
