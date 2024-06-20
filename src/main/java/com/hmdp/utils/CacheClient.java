package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @author hml
 * @version 1.0
 * @description: 处理Redis缓存的工具类
 * @date 2022/11/27 15:47
 */

@Slf4j
@Component
public class CacheClient {


    private final StringRedisTemplate redisTemplate;

    public CacheClient(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit){
        redisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(value),time,unit);
    }

    /**
     * 设置逻辑过期时间
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){

        RedisData redisData = new RedisData();
        //写入数据
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        redisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData),time,unit);
    }


    //创建10个线程的线程池
    // ExecutorService是Java提供的线程池，也就是说，每次我们需要使用线程的时候，可以通过ExecutorService获得线程。
    public static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    /**
     * 尝试获取锁
     * @param key
     * @return
     */
    private boolean tryLock(String key){
        //setnx()方法
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        //利用工具类返回值
        return BooleanUtil.isTrue(flag);
    }


    /**
     * 释放锁
     * @param key
     * @return
     */
    private void unLock(String key){
        redisTemplate.delete(key);
    }


    /**
     * 利用逻辑过期时间解决缓存击穿
     * @param keyPrefix id前缀
     * @param id id
     * @param type 对象类型
     * @param dbFallback 查询函数
     * @param time 过期时间
     * @param unit 时间单位
     * @return
     * @param <R>  对象类型
     * @param <T> id类型
     */
    public <R,T> R queryWithLogicalExpire(String keyPrefix, T id,Class<R> type,Function<T,R> dbFallback,Long time, TimeUnit unit){
        String key = keyPrefix + id;

        //1.从redis中查询商铺缓存
        String shopJson = redisTemplate.opsForValue().get(key);

        //2.判断是否命中
        if (StrUtil.isBlank((shopJson))){
            //3.不命中，直接返回null
            return null;
        }

        //4.命中，先把json反系列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        //本质是JSONObject类型
        R r = JSONUtil.toBean((JSONObject) redisData.getData(),type);
        LocalDateTime expireTime = redisData.getExpireTime();

        //5.判断是否过期
        //isAfter() 在.....之后
        if (expireTime.isAfter(LocalDateTime.now())) {
            //5.1 未过期，直接返回店铺信息
            return r;
        }
        //5.2 已过期，需要缓存重建
        //6.缓存重建
        //6.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);

        //6.2 判断是否获取锁成功
        if (isLock){
            //需要监测缓存是否过期

            //过期
            //6.3 成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    //查询数据库
                    R r1 = dbFallback.apply(id);

                    //写入Redis
                    this.setWithLogicalExpire(key,r1,time,unit);
                }catch (Exception e){
                    throw new RuntimeException(e);

                }finally {
                    //释放锁
                    this.unLock(lockKey);
                }
            });

        }
        //6.4 返回过期的商铺信息

        return r;
    }

    /**
     * 利用空值对象解决缓存缓存穿透
     * @param keyPrefix id前缀
     * @param id id
     * @param type 对象类型
     * @param dbFallback 查询函数
     * @param time 过期时间
     * @param unit 时间类型
     * @return
     * @param <R> 对象
     * @param <T> id类型
     */
    public <R,T> R queryWithPassThrough(String keyPrefix, T id, Class<R> type, Function<T,R> dbFallback,Long time, TimeUnit unit){

        String key = keyPrefix + id;
        //1.从redis中查询商铺缓存
        String json = redisTemplate.opsForValue().get(key);

        //2.判断是否存在
        if (StrUtil.isNotBlank((json))){
            //3.存在，直接返回
            return JSONUtil.toBean(json, type);
        }

        //判断命中的是否是空值
        if (json != null){
            //返回一个错误信息
            return null;
        }

        //4.不存在，根据id查询数据库
        R r = dbFallback.apply(id);

        //5.不存在，返回错误
        if (r == null) {
            //将空值写入Redis   过期时间两分钟
            redisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            //返回错误信息
            return null;
        }
        //6.存在，写入redis,设置过期时间30min
        this.set(key,r,time,unit);

        //7.返回
        return r;
    }
}
