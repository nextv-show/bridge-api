package com.sanshuiyuan.iot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 设备密钥（每 SN 一行）。{@code secretEnc} 为 AES-GCM 加密后的设备密钥密文；
 * {@code revokedAt} 非空表示已吊销。
 */
@Entity
@Table(name = "device_secrets")
public class DeviceSecret {

    @Id
    private String sn;

    @Column(name = "secret_enc", nullable = false)
    private byte[] secretEnc;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    protected DeviceSecret() {
    }

    public DeviceSecret(String sn, byte[] secretEnc, LocalDateTime issuedAt) {
        this.sn = sn;
        this.secretEnc = secretEnc;
        this.issuedAt = issuedAt;
    }

    public String getSn() { return sn; }
    public byte[] getSecretEnc() { return secretEnc; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
}
