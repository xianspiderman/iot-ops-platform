package io.github.xianspiderman.iotops.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class PermissionCacheService {
    private static final String KEY_PREFIX = "iotops:permission:user:";

    private final RbacMapper rbacMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;
    private final Counter hitCounter;
    private final Counter missCounter;
    private final Counter invalidationCounter;
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong invalidations = new AtomicLong();

    public PermissionCacheService(RbacMapper rbacMapper, StringRedisTemplate redis, ObjectMapper objectMapper,
                                  MeterRegistry meterRegistry,
                                  @Value("${iot-ops.permission-cache.ttl:PT30M}") Duration ttl) {
        this.rbacMapper = rbacMapper;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
        this.hitCounter = meterRegistry.counter("iot.permission.cache.hit");
        this.missCounter = meterRegistry.counter("iot.permission.cache.miss");
        this.invalidationCounter = meterRegistry.counter("iot.permission.cache.invalidation");
    }

    public List<String> permissions(Long userId) {
        String key = key(userId);
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                hits.incrementAndGet();
                hitCounter.increment();
                return objectMapper.readValue(cached, new TypeReference<>() { });
            }
        } catch (Exception exception) {
            log.warn("permission_cache_read_failed userId={}", userId, exception);
        }

        misses.incrementAndGet();
        missCounter.increment();
        List<String> permissions = rbacMapper.selectPermissionCodes(userId);
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(permissions), ttl);
        } catch (Exception exception) {
            log.warn("permission_cache_write_failed userId={}", userId, exception);
        }
        return permissions;
    }

    public void evictRequired(Long userId) {
        try {
            redis.delete(key(userId));
            invalidations.incrementAndGet();
            invalidationCounter.increment();
        } catch (Exception exception) {
            throw new IllegalStateException("Permission cache could not be invalidated", exception);
        }
    }

    public void evictBestEffort(Long userId) {
        try {
            evictRequired(userId);
        } catch (RuntimeException exception) {
            log.error("permission_cache_invalidation_failed userId={}", userId, exception);
        }
    }

    public CacheStats snapshot() {
        return new CacheStats(hits.get(), misses.get(), invalidations.get());
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }

    public record CacheStats(long hits, long misses, long invalidations) {
    }
}
