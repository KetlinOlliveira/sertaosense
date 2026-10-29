package br.com.sertaosense.dispositivos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Ponto de entrada do serviço. O @SpringBootApplication escaneia este pacote e os subpacotes,
// então entidades, repositórios e controllers devem ficar abaixo de br.com.sertaosense.dispositivos.
@SpringBootApplication
public class DispositivosApplication {

    public static void main(String[] args) {
        SpringApplication.run(DispositivosApplication.class, args);
    }
}
