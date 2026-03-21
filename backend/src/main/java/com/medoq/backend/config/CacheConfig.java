package com.medoq.backend.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    // Cache names
    public static final String CACHE_SEARCH    = "medications-search";
    public static final String CACHE_DETAIL    = "medication-detail";
    public static final String CACHE_POPULAR   = "popular-medications";
    public static final String CACHE_NEARBY    = "pharmacies-nearby";

    private static final Duration TTL_5_MIN  = Duration.ofMinutes(5);
    private static final Duration TTL_10_MIN = Duration.ofMinutes(10);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        return RedisCacheManager.builder(factory)
                .cacheDefaults(base.entryTtl(TTL_5_MIN))
                .withInitialCacheConfigurations(Map.of(
                    CACHE_SEARCH,  base.entryTtl(TTL_5_MIN),
                    CACHE_DETAIL,  base.entryTtl(TTL_5_MIN),
                    CACHE_POPULAR, base.entryTtl(TTL_10_MIN),  // popular changes slowly
                    CACHE_NEARBY,  base.entryTtl(TTL_5_MIN)
                ))
                .build();
    }
}
