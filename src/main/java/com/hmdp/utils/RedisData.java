package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 具有逻辑过期时间的对象，减少对源代码的修改
 */
@Data
public class RedisData {
    //逻辑过期时间
    private LocalDateTime expireTime;
    //数据对象
    private Object data;
}
