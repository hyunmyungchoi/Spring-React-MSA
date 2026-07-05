package com.springmsa.memberbff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class MemberBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(MemberBffApplication.class, args);
    }
}
