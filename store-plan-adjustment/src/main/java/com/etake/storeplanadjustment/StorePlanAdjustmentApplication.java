package com.etake.storeplanadjustment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StorePlanAdjustmentApplication {

    public static void main(String[] args) {
        SpringApplication.run(StorePlanAdjustmentApplication.class, args);
    }
}
