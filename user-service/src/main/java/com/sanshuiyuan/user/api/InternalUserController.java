package com.sanshuiyuan.user.api;

import com.sanshuiyuan.user.api.dto.AddRoleRequest;
import com.sanshuiyuan.user.api.dto.SyncH5Request;
import com.sanshuiyuan.user.api.dto.SyncH5Response;
import com.sanshuiyuan.user.api.dto.UserDirectoryItem;
import com.sanshuiyuan.user.application.RoleService;
import com.sanshuiyuan.user.application.SyncH5UseCase;
import com.sanshuiyuan.user.domain.Role;
import com.sanshuiyuan.user.domain.User;
import com.sanshuiyuan.user.infra.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final RoleService roleService;
    private final UserRepository userRepository;
    private final SyncH5UseCase syncH5UseCase;

    public InternalUserController(RoleService roleService, UserRepository userRepository,
                                  SyncH5UseCase syncH5UseCase) {
        this.roleService = roleService;
        this.userRepository = userRepository;
        this.syncH5UseCase = syncH5UseCase;
    }

    @PostMapping("/{id}/roles")
    public ResponseEntity<Void> addRole(@PathVariable("id") Long userId,
                                         @RequestBody AddRoleRequest request) {
        roleService.addRole(userId, Role.valueOf(request.getRole()));
        return ResponseEntity.ok().build();
    }

    /**
     * H5 登录成功后并号（spec 012）。/internal/** 已由 S2sTokenFilter 鉴权，不对外暴露。
     * inviterId 由 H5 端 RefIdCodec 解密后传入，仅首次创建写入关系链。
     */
    @PostMapping("/sync-h5")
    public SyncH5Response syncH5(@Valid @RequestBody SyncH5Request request) {
        return syncH5UseCase.sync(request.openid(), request.unionid(), request.inviterId());
    }

    /**
     * 分页导出真实用户目录（供 admin-service 同步）。
     * 返回 {items, total, page, size}，createdAt 为 ISO-8601 字符串。
     */
    @GetMapping
    public Map<String, Object> export(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "200") int size) {
        Page<User> result = userRepository.findAll(PageRequest.of(page, size, Sort.by("id")));
        List<UserDirectoryItem> items = result.getContent().stream()
                .map(u -> new UserDirectoryItem(
                        u.getId(),
                        u.getUnionid(),
                        u.getOpenidWx(),
                        u.getOpenidApp(),
                        u.getNickname(),
                        u.getAvatarUrl(),
                        u.getActiveRole() != null ? u.getActiveRole().name() : null,
                        u.getCreatedAt() != null ? u.getCreatedAt().toString() : null))
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("total", result.getTotalElements());
        body.put("page", page);
        body.put("size", size);
        return body;
    }
}
