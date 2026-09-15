package io.github.xianspiderman.iotops.timeout;

import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TimeoutInspectionJob {
    private final TimeoutInspectionService service;

    @XxlJob("workOrderTimeoutInspection")
    public void inspect() {
        service.run("XXL_JOB");
    }
}
