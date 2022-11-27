package com.hmdp.utils;

public class RedisConstants {
    //登录验证码前缀
    public static final String LOGIN_CODE_KEY = "login:code:";
    //登录验证码有效期
    public static final Long LOGIN_CODE_TTL = 2L;
    //存储用户token的前缀
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;

    //店铺空值缓存的时间
    public static final Long CACHE_NULL_TTL = 2L;

    //店铺缓存的时间
    public static final Long CACHE_SHOP_TTL = 30L;

    //店铺缓存的常量
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    //店铺类型缓存的常量
    public static final String CACHE_SHOP_TYPE_KEY = "cache:shopType:";

    //店铺类型缓存的时间
    public static final Long CACHE_SHOP_TYPE_TTL = 30L;

    //互斥锁前缀
    public static final String LOCK_SHOP_KEY = "lock:shop:";

    //设置的锁有效期
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
}
