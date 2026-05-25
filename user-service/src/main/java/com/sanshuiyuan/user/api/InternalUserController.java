package com.sanshuiyuan.user.api;

import com.sanshuiyuan.user.api.dto.AddRoleRequest;
import com.sanshuiyuan.user.api.dto.UserDirectoryItem;
import com.sanshuiyuan.user.application.RoleService;
import com.sanshuiyuan.user.domain.Role;
import com.sanshuiyuan.user.domain.User;
import com.sanshuiyuan.user.infra.repository.UserRepository;
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

    public InternalUserController(RoleService roleService, UserRepository userRepository) {
        this.roleService = roleService;
        this.userRepository = userRepository;
    }

    @PostMapping("/{id}/roles")
    public ResponseEntity<Void> addRole(@PathVariable("id") Long userId,
                                         @RequestBody AddRoleRequest request) {
        roleService.addRole(userId, Role.valueOf(request.getRole()));
        return ResponseEntity.ok().build();
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
