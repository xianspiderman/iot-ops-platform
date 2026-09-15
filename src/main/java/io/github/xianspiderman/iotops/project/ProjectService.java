package io.github.xianspiderman.iotops.project;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.xianspiderman.iotops.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectMapper mapper;

    public List<Project> list() {
        return mapper.selectList(Wrappers.<Project>lambdaQuery().orderByAsc(Project::getProjectCode));
    }

    public Project create(ProjectCommand command) {
        if (mapper.exists(Wrappers.<Project>lambdaQuery().eq(Project::getProjectCode, command.projectCode()))) {
            throw new BusinessException("PROJECT_CODE_EXISTS", "Project code already exists");
        }
        Project project = new Project();
        project.setProjectCode(command.projectCode().trim());
        project.setProjectName(command.projectName().trim());
        project.setDescription(command.description());
        project.setStatus("ENABLED");
        mapper.insert(project);
        return mapper.selectById(project.getId());
    }

    public record ProjectCommand(String projectCode, String projectName, String description) {
    }
}

