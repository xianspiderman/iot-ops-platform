package io.github.xianspiderman.iotops.device;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.xianspiderman.iotops.common.BusinessException;
import io.github.xianspiderman.iotops.common.PageResult;
import io.github.xianspiderman.iotops.product.ProductMapper;
import io.github.xianspiderman.iotops.project.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeviceService {
    private final DeviceMapper mapper;
    private final ProjectMapper projectMapper;
    private final ProductMapper productMapper;

    public PageResult<Device> page(long page, long size, String keyword, Long projectId) {
        IPage<Device> result = mapper.selectPage(new Page<>(page, Math.min(size, 100)),
                Wrappers.<Device>lambdaQuery()
                        .and(keyword != null && !keyword.isBlank(), query -> query
                                .like(Device::getSn, keyword).or().like(Device::getDeviceName, keyword))
                        .eq(projectId != null, Device::getProjectId, projectId)
                        .orderByDesc(Device::getId));
        return PageResult.from(result);
    }

    public Device create(DeviceCommand command) {
        if (mapper.exists(Wrappers.<Device>lambdaQuery().eq(Device::getSn, command.sn()))) {
            throw new BusinessException("DEVICE_SN_EXISTS", "Device SN already exists");
        }
        if (projectMapper.selectById(command.projectId()) == null) {
            throw new BusinessException("PROJECT_NOT_FOUND", "Project does not exist");
        }
        if (productMapper.selectById(command.productId()) == null) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "Product does not exist");
        }
        Device device = new Device();
        device.setSn(command.sn().trim());
        device.setDeviceName(command.deviceName().trim());
        device.setProjectId(command.projectId());
        device.setProductId(command.productId());
        device.setImei(command.imei());
        device.setMac(command.mac());
        device.setFirmwareVersion(command.firmwareVersion());
        device.setOnlineStatus("UNKNOWN");
        mapper.insert(device);
        return mapper.selectById(device.getId());
    }

    public record DeviceCommand(String sn, String deviceName, Long projectId, Long productId,
                                String imei, String mac, String firmwareVersion) {
    }
}

