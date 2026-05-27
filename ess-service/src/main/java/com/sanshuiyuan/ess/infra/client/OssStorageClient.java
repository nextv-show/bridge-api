package com.sanshuiyuan.ess.infra.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * 对象存储客户端（存根实现）。
 * <p>
 * 生产环境替换为实际 OSS SDK（腾讯 COS / 阿里云 OSS / MinIO）。
 * 当前提供基于 HTTP 的简化上传与 URL 生成能力。
 */
@Component
public class OssStorageClient {

    private static final Logger log = LoggerFactory.getLogger(OssStorageClient.class);

    /**
     * 上传文件到 OSS。
     *
     * @param objectKey  对象键（路径+文件名）
     * @param data       文件数据
     * @param contentType 内容类型
     * @return 文件访问 URL
     */
    public String upload(String objectKey, byte[] data, String contentType) {
        // 存根实现：实际调用 OSS SDK 上传
        log.info("[OSS-Stub] 上传文件 objectKey={}, size={}, contentType={}",
                objectKey, data.length, contentType);
        return "https://oss.sanshuiyuan.com/" + objectKey;
    }

    /**
     * 上传文件到 OSS（流式）。
     */
    public String upload(String objectKey, InputStream inputStream, long contentLength, String contentType) {
        log.info("[OSS-Stub] 流式上传 objectKey={}, contentLength={}, contentType={}",
                objectKey, contentLength, contentType);
        return "https://oss.sanshuiyuan.com/" + objectKey;
    }

    /**
     * 下载文件。
     *
     * @param objectKey 对象键
     * @return 文件字节数据
     */
    public byte[] download(String objectKey) {
        log.info("[OSS-Stub] 下载文件 objectKey={}", objectKey);
        return new byte[0];
    }

    /**
     * 生成带签名的临时访问 URL。
     *
     * @param objectKey 对象键
     * @param expireSeconds 过期时间（秒）
     * @return 签名 URL
     */
    public String generatePresignedUrl(String objectKey, int expireSeconds) {
        log.debug("[OSS-Stub] 生成签名URL objectKey={}, expire={}s", objectKey, expireSeconds);
        return "https://oss.sanshuiyuan.com/" + objectKey + "?sign=stub&expire=" + expireSeconds;
    }

    /**
     * 检查文件是否存在。
     */
    public boolean exists(String objectKey) {
        return true;
    }
}
