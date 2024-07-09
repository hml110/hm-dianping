package com.hmdp.config;


import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;


/**
 * minio 核心配置类
 */
@Configuration
@EnableConfigurationProperties(MinioProp.class)
public class MinioConfig {

    @Autowired
    private MinioProp minioProp;

    /**
     * 获取 MinioClient
     * @return
     */
    @Bean
    public MinioClient minioClient() {

        return MinioClient.builder().endpoint(minioProp.getEndpoint()).credentials(minioProp.getAccesskey(), minioProp.getSecretKey()).build();

    }

}
