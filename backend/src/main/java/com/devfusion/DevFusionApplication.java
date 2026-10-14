package com.devfusion;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.devfusion.**.mapper")
public class DevFusionApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevFusionApplication.class, args);
    }
}
