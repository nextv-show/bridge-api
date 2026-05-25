package com.sanshuiyuan.h5.wxmsg.repository;

import com.sanshuiyuan.h5.wxmsg.domain.WxMessageLog;
import com.sanshuiyuan.h5.wxmsg.domain.WxMsgStatus;
import com.sanshuiyuan.h5.wxmsg.domain.WxMsgType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WxMessageLogRepository extends JpaRepository<WxMessageLog, Long> {

    Optional<WxMessageLog> findByMsgTypeAndOrderId(WxMsgType msgType, Long orderId);

    boolean existsByMsgTypeAndOrderIdAndStatus(WxMsgType msgType, Long orderId, WxMsgStatus status);
}
