package com.example.sw;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateLektorDTO {
    private Long id;        // ID lektora do edycji
    private String imie;
    private String nazwisko;
    private String adres;
    private String telefon;
    private String umowaOd;
    private String umowaDo;
    private String login;
}
