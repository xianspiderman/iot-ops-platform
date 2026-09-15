package io.github.xianspiderman.iotops.integration;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Component
@Intercepts(@Signature(type = Executor.class, method = "update",
        args = {MappedStatement.class, Object.class}))
class BatchInsertStatementProbe implements Interceptor {
    private static final String BATCH_INSERT_STATEMENT =
            "io.github.xianspiderman.iotops.device.DeviceMapper.batchInsert";
    private static final Pattern VALUE_TUPLE = Pattern.compile("\\(\\s*\\?");

    private final AtomicInteger mapperCalls = new AtomicInteger();
    private final AtomicInteger sqlStatements = new AtomicInteger();
    private final AtomicInteger valueTuples = new AtomicInteger();
    private final AtomicReference<String> lastSql = new AtomicReference<>();

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
        if (BATCH_INSERT_STATEMENT.equals(statement.getId())) {
            String sql = statement.getBoundSql(invocation.getArgs()[1]).getSql()
                    .replaceAll("\\s+", " ").trim();
            mapperCalls.incrementAndGet();
            sqlStatements.incrementAndGet();
            valueTuples.set((int) VALUE_TUPLE.matcher(sql).results().count());
            lastSql.set(sql);
        }
        return invocation.proceed();
    }

    void reset() {
        mapperCalls.set(0);
        sqlStatements.set(0);
        valueTuples.set(0);
        lastSql.set(null);
    }

    Snapshot snapshot() {
        return new Snapshot(mapperCalls.get(), sqlStatements.get(), valueTuples.get(), lastSql.get());
    }

    record Snapshot(int mapperCalls, int sqlStatements, int valueTuples, String sql) {
    }
}
