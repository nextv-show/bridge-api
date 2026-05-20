package com.sanshuiyuan.asset.application.event;

/**
 * D.2.4: Published inside the payment transaction once an order is marked PAID and its assets
 * are created. The actual user-service call (granting the OWNER role) is deferred to after the
 * transaction commits so a downstream failure can never roll back or block the payment flow.
 */
public record OwnerRoleGrantRequested(Long userId) {
}
