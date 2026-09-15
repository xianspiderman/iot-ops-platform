package io.github.xianspiderman.iotops.audit;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.github.xianspiderman.iotops.common.ApiResponse;
import io.github.xianspiderman.iotops.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
public class AuditController {
    private final AuditService service;

    @GetMapping
    @SaCheckPermission("audit:read")
    public ApiResponse<PageResult<BusinessAuditLog>> page(@RequestParam(defaultValue = "1") long page,
                                                          @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(service.page(page, size));
    }
}
