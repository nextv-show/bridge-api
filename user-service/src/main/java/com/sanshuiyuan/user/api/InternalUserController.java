package com.sanshuiyuan.user.api;

import com.sanshuiyuan.user.api.dto.AddRoleRequest;
import com.sanshuiyuan.user.application.RoleService;
import com.sanshuiyuan.user.domain.Role;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final RoleService roleService;

    public InternalUserController(RoleService roleService) {
        this.roleService = roleService;
    }

    @PostMapping("/{id}/roles")
    public ResponseEntity<Void> addRole(@PathVariable("id") Long userId,
                                         @RequestBody AddRoleRequest request) {
        roleService.addRole(userId, Role.valueOf(request.getRole()));
        return ResponseEntity.ok().build();
    }
}
