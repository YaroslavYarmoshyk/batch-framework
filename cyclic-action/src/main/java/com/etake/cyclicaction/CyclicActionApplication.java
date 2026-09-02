package com.etake.cyclicaction;

import com.excel.custom.library.annotation.EnableExcelLibrary;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableExcelLibrary
public class CyclicActionApplication {

    static void main(String[] args) {
        SpringApplication.run(CyclicActionApplication.class, args);
    }
}
