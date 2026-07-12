package com.springmsa.memberstockservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class MemberStockServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MemberStockServiceApplication.class, args);
    }
}
