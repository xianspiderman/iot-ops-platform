package io.github.xianspiderman.iotops.device.importer;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import io.github.xianspiderman.iotops.auth.DataScope;
import io.github.xianspiderman.iotops.auth.DataScopeService;
import io.github.xianspiderman.iotops.common.ApiResponse;
import io.github.xianspiderman.iotops.common.BusinessException;
import io.github.xianspiderman.iotops.common.PageResult;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/device-imports")
@RequiredArgsConstructor
public class DeviceImportController {
    private final DeviceImportService service;
    private final DataScopeService dataScopeService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SaCheckPermission("device:import")
    public ApiResponse<DeviceImportTask> upload(@RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new BusinessException("IMPORT_FILE_EMPTY", "Select a non-empty workbook");
        }
        Long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.ok(service.importWorkbook(file.getOriginalFilename(), file.getInputStream(), userId,
                dataScopeService.forUser(userId)));
    }

    @GetMapping
    @SaCheckPermission("device:import")
    public ApiResponse<PageResult<DeviceImportTask>> tasks(@RequestParam(defaultValue = "1") long page,
                                                           @RequestParam(defaultValue = "20") long size) {
        Long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.ok(service.pageTasks(page, size, userId, dataScopeService.forUser(userId)));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("device:import")
    public ApiResponse<DeviceImportTask> task(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.ok(service.getTask(id, userId, dataScopeService.forUser(userId)));
    }

    @GetMapping("/{id}/errors")
    @SaCheckPermission("device:import")
    public ApiResponse<List<DeviceImportError>> errors(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.ok(service.errors(id, userId, dataScopeService.forUser(userId)));
    }

    @GetMapping(value = "/{id}/errors.csv", produces = "text/csv")
    @SaCheckPermission("device:import")
    public void downloadErrors(@PathVariable Long id, HttpServletResponse response) throws IOException {
        Long userId = StpUtil.getLoginIdAsLong();
        List<DeviceImportError> errors = service.errors(id, userId, dataScopeService.forUser(userId));
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename=import-errors-" + id + ".csv");
        try (PrintWriter writer = response.getWriter()) {
            writer.write('\ufeff');
            writer.println("rowNo,sn,errorMessage,rawData");
            for (DeviceImportError error : errors) {
                writer.println(error.getRowNo() + "," + csv(safeSpreadsheetValue(error.getSn())) + ","
                        + csv(error.getErrorMessage()) + "," + csv(error.getRawData()));
            }
        }
    }

    private String safeSpreadsheetValue(String value) {
        if (value != null && !value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) {
            return "'" + value;
        }
        return value;
    }

    private String csv(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
