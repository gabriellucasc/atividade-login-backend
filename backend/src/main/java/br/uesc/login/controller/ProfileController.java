package br.uesc.login.controller;

import br.uesc.login.dto.ProfileResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ProfileController {
    @GetMapping("/profile")
    public ProfileResponse profile(Authentication authentication) {
        return new ProfileResponse(authentication.getName(),
                authentication.getAuthorities().iterator().next().getAuthority());
    }
}
