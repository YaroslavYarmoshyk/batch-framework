package com.etake.avgcheckplan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AvgCheckPlanApplication {

    public static void main(String[] args) {
        SpringApplication.run(AvgCheckPlanApplication.class, args);
    }

}
