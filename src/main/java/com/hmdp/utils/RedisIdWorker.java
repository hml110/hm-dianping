package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author hml
 * @version 1.0
 * @description: redis的id生成类
 * 格式：   符号位（1）   时间戳（31）   序列号（32）
 * @date 2022/12/25 15:29
 */
@Component
public class RedisIdWorker {

    //开始的时间戳 2022.1.1.0.0
   private static final long BEGIN_TIMESTAMP =  1640995200L;
   //序列号的位数
   private static final int COUNT_BITS =  32;


   private StringRedisTemplate redisTemplate;

    public RedisIdWorker(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public long nexId(String keyPrefix){
        //1.生成时间戳
        //1.1. 获取当前时间
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        //时间间隔
        long timeStamp = nowSecond - BEGIN_TIMESTAMP;


        //2.生成序列号
        //2.1 获取当前的日期，精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        //2.2 自增长 生成每天的一个新前缀，便于统计
        long count = redisTemplate.opsForValue().increment("icr: " + keyPrefix + ":" + date);

        //3.拼接并返回
        //使用位运算
        //时间戳的值向左移动32位,把位置空出来，在取一个或运算。
        return timeStamp << COUNT_BITS | count;
    }

    public static void main(String[] args) {
        //获取年、月、日、小时、分钟和秒的LocalDateTime实例，将纳秒设置为零。这将返回具有指定年、月、月、日、小时、分钟和秒的LocalDateTime。日期必须对年份和月份有效，否则将引发异常。纳秒字段将设置为零
        LocalDateTime time = LocalDateTime.of(2022,1,1,0,0,0);
        //将此日期时间转换为1970-01-01T00:00:00Z历元的秒数。这将结合本地日期时间和指定的偏移量来计算纪元秒值，即1970-01-01T00:00:00Z之间经过的秒数。历元之后的时间线上的瞬间是正的，较早的是负的。
        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println("second = " + second);
    }


}
