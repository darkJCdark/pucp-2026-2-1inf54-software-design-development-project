package com.pucp.paqrap.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(EscenariosProperties.class)
public class EscenariosConfig {

    /** Reloj real del sistema; los tests lo reemplazan con un bean {@code @Primary} controlable. */
    @Bean
    public Clock relojReal() {
        return Clock.systemUTC();
    }
}
