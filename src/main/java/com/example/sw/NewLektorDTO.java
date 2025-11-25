package com.example.sw;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Date;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NewLektorDTO {
    private String imie;
    private String nazwisko;
    private String adres;
    private String telefon;
    private Date umowaOd;
    private Date umowaDo;
    private String login;
    private String haslo;
}
