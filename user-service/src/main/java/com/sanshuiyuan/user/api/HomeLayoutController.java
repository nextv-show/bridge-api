package com.sanshuiyuan.user.api;

import com.sanshuiyuan.user.api.dto.HomeLayoutDto;
import com.sanshuiyuan.user.application.HomeLayoutAssembler;
import com.sanshuiyuan.user.domain.Role;
import com.sanshuiyuan.user.domain.User;
import com.sanshuiyuan.user.infra.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/home")
public class HomeLayoutController {

    private final HomeLayoutAssembler homeLayoutAssembler;
    private final UserRepository userRepository;

    public HomeLayoutController(HomeLayoutAssembler homeLayoutAssembler, UserRepository userRepository) {
        this.homeLayoutAssembler = homeLayoutAssembler;
        this.userRepository = userRepository;
    }

    @GetMapping("/layout")
    public ResponseEntity<HomeLayoutDto> getLayout(@AuthenticationPrincipal Long userId,
                                                    @RequestParam(required = false) String role) {
        Role targetRole;
        if (role != null) {
            targetRole = Role.valueOf(role);
        } else {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            targetRole = user.getActiveRole();
        }
        return ResponseEntity.ok(homeLayoutAssembler.getLayout(targetRole));
    }
}
