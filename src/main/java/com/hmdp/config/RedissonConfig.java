package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置Redisson
 */
@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient(){
        // 配置
        Config config = new Config();
        config
                .useSingleServer()
                .setAddress("redis://192.168.19.128:6379")
                .setPassword("123456");
        // 创建
        return Redisson.create(config);
    }
    @Bean
    public RedissonClient redissonClient2(){
        // 配置
        Config config = new Config();
        config
                .useSingleServer()
                .setAddress("redis://192.168.19.128:6380")
                .setPassword("123456");
        // 创建
        return Redisson.create(config);
    }
    @Bean
    public RedissonClient redissonClient3(){
        // 配置
        Config config = new Config();
        config
                .useSingleServer()
                .setAddress("redis://192.168.19.128:6381")
                .setPassword("123456");
        // 创建
        return Redisson.create(config);
    }
}
