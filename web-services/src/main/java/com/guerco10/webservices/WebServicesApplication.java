package com.guerco10.webservices;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.guerco10.webservices", "scad.webservices"})
public class WebServicesApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebServicesApplication.class, args);
    }

}
