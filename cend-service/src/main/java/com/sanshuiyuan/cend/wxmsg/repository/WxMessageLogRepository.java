package com.sanshuiyuan.cend.wxmsg.repository;

import com.sanshuiyuan.cend.wxmsg.domain.WxMessageLog;
import com.sanshuiyuan.cend.wxmsg.domain.WxMsgStatus;
import com.sanshuiyuan.cend.wxmsg.domain.WxMsgType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WxMessageLogRepository extends JpaRepository<WxMessageLog, Long> {

    Optional<WxMessageLog> findByMsgTypeAndOrderId(WxMsgType msgType, Long orderId);

    boolean existsByMsgTypeAndOrderIdAndStatus(WxMsgType msgType, Long orderId, WxMsgStatus status);
}
