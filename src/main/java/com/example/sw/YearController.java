package com.example.sw;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/years")
public class YearController {

    private final DBEngine databaseService;

    public YearController(DBEngine databaseService) {
        this.databaseService = databaseService;
    }

    @GetMapping
    public ArrayList<String> getYears() {
        System.out.println("got year request!");
        try{
            ArrayList<String> years =(databaseService.getExistingYearNames());
            System.out.println(years);
            return years;
        }catch (SQLException e){
            e.printStackTrace();
            return null;
        }


    }

}
