package com.sanshuiyuan.cend.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IdentityLinkRepository extends JpaRepository<IdentityLink, Long> {

    Optional<IdentityLink> findByOpenid(String openid);

    /** 跨端聚合：取关联到同一自然人（id_card_hash）的全部 openid 绑定。 */
    List<IdentityLink> findAllByIdCardHash(String idCardHash);
}
