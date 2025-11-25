package com.example.sw;

import lombok.Data;

@Data
public class GrupaDTO {
    private Long id;
    private String nazwa;

    public GrupaDTO(Long id, String nazwa) {
        this.id = id;
        this.nazwa = nazwa;
    }
}
