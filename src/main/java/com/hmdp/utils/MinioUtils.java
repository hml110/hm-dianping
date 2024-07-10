package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.hmdp.config.MinioProp;
import com.hmdp.dto.FileVo;
import io.minio.*;
import io.minio.http.Method;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class MinioUtils {

    @Resource
    private MinioClient client;

    /**
     * 创建bucket
     *
     * @param bucketName bucket名称
     */
    @SneakyThrows
    public void createBucket(String bucketName) {
        // 检查存储桶是否已经存在
        if (client.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
            log.info("Bucket already exists.");
        } else {
            // 创建一个名为ota的存储桶
            client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            log.info("create a new bucket.");
        }
    }

    /**
     * @MonthName：upload
     * @Description： 上传文件
     * @Author：tanyp
     * @Date：2023/07/27 15:52
     * @Param： [file, bucketName]
     * @return：void
     **/
    public FileVo upload(MultipartFile file, String bucketName) {
        try {
            createBucket(bucketName);
            String oldName = file.getOriginalFilename();
            // 获取后缀
            String fileName = oldName.substring(0, oldName.lastIndexOf(".")) + "_" + System.currentTimeMillis() + oldName.substring(oldName.lastIndexOf("."));

            client.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fileName)
                            .stream(file.getInputStream(), file.getSize(), 0)
                            .contentType(file.getContentType()).build()
            );

            String url = this.getObjUrl(bucketName, fileName);
            return FileVo.builder()
                    .oldFileName(oldName)
                    .newFileName(fileName)
                    .fileUrl(url.substring(0, url.indexOf("?")))
                    .build();
        } catch (Exception e) {
            log.error("上传文件出错:{}", e);
            return null;
        }
    }

    /**
     * @MonthName：uploads
     * @Description： 上传多个文件
     * @Author：tanyp
     * @Date：2023/07/27 15:52
     * @Param： [file, bucketName]
     * @return：void
     **/
    public List<FileVo> uploads(List<MultipartFile> files, String bucketName) {
        try {
            List<FileVo> list = new ArrayList<>();
            createBucket(bucketName);

            for (MultipartFile file : files) {
                String oldName = file.getOriginalFilename();
                String fileName = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + UUID.randomUUID() + oldName.substring(oldName.lastIndexOf("."));

                client.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(fileName)
                                .stream(file.getInputStream(), file.getSize(), 0)
                                .contentType(file.getContentType()).build()
                );

                String url = this.getObjUrl(bucketName, fileName);
                list.add(
                        FileVo.builder()
                                .oldFileName(oldName)
                                .newFileName(fileName)
                                .fileUrl(url.substring(0, url.indexOf("?")))
                                .build()
                );
            }
            return list;
        } catch (Exception e) {
            log.error("上传文件出错:{}", e);
            return null;
        }
    }

    /**
     * @MonthName：download
     * @Description： 下载文件
     * @Author：tanyp
     * @Date：2023/07/27 15:54
     * @Param： [bucketName, fileName]
     * @return：void
     **/
    public void download(String bucketName, String fileName) throws Exception {
        client.downloadObject(DownloadObjectArgs.builder().bucket(bucketName).filename(fileName).build());
    }


    /**
     * @MonthName：getObjUrl
     * @Description： 获取文件链接
     * @Author：tanyp
     * @Date：2023/07/27 15:55
     * @Param： [bucketName, fileName]
     * @return：java.lang.String
     **/
    public String getObjUrl(String bucketName, String fileName) throws Exception {
        return client.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .bucket(bucketName)
                        .object(fileName)
                        .method(Method.GET)
                        .expiry(30, TimeUnit.SECONDS)
                        .build()
        );
    }


    /**
     * @MonthName：delete
     * @Description： 删除文件
     * @Author：tanyp
     * @Date：2023/5/26 15:56
     * @Param： [bucketName, fileName]
     * @return：void
     **/
    public void delete(String bucketName, String fileName) throws Exception {
        client.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(fileName).build());
    }

}
