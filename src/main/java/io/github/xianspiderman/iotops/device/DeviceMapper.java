package io.github.xianspiderman.iotops.device;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface DeviceMapper extends BaseMapper<Device> {
    List<Device> selectBySns(@Param("sns") List<String> sns);

    int batchInsert(@Param("devices") List<Device> devices);
}
