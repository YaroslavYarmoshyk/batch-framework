package com.etake.cyclicforecast;

import com.excel.custom.library.annotation.EnableExcelLibrary;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableExcelLibrary
public class CyclicForecastApplication {

    static void main(String[] args) {
        SpringApplication.run(CyclicForecastApplication.class, args);
    }

}
