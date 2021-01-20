package com.rytech;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.hystrix.EnableHystrix;

@EnableHystrix
@SpringBootApplication
public class StarterApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(StarterApiApplication.class, args);
    }

}
