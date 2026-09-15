package io.github.xianspiderman.iotops.device.importer;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class DeviceImportRow {
    @ExcelProperty(value = "SN", index = 0)
    private String sn;
    @ExcelProperty(value = "Device Name", index = 1)
    private String deviceName;
    @ExcelProperty(value = "Project Code", index = 2)
    private String projectCode;
    @ExcelProperty(value = "Product Code", index = 3)
    private String productCode;
    @ExcelProperty(value = "IMEI", index = 4)
    private String imei;
    @ExcelProperty(value = "MAC", index = 5)
    private String mac;
    @ExcelProperty(value = "Firmware Version", index = 6)
    private String firmwareVersion;
}
