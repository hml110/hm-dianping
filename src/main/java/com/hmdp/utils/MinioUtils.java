package com.hmdp.utils;

import com.hmdp.config.MinioProp;
import com.hmdp.dto.Result;
import io.minio.MinioClient;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;

@Slf4j
@Component
public class MinioUtils {

    @Resource
    private MinioClient client;

    @Resource
    private MinioProp minioProp;

    /**
     * 创建bucket
     *
     * @param bucketName bucket名称
     */
    @SneakyThrows
    public void createBucket(String bucketName) {
        if (!client.bucketExists(bucketName)) {
            client.makeBucket(bucketName);
        }
    }

    /**
     * 上传文件
     *
     * @param file
     * @param bucketName
     * @return
     */
    public Result uploadFile(MultipartFile file, String bucketName) {
        Result result = new Result();
        if (null == file || 0 == file.getSize()) {
            result.setErrorMsg("上传文件不能为空");
            return result;
        }
        try {
            // 判断存储桶是否存在
            createBucket(bucketName);
            // 文件名
            String originalFilename = file.getOriginalFilename();
            // 新的文件名 = 存储桶名称_时间戳.后缀名
            String fileName = bucketName + "_" + System.currentTimeMillis() + originalFilename.substring(originalFilename.lastIndexOf("."));
            // 开始上传
            client.putObject(bucketName,fileName,file.getInputStream(),file.getContentType());
            result.setSuccess(true);
            result.setData( minioProp.getEndpoint() + "/" + bucketName + "/" + fileName);
            return result;
        }catch (Exception e){
            log.error(e.getMessage());
        }
        return result;
    }


}
