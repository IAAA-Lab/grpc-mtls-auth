package dev.example.grpc.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ClientApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(ClientApplication.class, args)));
    }
}
