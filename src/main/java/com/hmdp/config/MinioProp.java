package com.hmdp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * minio 属性值
 */
@Data
@Component
@ConfigurationProperties(prefix = "spring.minio")
public class MinioProp {
    /**
     * 连接url
     */
    private String endpoint;
    /**
     * 用户名
     */
    private String accesskey;
    /**
     * 密码
     */
    private String secretKey;
}
 