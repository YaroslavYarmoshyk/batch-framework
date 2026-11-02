package com.etake.coresheetrenewal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CoreSheetRenewalApplication {

    static void main(String[] args) {
        SpringApplication.run(CoreSheetRenewalApplication.class, args);
    }

}
