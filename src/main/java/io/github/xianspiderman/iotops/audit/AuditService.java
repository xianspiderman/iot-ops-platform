package io.github.xianspiderman.iotops.audit;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xianspiderman.iotops.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final BusinessAuditLogMapper mapper;
    private final ObjectMapper objectMapper;

    public void record(Long operatorId, String action, String targetType, String targetId, Map<String, ?> detail) {
        BusinessAuditLog log = new BusinessAuditLog();
        log.setOperatorId(operatorId);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(toJson(detail));
        log.setRequestId(MDC.get("requestId"));
        mapper.insert(log);
    }

    public PageResult<BusinessAuditLog> page(long page, long size) {
        return PageResult.from(mapper.selectPage(new Page<>(page, Math.min(size, 100)),
                Wrappers.<BusinessAuditLog>lambdaQuery().orderByDesc(BusinessAuditLog::getId)));
    }

    private String toJson(Map<String, ?> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize audit detail", exception);
        }
    }
}
