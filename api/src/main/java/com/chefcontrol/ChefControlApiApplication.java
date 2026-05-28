package com.chefcontrol;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ChefControlApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChefControlApiApplication.class, args);
    }
}
