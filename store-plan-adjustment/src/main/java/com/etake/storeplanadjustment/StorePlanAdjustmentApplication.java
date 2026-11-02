package com.etake.storeplanadjustment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StorePlanAdjustmentApplication {

    public static void main(String[] args) {
        // Without this the process exits with 0 even when the job fails.
        System.exit(SpringApplication.exit(SpringApplication.run(StorePlanAdjustmentApplication.class, args)));
    }
}
