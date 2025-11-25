package com.example.sw;

import com.example.sw.DBEngine;
import com.example.sw.LektorDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final DBEngine databaseservice;

    public AdminController(DBEngine databaseservice) {
        this.databaseservice = databaseservice;
    }

    private boolean isLektor() {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            return false; // brak zalogowanego użytkownika
        }

        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof CustomUserDetails)) {
            return false; // nieoczekiwany typ
        }

        CustomUserDetails userDetails = (CustomUserDetails) principal;
        return "lektor".equals(userDetails.getType());
    }

    // Lektorzy

    @GetMapping("/lectors")
    public ResponseEntity<List<LektorAccountDTO>> getAllLectors() {

        if (!isLektor()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }

        return ResponseEntity.ok(databaseservice.getAllLectorData());
    }

    @PostMapping("/lectors")
    public ResponseEntity<LektorAccountDTO> createLector(@RequestBody NewLektorDTO lektor) {

        if (!isLektor()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }
        return ResponseEntity.ok(databaseservice.createLector(lektor));
    }

    @PutMapping("/lectors/{id}")
    public ResponseEntity<String> updateLector(
            @PathVariable Long id,
            @RequestBody UpdateLektorDTO dto) {
        dto.setId(id);
        boolean success = databaseservice.updateLector(dto);
        if(success) {
            return ResponseEntity.ok("Lektor zaktualizowany");
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Błąd przy aktualizacji lektora");
        }
    }

    @DeleteMapping("/lectors/{id}")
    public ResponseEntity<Void> deleteLector(@PathVariable int id) {
        if (!isLektor()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }
        boolean deleted = databaseservice.deleteLectorById(id);
        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/lectors/{id}/reset-password")
    public ResponseEntity<String> resetPassword(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        if (!isLektor()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Brak uprawnień");
        }

        String newPassword = body.get("password");
        if (newPassword == null || newPassword.isBlank()) {
            return ResponseEntity.badRequest().body("Hasło nie może być puste");
        }

        boolean ok = databaseservice.resetLectorPassword(id, newPassword);

        if (ok) return ResponseEntity.ok("Hasło zostało zresetowane");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Błąd resetowania hasła");
    }


}
