package com.example.sw;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Date;
import java.sql.Time;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class LekcjaDTO {
    private int id;
    private Date data;
    private Time godzinaRozp;
    private Time godzinaKon;
    private String przedmiot;
    private String lokacja;
    private int lektorId;
    private String lektor;
    private int grupaId;
    private int rokId;

}
