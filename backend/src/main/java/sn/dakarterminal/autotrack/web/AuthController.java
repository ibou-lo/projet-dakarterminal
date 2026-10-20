package sn.dakarterminal.autotrack.web;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sn.dakarterminal.autotrack.security.AuthPrincipal;
import sn.dakarterminal.autotrack.service.AuthService;
import sn.dakarterminal.autotrack.service.PrivilegeService;
import sn.dakarterminal.autotrack.web.dto.Dtos.*;

@RestController
public class AuthController {

    private final AuthService auth;
    private final PrivilegeService privileges;

    public AuthController(AuthService auth, PrivilegeService privileges) {
        this.auth = auth;
        this.privileges = privileges;
    }

    /** Connexion du personnel : renvoie un jeton d'accès et un jeton de rafraîchissement. */
    @PostMapping("/api/auth/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest r) {
        return auth.login(r.username(), r.password());
    }

    @PostMapping("/api/auth/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest r) {
        return auth.refresh(r.refreshToken());
    }

    @GetMapping("/api/auth/me")
    public UserInfo me(@AuthenticationPrincipal AuthPrincipal me) {
        return auth.me(me);
    }

    /** Changement de son propre mot de passe (personnel). */
    @PostMapping("/api/auth/password")
    public TokenResponse changePassword(@Valid @RequestBody PasswordChangeRequest r, @AuthenticationPrincipal AuthPrincipal me) {
        return auth.changePassword(me.username(), r.currentPassword(), r.newPassword());
    }

    /** Déconnexion : invalide côté serveur tous les jetons du compte (personnel). */
    @PostMapping("/api/auth/logout")
    public java.util.Map<String, String> logout(@AuthenticationPrincipal AuthPrincipal me) {
        if (me != null && me.bl() == null) auth.logout(me.username());
        return java.util.Map.of("status", "ok");
    }

    /** Connexion client : numéro de BL + code reçu par SMS. */
    @PostMapping("/api/client/login")
    public TokenResponse clientLogin(@Valid @RequestBody ClientLoginRequest r) {
        return auth.clientLogin(r.blNumber(), r.smsCode());
    }
}
