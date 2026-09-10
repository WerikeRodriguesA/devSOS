package com.devsos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Ponto de entrada da aplicação DevSOS.
 * <p>
 * O {@code @SpringBootApplication} resume três anotações:
 * <ul>
 *   <li>{@code @Configuration} — marca esta classe como fonte de beans do Spring;</li>
 *   <li>{@code @EnableAutoConfiguration} — o Spring configura sozinho o que detectar
 *       no classpath (JPA, Web MVC, Validation etc.);</li>
 *   <li>{@code @ComponentScan} — o Spring "varre" este pacote e subpacotes para
 *       encontrar Controllers, Services, Repositórios e anotações.</li>
 * </ul>
 */
@SpringBootApplication
@EnableJpaAuditing
public class DevSosApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevSosApplication.class, args);
    }
}