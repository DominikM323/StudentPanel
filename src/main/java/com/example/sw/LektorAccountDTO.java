package com.example.sw;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LektorAccountDTO {

    public Integer id;
    public String imie;
    public String nazwisko;
    public String adres;
    public String telefon;

    public LocalDate umowaOd;
    public LocalDate umowaDo;

    public Integer loginId;
    public String login;

}
