package io.github.xianspiderman.iotops.project;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import io.github.xianspiderman.iotops.auth.DataScopeService;
import io.github.xianspiderman.iotops.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {
    private final ProjectService service;
    private final DataScopeService dataScopeService;

    @GetMapping
    @SaCheckPermission("project:read")
    public ApiResponse<List<Project>> list() {
        return ApiResponse.ok(service.list(dataScopeService.forUser(StpUtil.getLoginIdAsLong())));
    }

    @PostMapping
    @SaCheckPermission("project:write")
    public ApiResponse<Project> create(@Valid @RequestBody ProjectRequest request) {
        return ApiResponse.ok(service.create(new ProjectService.ProjectCommand(
                request.projectCode(), request.projectName(), request.description())));
    }

    public record ProjectRequest(@NotBlank @Size(max = 64) String projectCode,
                                 @NotBlank @Size(max = 128) String projectName,
                                 @Size(max = 500) String description) {
    }
}
