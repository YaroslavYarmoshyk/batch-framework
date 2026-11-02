package com.etake.promotionforecast;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PromotionForecastApplication {

    static void main(String[] args) {
        SpringApplication.run(PromotionForecastApplication.class, args);
    }

}
