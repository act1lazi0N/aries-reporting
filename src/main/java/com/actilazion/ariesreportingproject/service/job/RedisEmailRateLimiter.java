package com.actilazion.ariesreportingproject.service.job;

import com.actilazion.ariesreportingproject.config.EmailDeliveryProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RedisEmailRateLimiter implements EmailRateLimiter {
    private static final DefaultRedisScript<Long> TOKEN_BUCKET = new DefaultRedisScript<>("""
            local state = redis.call('HMGET', KEYS[1], 'tokens', 'timestamp')
            local tokens = tonumber(state[1])
            local timestamp = tonumber(state[2])
            local now = tonumber(ARGV[1])
            local refill = tonumber(ARGV[2])
            local capacity = tonumber(ARGV[3])
            if tokens == nil then
                tokens = capacity
                timestamp = now
            end
            local elapsed = math.max(0, now - timestamp)
            tokens = math.min(capacity, tokens + elapsed * refill)
            local allowed = 0
            if tokens >= 1 then
                tokens = tokens - 1
                allowed = 1
            end
            redis.call('HSET', KEYS[1], 'tokens', tokens, 'timestamp', now)
            redis.call('EXPIRE', KEYS[1], 60)
            return allowed
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final EmailDeliveryProperties properties;
    private final Clock clock;

    @Override
    public boolean tryAcquire() {
        Double refillPerMillisecond = properties.getPermitsPerSecond() / 1000.0;
        Long allowed = redisTemplate.execute(
                TOKEN_BUCKET,
                List.of(properties.getRateLimitKey()),
                Long.toString(Instant.now(clock).toEpochMilli()),
                Double.toString(refillPerMillisecond),
                Integer.toString(properties.getBurstCapacity()));
        return Long.valueOf(1L).equals(allowed);
    }
}
