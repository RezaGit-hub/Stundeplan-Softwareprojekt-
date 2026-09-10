package de.wsp3_1.auth.api;

import de.wsp3_1.auth.api.AuthDtos.LoginRequest;
import de.wsp3_1.auth.api.AuthDtos.LoginResponse;
import de.wsp3_1.auth.api.AuthDtos.MeDto;
import de.wsp3_1.auth.api.AuthDtos.PasswordChange;
import de.wsp3_1.auth.domain.AuthService;
import de.wsp3_1.auth.domain.LoginResult;
import de.wsp3_1.auth.domain.UserService;
import de.wsp3_1.shared.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anmelden, Abmelden, eigene Identitaet, eigenes Passwort (Vertrag: {@code /api/auth/*},
 * {@code GET /api/me}, {@code PUT /api/me/password}).
 *
 * <p>Alle vier Pfade gelten fuer beide Rollen. „Eigentum ist keine Rollenfrage": das Konto kommt
 * immer aus dem Principal, nie aus einem Parameter (api.md, Punkt 4).</p>
 */
@RestController
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/api/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = authService.login(request.username(), request.password());
        CurrentUser user = result.user();
        return new LoginResponse(result.token(), user.role(), result.displayName(), user.teacherId());
    }

    @DeleteMapping("/api/auth/token")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(TokenAuthenticationFilter.bearerToken(request));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/me")
    public MeDto me(@AuthenticationPrincipal CurrentUser user) {
        return new MeDto(user.id(), user.username(), user.role(), authService.displayName(user), user.teacherId());
    }

    @PutMapping("/api/me/password")
    public ResponseEntity<Void> changeMyPassword(@AuthenticationPrincipal CurrentUser user,
                                                 @Valid @RequestBody PasswordChange request) {
        userService.changeOwnPassword(user, request.oldPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
