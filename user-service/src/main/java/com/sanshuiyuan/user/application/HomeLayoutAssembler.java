package com.sanshuiyuan.user.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sanshuiyuan.user.api.dto.HomeLayoutDto;
import com.sanshuiyuan.user.api.dto.SectionDto;
import com.sanshuiyuan.user.domain.Role;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class HomeLayoutAssembler {

    private final Cache<Role, HomeLayoutDto> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(10)
            .build();

    public HomeLayoutDto getLayout(Role role) {
        return cache.get(role, this::buildLayout);
    }

    private HomeLayoutDto buildLayout(Role role) {
        return switch (role) {
            case CONSUMER -> new HomeLayoutDto(List.of(
                    new SectionDto("scan_water", "扫码打水", 1, Map.of()),
                    new SectionDto("recharge", "充值", 2, Map.of()),
                    new SectionDto("nearby_devices", "附近设备", 3, Map.of())
            ));
            case OWNER -> new HomeLayoutDto(List.of(
                    new SectionDto("my_assets", "我的资产", 1, Map.of()),
                    new SectionDto("today_income", "今日收益", 2, Map.of()),
                    new SectionDto("match_notify", "撮合通知", 3, Map.of())
            ));
            case PROMOTER -> new HomeLayoutDto(List.of(
                    new SectionDto("invite_link", "邀请链接", 1, Map.of()),
                    new SectionDto("team_stats", "团队业绩", 2, Map.of())
            ));
        };
    }
}
