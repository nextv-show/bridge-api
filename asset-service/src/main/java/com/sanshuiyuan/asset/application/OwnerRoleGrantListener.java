package com.sanshuiyuan.asset.application;

import com.sanshuiyuan.asset.application.event.OwnerRoleGrantRequested;
import com.sanshuiyuan.asset.infra.client.UserServiceClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * D.2.4: Grants the OWNER role after the payment transaction has committed, off the request
 * thread. {@link UserServiceClient#addOwnerRole(Long)} carries its own @Retryable/@Recover, so a
 * transient user-service outage is retried and ultimately alerted without affecting the payment.
 */
@Component
public class OwnerRoleGrantListener {

    private final UserServiceClient userServiceClient;

    public OwnerRoleGrantListener(UserServiceClient userServiceClient) {
        this.userServiceClient = userServiceClient;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOwnerRoleGrantRequested(OwnerRoleGrantRequested event) {
        userServiceClient.addOwnerRole(event.userId());
    }
}
