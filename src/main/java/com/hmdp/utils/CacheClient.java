package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@Component
public class CacheClient {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogical(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    // 缓存穿透
    public <R, ID> R queryWithPathThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback,
                                          Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        // 判断是否存在
        if (StrUtil.isNotBlank(json)) {
            // 存在，直接返回
            return JSONUtil.toBean(json, type);
        }

        // 判断是否是空值
        if (json != null) {
            // 是空字符串，直接返回不存在
            return null;
        }

        // 不存在(null)，查询数据库
        R result = dbFallback.apply(id);

        // 不存在，返回错误
        if (result == null) {
            // 将空字符串写入redis，后续再查询该id时，查询到的是空字符串，直接返回错误信息
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 存在，写入redis
        this.set(key, result, time, unit);

        // 返回
        return result;
    }

    // 创建线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    // 设置逻辑过期时间解决缓存击穿
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback,
                                            Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            // 不存在，返回空
            return null;
        }

        // 缓存中存在数据
        // 检查是否逻辑过期
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        R result = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        // 没有过期，直接返回店铺信息
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            return result;
        }

        // 过期，获取互斥锁，判断是否获取锁成功
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        if (tryLock(lockKey)) {
            // double check缓存中是否存在数据，如果仍然不存在 ，开启独立线程实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                // 查询数据库
                R r = dbFallback.apply(id);

                // 写入redis
                this.setWithLogical(key, r, time, unit);

                // 释放锁
                unLock(lockKey);
            });
        }

        // 返回旧数据
        return result;
    }

    // 尝试取锁，如果存在锁，返回false
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10L, TimeUnit.SECONDS);
        return flag == null ? false : flag;
    }

    // 删除互斥锁
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

}
