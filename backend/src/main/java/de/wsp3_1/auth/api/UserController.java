package de.wsp3_1.auth.api;

import de.wsp3_1.auth.api.AuthDtos.ClassAccountDto;
import de.wsp3_1.auth.api.AuthDtos.CreateUser;
import de.wsp3_1.auth.api.AuthDtos.PasswordSet;
import de.wsp3_1.auth.api.AuthDtos.UserDto;
import de.wsp3_1.auth.domain.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Kontenverwaltung der Planung (Vertrag: {@code GET/POST /api/users},
 * {@code POST /api/users/class-accounts}, {@code PUT /api/users/{id}/password}). Die Rollenpruefung
 * macht die Pfadregel in {@link SecurityConfig} — jeder Pfad unter {@code /api/**} ohne eigene Zeile
 * ist Planungssache.
 */
@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/api/users")
    public List<UserDto> list() {
        return userService.findAll().stream().map(UserDto::of).toList();
    }

    @PostMapping("/api/users")
    public ResponseEntity<UserDto> create(@Valid @RequestBody CreateUser request) {
        UserDto angelegt = UserDto.of(userService.create(
                request.username(), request.password(), request.role(), request.teacherId(), request.classId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(angelegt);
    }

    /**
     * Legt fuer jede aktive Klasse ohne Konto ein Klassenkonto an (E-50, ST-4). Die Antwort enthaelt
     * die Passwoerter <b>einmal</b> im Klartext — sie lassen sich danach nicht mehr auslesen, nur
     * neu setzen ({@code PUT /api/users/&#123;id&#125;/password}).
     *
     * <p>201 auch dann, wenn die Liste leer ist: der Aufruf ist idempotent, ein zweites Mal legt er
     * nichts an.</p>
     */
    @PostMapping("/api/users/class-accounts")
    public ResponseEntity<List<ClassAccountDto>> createClassAccounts() {
        List<ClassAccountDto> angelegt = userService.createClassAccounts().stream()
                .map(ClassAccountDto::of).toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(angelegt);
    }

    @PutMapping("/api/users/{id}/password")
    public ResponseEntity<Void> setPassword(@PathVariable Long id, @Valid @RequestBody PasswordSet request) {
        userService.setPassword(id, request.password());
        return ResponseEntity.noContent().build();
    }
}
