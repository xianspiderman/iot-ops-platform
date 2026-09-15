package io.github.xianspiderman.iotops.device.importer;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.exception.ExcelAnalysisException;
import io.github.xianspiderman.iotops.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
public class DeviceImportExcelReader {
    private final int maxRows;

    public DeviceImportExcelReader(@Value("${iot-ops.import.max-rows:10000}") int maxRows) {
        this.maxRows = maxRows;
    }

    public List<ParsedDeviceImportRow> read(InputStream inputStream) {
        List<ParsedDeviceImportRow> rows = new ArrayList<>();
        try {
            EasyExcel.read(inputStream, DeviceImportRow.class, new AnalysisEventListener<DeviceImportRow>() {
                @Override
                public void invoke(DeviceImportRow row, AnalysisContext context) {
                    if (rows.size() >= maxRows) {
                        throw new BusinessException("IMPORT_ROW_LIMIT",
                                "Import exceeds the configured limit of " + maxRows + " data rows");
                    }
                    rows.add(new ParsedDeviceImportRow(context.readRowHolder().getRowIndex() + 1, row));
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    // Validation is intentionally performed after the full key set has been collected.
                }
            }).headRowNumber(1).autoCloseStream(false).sheet().doRead();
        } catch (BusinessException exception) {
            throw exception;
        } catch (ExcelAnalysisException exception) {
            throw new BusinessException("IMPORT_FILE_INVALID", "The uploaded workbook cannot be read");
        }
        if (rows.isEmpty()) {
            throw new BusinessException("IMPORT_FILE_EMPTY", "The workbook contains no data rows");
        }
        return rows;
    }
}
