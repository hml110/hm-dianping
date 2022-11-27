package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 根据id查询商铺信息
     *
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @Override
    public Result queryById(Long id) {
        //空值对象解决缓存缓存穿透
//        Shop shop = queryWithPassThrough(id);

        //互斥锁解决缓存击穿
//        Shop shop = queryWithMutex(id);

        //逻辑过期解决缓存击穿
        Shop shop = queryWithLogicalExpire(id);

        if (shop == null) {
            return Result.fail("店铺不存在！");
        }

        //7.返回
        return Result.ok(shop);
    }


    /**
     * 逻辑过期解决缓存击穿
     * @param id
     * @return
     */
    public Shop queryWithLogicalExpire(Long id){
        String key = CACHE_SHOP_KEY + id;

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
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(),Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        //5.判断是否过期
        //isAfter() 在.....之后
        if (expireTime.isAfter(LocalDateTime.now())) {
            //5.1 未过期，直接返回店铺信息
            return shop;
        }
        //5.2 已过期，需要缓存重建
        //6.缓存重建
        //6.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(key);

        //6.2 判断是否获取锁成功
        if (isLock){
            //监测缓存是否过期

            //过期
            //6.3 成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
               try {
                   //重建
                   this.saveShop2Redis(id,20L);
               }catch (Exception e){
                   throw new RuntimeException(e);

               }finally {
                   //释放锁
                   this.unLock(lockKey);
               }
            });

        }
        //6.4 返回过期的商铺信息

        return shop;
    }


    //创建10个线程的线程池
    // ExecutorService是Java提供的线程池，也就是说，每次我们需要使用线程的时候，可以通过ExecutorService获得线程。
    public static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);






    /**
     * 考虑缓存穿透的获取商铺信息方法
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id){

        String key = CACHE_SHOP_KEY + id;
        //1.从redis中查询商铺缓存
        String shopJson = redisTemplate.opsForValue().get(key);

        //2.判断是否存在
        if (StrUtil.isNotBlank((shopJson))){
            //3.存在，直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

        //判断命中的是否是空值
        if (shopJson != null){
            //返回一个错误信息
            return null;
        }

        //4.不存在，根据id查询数据库
        Shop shop = getById(id);

        //5.不存在，返回错误
        if (shop == null) {
            //将空值写入Redis
            redisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            //返回错误信息
            return null;
        }
        //6.存在，写入redis,设置过期时间30min
        redisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //7.返回
        return shop;
    }



    /**
     * 考虑互斥锁解决缓存击穿来获取商铺信息方法
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id){
        String key = CACHE_SHOP_KEY + id;
        //1.从redis中查询商铺缓存
        String shopJson = redisTemplate.opsForValue().get(key);

        //2.判断是否存在
        if (StrUtil.isNotBlank((shopJson))){
            //3.存在，直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

        //判断命中的是否是空值
        if (shopJson != null){
            //返回一个错误信息
            return null;
        }

        //4、实现缓存重现
        //4.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            //4.2 判断是否获取成功
            if (!isLock){
                //4.3 失败，则休眠并重试
                Thread.sleep(50);
                queryWithMutex(id);
            }

            //4.4 成功，根据id查询数据库
            shop = getById(id);

            //5.不存在，返回错误
            if (shop == null) {
                //将空值写入Redis
                redisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
                //返回错误信息
                return null;
            }
            //6.存在，写入redis,设置过期时间30min
            redisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);

        }catch (InterruptedException e){
            throw new RuntimeException(e);
        }finally {
            //7.释放互斥锁
            unLock(lockKey);
        }
        //8.返回
        return shop;
    }






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
     * 向Redis存入具有逻辑过期时间的key
     * @param id shopId
     * @param expireSeconds 逻辑过期时间
     */
    public void saveShop2Redis(Long id,Long expireSeconds)  {
        //1.查询店铺数据
        Shop shop = getById(id);
        try {
            Thread.sleep(200);
        }catch (Exception e){
           throw  new RuntimeException(e);
        }
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        //plusSeconds() 在此基础上添加的时间
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.写入Redis
        redisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));

    }

    /**
     * 更新商铺信息
     *
     * @param shop 商铺数据
     * @return 无
     */
    @Override
    @Transactional
    public Result update(Shop shop) {

        Long id = shop.getId();
        if (id == null){
            return Result.fail("店铺id不能为空！");

        }
        //1.更新数据库
        updateById(shop);

        //2.删除缓存
        redisTemplate.delete(CACHE_SHOP_KEY +shop.getId());

        return Result.ok();
    }
}
