package com.example.sw;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = {"http://localhost:5500","https://studentpanel-sw-dm.onrender.com","https://studentpanel-front.onrender.com"})
@RestController
@RequestMapping("/api/auth")
public class LoginController {

    private final DBEngine databaseService;
    private final JwtService jwtservice;

    public LoginController(DBEngine databaseService, JwtService jwtservice) {
        this.databaseService = databaseService;
        this.jwtservice=jwtservice;
    }

    @CrossOrigin(origins = "http://localhost:5500")
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        System.out.println("got auth request!");

        // pobranie loginu i hasła z requesta
        String username = request.getUsername();
        String password = request.getPassword();

        // walidacja usera w twoim DBEngine
        String token = databaseService.validate(username, password);

        if (token == null) {
            return ResponseEntity.status(401).body("Invalid credentials");
        }

        // zwrócenie tokena w formie JSON
        return ResponseEntity.ok(Map.of("token", token));
    }

    @GetMapping("/me")
    public ResponseEntity<CustomUserDetails> me() {

        CustomUserDetails userDetails =
                (CustomUserDetails) SecurityContextHolder.getContext()
                        .getAuthentication()
                        .getPrincipal();
        System.out.println(userDetails.getType());
        int uczenId = userDetails.getId().intValue();
        String usertype = userDetails.getType();

        System.out.println("got me request");

        CustomUserDetails user = databaseService.loadUserByUsername(databaseService.getLoginById(uczenId));

        return ResponseEntity.ok(user);
    }

}


