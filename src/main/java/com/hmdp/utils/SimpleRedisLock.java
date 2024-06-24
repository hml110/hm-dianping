package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * 对分布式锁的简单实现
 */
public class SimpleRedisLock implements ILock {

    /**
     * 锁名称
     */
    private String name;

    private StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "lock:";

    public SimpleRedisLock(String name, StringRedisTemplate redisTemplate) {
        this.name = name;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 尝试获取锁
     *
     * @param timeoutSec 锁持有的超时时间，过期后自动释放
     * @return true代表获取锁成功，false代表获取锁失败
     */
    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取线程的标识
        long threadId = Thread.currentThread().getId();
        // 获取锁
        Boolean success = redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, String.valueOf(threadId), timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success); //自动拆箱处理
    }

    /**
     * 释放锁
     */
    @Override
    public void unLock() {
        // 释放锁
        redisTemplate.delete(KEY_PREFIX + name);

    }
}
