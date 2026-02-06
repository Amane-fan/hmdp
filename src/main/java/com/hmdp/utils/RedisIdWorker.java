package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 开始时间戳
    private static final long BEGIN_TIMESTAMP = 1767225600L;
    private static final int COUNT_BITS = 32;

    // 获取某一个缓存的下一个不重复id
    public long nextId(String keyPrefix) {
        // 生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long nowStamp = nowSecond - BEGIN_TIMESTAMP;

        // 生成序列号
        // 获取当天的时间戳作为key的后缀
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        // 拼接并返回
        return nowStamp << COUNT_BITS | count;
    }

}
