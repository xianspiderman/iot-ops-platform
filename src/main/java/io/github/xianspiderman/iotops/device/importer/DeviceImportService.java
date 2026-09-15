package io.github.xianspiderman.iotops.device.importer;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xianspiderman.iotops.auth.DataScope;
import io.github.xianspiderman.iotops.common.BusinessException;
import io.github.xianspiderman.iotops.common.PageResult;
import io.github.xianspiderman.iotops.device.Device;
import io.github.xianspiderman.iotops.device.DeviceMapper;
import io.github.xianspiderman.iotops.product.Product;
import io.github.xianspiderman.iotops.product.ProductMapper;
import io.github.xianspiderman.iotops.project.Project;
import io.github.xianspiderman.iotops.project.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DeviceImportService {
    private static final Pattern SN_PATTERN = Pattern.compile("[A-Z0-9][A-Z0-9_-]{2,63}");
    private static final Pattern IMEI_PATTERN = Pattern.compile("\\d{15}");
    private static final Pattern MAC_PATTERN = Pattern.compile("(?i)([0-9A-F]{2}[:-]){5}[0-9A-F]{2}");
    private static final int QUERY_BATCH_SIZE = 500;

    private final DeviceImportExcelReader excelReader;
    private final DeviceImportTaskStateService taskStateService;
    private final DeviceImportWriteService writeService;
    private final DeviceImportTaskMapper taskMapper;
    private final DeviceImportErrorMapper errorMapper;
    private final DeviceMapper deviceMapper;
    private final ProjectMapper projectMapper;
    private final ProductMapper productMapper;
    private final ObjectMapper objectMapper;

    public DeviceImportTask importWorkbook(String originalFileName, InputStream inputStream, Long operatorId) {
        return importWorkbook(originalFileName, inputStream, operatorId, DataScope.all());
    }

    public DeviceImportTask importWorkbook(String originalFileName, InputStream inputStream, Long operatorId,
                                           DataScope scope) {
        DeviceImportTask task = taskStateService.start(safeFileName(originalFileName), operatorId);
        try {
            List<ParsedDeviceImportRow> rows = excelReader.read(inputStream);
            Classification classification = classify(rows, scope);
            writeService.persist(task.getId(), rows.size(), classification.validDevices(), classification.errors());
            return taskMapper.selectById(task.getId());
        } catch (RuntimeException exception) {
            taskStateService.fail(task.getId(), exception.getMessage());
            throw exception;
        }
    }

    public DeviceImportTask getTask(Long taskId) {
        DeviceImportTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("IMPORT_TASK_NOT_FOUND", "Import task does not exist");
        }
        return task;
    }

    public DeviceImportTask getTask(Long taskId, Long userId, DataScope scope) {
        DeviceImportTask task = getTask(taskId);
        if (!scope.allProjects() && !userId.equals(task.getCreatedBy())) {
            throw new BusinessException("DATA_SCOPE_DENIED", "Import task belongs to another user");
        }
        return task;
    }

    public PageResult<DeviceImportTask> pageTasks(long page, long size) {
        return pageTasks(page, size, null, DataScope.all());
    }

    public PageResult<DeviceImportTask> pageTasks(long page, long size, Long userId, DataScope scope) {
        return PageResult.from(taskMapper.selectPage(new Page<>(page, Math.min(size, 100)),
                Wrappers.<DeviceImportTask>lambdaQuery()
                        .eq(!scope.allProjects(), DeviceImportTask::getCreatedBy, userId)
                        .orderByDesc(DeviceImportTask::getId)));
    }

    public List<DeviceImportError> errors(Long taskId) {
        getTask(taskId);
        return errorMapper.selectList(Wrappers.<DeviceImportError>lambdaQuery()
                .eq(DeviceImportError::getTaskId, taskId)
                .orderByAsc(DeviceImportError::getRowNo));
    }

    public List<DeviceImportError> errors(Long taskId, Long userId, DataScope scope) {
        getTask(taskId, userId, scope);
        return errorMapper.selectList(Wrappers.<DeviceImportError>lambdaQuery()
                .eq(DeviceImportError::getTaskId, taskId)
                .orderByAsc(DeviceImportError::getRowNo));
    }

    Classification classify(List<ParsedDeviceImportRow> rows) {
        return classify(rows, DataScope.all());
    }

    Classification classify(List<ParsedDeviceImportRow> rows, DataScope scope) {
        Set<String> projectCodes = new LinkedHashSet<>();
        Set<String> productCodes = new LinkedHashSet<>();
        Set<String> candidateSns = new LinkedHashSet<>();
        for (ParsedDeviceImportRow parsed : rows) {
            DeviceImportRow row = parsed.row();
            addIfPresent(projectCodes, normalizeCode(row.getProjectCode()));
            addIfPresent(productCodes, normalizeCode(row.getProductCode()));
            addIfPresent(candidateSns, normalizeSn(row.getSn()));
        }

        Map<String, Project> projects = new HashMap<>();
        if (!projectCodes.isEmpty()) {
            projectMapper.selectList(Wrappers.<Project>lambdaQuery().in(Project::getProjectCode, projectCodes))
                    .forEach(project -> projects.put(normalizeCode(project.getProjectCode()), project));
        }
        Map<String, Product> products = new HashMap<>();
        if (!productCodes.isEmpty()) {
            productMapper.selectList(Wrappers.<Product>lambdaQuery().in(Product::getProductCode, productCodes))
                    .forEach(product -> products.put(normalizeCode(product.getProductCode()), product));
        }
        Set<String> existingSns = loadExistingSns(new ArrayList<>(candidateSns));

        Set<String> seenSns = new HashSet<>();
        List<Device> valid = new ArrayList<>();
        List<DeviceImportError> errors = new ArrayList<>();
        for (ParsedDeviceImportRow parsed : rows) {
            DeviceImportRow row = parsed.row();
            String sn = normalizeSn(row.getSn());
            String projectCode = normalizeCode(row.getProjectCode());
            String productCode = normalizeCode(row.getProductCode());
            List<String> messages = new ArrayList<>();
            require(messages, sn, "SN is required");
            if (sn != null && !SN_PATTERN.matcher(sn).matches()) {
                messages.add("SN format is invalid");
            }
            String deviceName = trim(row.getDeviceName());
            require(messages, deviceName, "Device name is required");
            if (deviceName != null && deviceName.length() > 128) {
                messages.add("Device name exceeds 128 characters");
            }
            require(messages, projectCode, "Project code is required");
            require(messages, productCode, "Product code is required");
            if (projectCode != null && !projects.containsKey(projectCode)) {
                messages.add("Project code does not exist");
            } else if (projectCode != null && !scope.permits(projects.get(projectCode).getId())) {
                messages.add("Project is outside your data scope");
            }
            if (productCode != null && !products.containsKey(productCode)) {
                messages.add("Product code does not exist");
            }
            if (sn != null && !seenSns.add(sn)) {
                messages.add("SN is duplicated in this file");
            }
            if (sn != null && existingSns.contains(sn)) {
                messages.add("SN already exists in the database");
            }
            String imei = trim(row.getImei());
            if (imei != null && !IMEI_PATTERN.matcher(imei).matches()) {
                messages.add("IMEI must contain 15 digits");
            }
            String mac = trim(row.getMac());
            if (mac != null && !MAC_PATTERN.matcher(mac).matches()) {
                messages.add("MAC address format is invalid");
            }

            if (!messages.isEmpty()) {
                errors.add(error(parsed, sn, String.join("; ", messages)));
                continue;
            }
            Device device = new Device();
            device.setSn(sn);
            device.setDeviceName(deviceName);
            device.setProjectId(projects.get(projectCode).getId());
            device.setProductId(products.get(productCode).getId());
            device.setImei(imei);
            device.setMac(mac == null ? null : mac.toUpperCase(Locale.ROOT).replace('-', ':'));
            device.setFirmwareVersion(trim(row.getFirmwareVersion()));
            device.setOnlineStatus("UNKNOWN");
            valid.add(device);
        }
        return new Classification(valid, errors);
    }

    private Set<String> loadExistingSns(List<String> sns) {
        Set<String> result = new HashSet<>();
        for (int start = 0; start < sns.size(); start += QUERY_BATCH_SIZE) {
            int end = Math.min(start + QUERY_BATCH_SIZE, sns.size());
            deviceMapper.selectBySns(sns.subList(start, end)).stream()
                    .map(Device::getSn).map(this::normalizeSn).forEach(result::add);
        }
        return result;
    }

    private DeviceImportError error(ParsedDeviceImportRow parsed, String sn, String message) {
        DeviceImportError error = new DeviceImportError();
        error.setRowNo(parsed.rowNo());
        error.setSn(sn);
        error.setRawData(toJson(parsed.row()));
        error.setErrorMessage(message);
        return error;
    }

    private String toJson(DeviceImportRow row) {
        try {
            return objectMapper.writeValueAsString(row);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize import row", exception);
        }
    }

    private String safeFileName(String value) {
        String normalized = value == null ? "devices.xlsx" : value.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return name.isBlank() ? "devices.xlsx" : name.substring(0, Math.min(name.length(), 255));
    }

    private String normalizeSn(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String normalizeCode(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String trim(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private void addIfPresent(Set<String> set, String value) {
        if (value != null) {
            set.add(value);
        }
    }

    private void require(List<String> errors, String value, String message) {
        if (value == null) {
            errors.add(message);
        }
    }

    record Classification(List<Device> validDevices, List<DeviceImportError> errors) {
    }
}
