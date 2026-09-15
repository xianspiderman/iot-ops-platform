package io.github.xianspiderman.iotops.device.taxonomy;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface DeviceTaxonomyRelationMapper {
    @Select("SELECT device_id FROM device_group_device WHERE group_id = #{groupId} ORDER BY device_id")
    List<Long> selectGroupDeviceIds(@Param("groupId") Long groupId);

    @Select("SELECT device_id FROM device_tag_device WHERE tag_id = #{tagId} ORDER BY device_id")
    List<Long> selectTagDeviceIds(@Param("tagId") Long tagId);

    @Delete("DELETE FROM device_group_device WHERE group_id = #{groupId}")
    int deleteGroupDevices(@Param("groupId") Long groupId);

    @Delete("DELETE FROM device_tag_device WHERE tag_id = #{tagId}")
    int deleteTagDevices(@Param("tagId") Long tagId);

    @Insert("INSERT INTO device_group_device(group_id, device_id) VALUES(#{groupId}, #{deviceId})")
    int insertGroupDevice(@Param("groupId") Long groupId, @Param("deviceId") Long deviceId);

    @Insert("INSERT INTO device_tag_device(tag_id, device_id) VALUES(#{tagId}, #{deviceId})")
    int insertTagDevice(@Param("tagId") Long tagId, @Param("deviceId") Long deviceId);
}
