package com.etake.turnoverplan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TurnoverPlanApplication {

    public static void main(String[] args) {
        SpringApplication.run(TurnoverPlanApplication.class, args);
    }

}
