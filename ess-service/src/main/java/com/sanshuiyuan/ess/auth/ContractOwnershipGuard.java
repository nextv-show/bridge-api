package com.sanshuiyuan.ess.auth;

import com.sanshuiyuan.ess.infra.client.UserServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * H5 合同 owner 校验：保证当前会话用户只能操作属于自己的合同。
 * <p>
 * 流程：取 {@link CurrentOpenid#require()}（无 token → 401）→ {@link UserServiceClient#resolveUserId}
 * → 与合同 {@code userId} 比对，不等抛 403；解析不到 userId（user-service 降级或查无此人）同样抛 403。
 */
@Component
public class ContractOwnershipGuard {

    private static final Logger log = LoggerFactory.getLogger(ContractOwnershipGuard.class);

    private final UserServiceClient userServiceClient;

    public ContractOwnershipGuard(UserServiceClient userServiceClient) {
        this.userServiceClient = userServiceClient;
    }

    /**
     * 断言当前会话用户为该合同归属人。
     *
     * @param contractUserId 合同归属 userId（{@code Contract#getUserId()}）
     * @throws ResponseStatusException 401（未登录）/ 403（非属主或无法解析身份）
     */
    public void assertOwner(Long contractUserId) {
        String openid = CurrentOpenid.require();
        Long resolvedUserId = userServiceClient.resolveUserId(openid);
        if (resolvedUserId == null) {
            log.warn("合同 owner 校验失败：无法将 openid 解析为 userId");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该合同");
        }
        if (!resolvedUserId.equals(contractUserId)) {
            log.warn("合同 owner 校验失败：会话 userId={} 与合同 userId={} 不一致",
                    resolvedUserId, contractUserId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该合同");
        }
    }
}
