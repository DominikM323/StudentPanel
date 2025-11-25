package com.example.sw;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.awt.desktop.SystemEventListener;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/schedule")
public class LessonController {
    private final DBEngine databaseService;

    public LessonController(DBEngine databaseService) {
        this.databaseService = databaseService;
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

    @GetMapping
    public ResponseEntity<List<LekcjaDTO>> getSchedule(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam String yearName
    ) {
        System.out.println("got schedule request");
        try {
            //Pobierz ID ucznia z JWT
            CustomUserDetails userDetails =
                    (CustomUserDetails) SecurityContextHolder.getContext()
                            .getAuthentication()
                            .getPrincipal();
            System.out.println(userDetails.getType());
            int uczenId = userDetails.getId().intValue();
            String usertype = userDetails.getType();

            //Konwersja dat z zapytania
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);

            List<LekcjaDTO> lekcje = new ArrayList<>();

            //Pobranie lekcji
            System.out.println("user: "+usertype);
            if(usertype.equals("uczen")){
                 lekcje = databaseService.findLekcjeForStudent(
                        yearName,
                        uczenId,
                        start,
                        end
                );
            } else if (usertype.equals("lektor")) {
                lekcje = databaseService.findLekcjeForLektor(
                        yearName,
                        uczenId,
                        start,
                        end
                );
            }else{
                throw new RuntimeException("Nieznany typ użytkownika: " + usertype);
            }


            return ResponseEntity.ok(lekcje);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PutMapping("/lessonUpdate")
    public ResponseEntity<String> updateLesson(@RequestBody LekcjaDTO dto) {
        if (!isLektor()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Brak uprawnień");
        }
        try {
            boolean updated = databaseService.updateLesson(dto);
            if (updated) {
                return ResponseEntity.ok("Lekcja zaktualizowana");
            } else {
                return ResponseEntity.status(404).body("Lekcja nie istnieje");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Błąd serwera");
        }
    }

    @GetMapping("/lektorzy")
    public List<LektorDTO> getLektorzy() {
        List<LektorDTO> lektorzy = databaseService.getAllLectors();
        return lektorzy;
        //databaseService.update(dto);
    }

    @GetMapping("/grupy")
    public List<GrupaDTO> getAllGroups() {
        List<GrupaDTO> grupy = databaseService.getAllGroups();
        return grupy;
        //databaseService.update(dto);
    }

    @GetMapping("/lessons/{id}")
    public ResponseEntity<LekcjaDTO> getLesson(@PathVariable int id) {
        System.out.println("got lessonID req");
        LekcjaDTO lekcja = databaseService.getLessonById(id);
        if (lekcja == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(lekcja);
    }

    @PostMapping("/lessonCreate")
    public ResponseEntity<String> createLesson(@RequestBody LekcjaDTO dto, String yearName) {
        if (!isLektor()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Brak uprawnień");
        }
        try {
            // Weryfikacja minimalna: wszystkie pola muszą być niezerowe/null
            if (dto.getData() == null || dto.getPrzedmiot() == null || dto.getLokacja() == null ||
                    dto.getGodzinaRozp() == null || dto.getGodzinaKon() == null ||
                    dto.getGrupaId() == 0 || dto.getLektorId() == 0) {
                return ResponseEntity.badRequest().body("Wszystkie pola muszą być wypełnione");
            }

            boolean created = databaseService.createLesson(dto,yearName);
            if (created) {
                return ResponseEntity.ok("Lekcja utworzona");
            } else {
                return ResponseEntity.status(500).body("Nie udało się utworzyć lekcji");
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Błąd serwera: " + e.getMessage());
        }
    }

    @DeleteMapping("/lessonDelete/{id}")
    public ResponseEntity<String> deleteLesson(@PathVariable int id) {
        if (!isLektor()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Brak uprawnień");
        }
        try {
            boolean deleted = databaseService.deleteLesson(id);
            if (deleted) {
                return ResponseEntity.ok("Lekcja usunięta");
            } else {
                return ResponseEntity.status(404).body("Lekcja nie istnieje");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Błąd serwera");
        }
    }

}
