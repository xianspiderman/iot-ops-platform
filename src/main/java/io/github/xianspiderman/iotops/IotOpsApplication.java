package io.github.xianspiderman.iotops;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("io.github.xianspiderman.iotops")
@SpringBootApplication
public class IotOpsApplication {
    public static void main(String[] args) {
        SpringApplication.run(IotOpsApplication.class, args);
    }
}

