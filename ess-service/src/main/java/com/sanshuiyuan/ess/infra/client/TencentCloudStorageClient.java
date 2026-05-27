package com.sanshuiyuan.ess.infra.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 腾讯云端存储客户端（存根实现）。
 * <p>
 * 用于合同 PDF 的腾讯云端冗余存储。
 * 生产环境对接腾讯 COS SDK。
 */
@Component
public class TencentCloudStorageClient {

    private static final Logger log = LoggerFactory.getLogger(TencentCloudStorageClient.class);

    /**
     * 上传文件到腾讯云端。
     *
     * @param objectKey  对象键
     * @param data       文件数据
     * @param contentType 内容类型
     * @return 文件访问 URL
     */
    public String upload(String objectKey, byte[] data, String contentType) {
        log.info("[TencentCloud-Stub] 上传文件 objectKey={}, size={}, contentType={}",
                objectKey, data.length, contentType);
        return "https://cos.ap-guangzhou.myqcloud.com/sanshuiyuan-contracts/" + objectKey;
    }

    /**
     * 生成带签名的临时访问 URL。
     *
     * @param objectKey 对象键
     * @param expireSeconds 过期时间（秒）
     * @return 签名 URL
     */
    public String generatePresignedUrl(String objectKey, int expireSeconds) {
        log.debug("[TencentCloud-Stub] 生成签名URL objectKey={}, expire={}s", objectKey, expireSeconds);
        return "https://cos.ap-guangzhou.myqcloud.com/sanshuiyuan-contracts/" + objectKey + "?sign=stub&expire=" + expireSeconds;
    }

    /**
     * 检查文件是否存在。
     */
    public boolean exists(String objectKey) {
        return true;
    }
}
