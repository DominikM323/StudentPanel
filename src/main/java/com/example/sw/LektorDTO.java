package com.example.sw;

import lombok.Data;

@Data
public class LektorDTO {

    private int id;
    private String nazwisko;

    public LektorDTO(int id,  String nazwisko) {
        this.id = id;
        this.nazwisko = nazwisko;
    }

}
