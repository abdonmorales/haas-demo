package com.example.haas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HaasApplication {

    public static void main(String[] args) {
        SpringApplication.run(HaasApplication.class, args);
    }
}
