package io.github.xianspiderman.iotops.device.taxonomy;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.xianspiderman.iotops.audit.AuditService;
import io.github.xianspiderman.iotops.auth.DataScope;
import io.github.xianspiderman.iotops.common.BusinessException;
import io.github.xianspiderman.iotops.device.Device;
import io.github.xianspiderman.iotops.device.DeviceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DeviceTaxonomyService {
    private final DeviceGroupMapper groupMapper;
    private final DeviceTagMapper tagMapper;
    private final DeviceTaxonomyRelationMapper relationMapper;
    private final DeviceMapper deviceMapper;
    private final AuditService auditService;

    public List<GroupView> groups(DataScope scope) {
        if (!scope.allProjects() && scope.projectIds().isEmpty()) {
            return List.of();
        }
        return groupMapper.selectList(Wrappers.<DeviceGroup>lambdaQuery()
                        .in(!scope.allProjects(), DeviceGroup::getProjectId, scope.projectIds())
                        .orderByAsc(DeviceGroup::getGroupName)).stream()
                .map(group -> new GroupView(group, relationMapper.selectGroupDeviceIds(group.getId())))
                .toList();
    }

    public List<TagView> tags(DataScope scope) {
        return tagMapper.selectList(Wrappers.<DeviceTag>lambdaQuery().orderByAsc(DeviceTag::getTagName)).stream()
                .map(tag -> new TagView(tag, visibleDeviceIds(relationMapper.selectTagDeviceIds(tag.getId()), scope)))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public DeviceGroup createGroup(Long projectId, String name, String description, Long operatorId) {
        DeviceGroup group = new DeviceGroup();
        group.setProjectId(projectId);
        group.setGroupName(name.trim());
        group.setDescription(description);
        groupMapper.insert(group);
        auditService.record(operatorId, "DEVICE_GROUP_CREATE", "DEVICE_GROUP", String.valueOf(group.getId()),
                Map.of("projectId", projectId, "name", name.trim()));
        return groupMapper.selectById(group.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public DeviceTag createTag(String name, String color, Long operatorId) {
        DeviceTag tag = new DeviceTag();
        tag.setTagName(name.trim());
        tag.setTagColor(color.toUpperCase());
        tagMapper.insert(tag);
        auditService.record(operatorId, "DEVICE_TAG_CREATE", "DEVICE_TAG", String.valueOf(tag.getId()),
                Map.of("name", name.trim(), "color", color.toUpperCase()));
        return tagMapper.selectById(tag.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void replaceGroupDevices(Long groupId, List<Long> deviceIds, DataScope scope, Long operatorId) {
        DeviceGroup group = groupMapper.selectById(groupId);
        if (group == null || !scope.permits(group.getProjectId())) {
            throw new BusinessException("DEVICE_GROUP_NOT_FOUND", "Device group does not exist in your scope");
        }
        Set<Long> distinct = validateDevices(deviceIds, scope);
        List<Device> devices = deviceMapper.selectBatchIds(distinct);
        if (devices.stream().anyMatch(device -> !group.getProjectId().equals(device.getProjectId()))) {
            throw new BusinessException("DEVICE_GROUP_PROJECT_MISMATCH", "Every device must belong to the group project");
        }
        relationMapper.deleteGroupDevices(groupId);
        distinct.forEach(deviceId -> relationMapper.insertGroupDevice(groupId, deviceId));
        auditService.record(operatorId, "DEVICE_GROUP_MEMBERS_REPLACE", "DEVICE_GROUP", String.valueOf(groupId),
                Map.of("deviceIds", distinct));
    }

    @Transactional(rollbackFor = Exception.class)
    public void replaceTagDevices(Long tagId, List<Long> deviceIds, DataScope scope, Long operatorId) {
        if (tagMapper.selectById(tagId) == null) {
            throw new BusinessException("DEVICE_TAG_NOT_FOUND", "Device tag does not exist");
        }
        Set<Long> distinct = validateDevices(deviceIds, scope);
        relationMapper.deleteTagDevices(tagId);
        distinct.forEach(deviceId -> relationMapper.insertTagDevice(tagId, deviceId));
        auditService.record(operatorId, "DEVICE_TAG_MEMBERS_REPLACE", "DEVICE_TAG", String.valueOf(tagId),
                Map.of("deviceIds", distinct));
    }

    private Set<Long> validateDevices(List<Long> deviceIds, DataScope scope) {
        Set<Long> distinct = new LinkedHashSet<>(deviceIds);
        List<Device> devices = distinct.isEmpty() ? List.of() : deviceMapper.selectBatchIds(distinct);
        if (devices.size() != distinct.size() || devices.stream().anyMatch(device -> !scope.permits(device.getProjectId()))) {
            throw new BusinessException("DEVICE_NOT_FOUND", "One or more devices do not exist in your scope");
        }
        return distinct;
    }

    private List<Long> visibleDeviceIds(List<Long> ids, DataScope scope) {
        if (scope.allProjects() || ids.isEmpty()) {
            return ids;
        }
        return deviceMapper.selectBatchIds(ids).stream()
                .filter(device -> scope.permits(device.getProjectId())).map(Device::getId).toList();
    }

    public record GroupView(DeviceGroup group, List<Long> deviceIds) { }
    public record TagView(DeviceTag tag, List<Long> deviceIds) { }
}
