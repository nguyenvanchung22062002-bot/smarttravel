package com.smarttravel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SmartTravelApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartTravelApplication.class, args);
        System.out.println("SmartTravel is running ...!");
        System.out.println("API: http://localhost:8080");

    }
}
