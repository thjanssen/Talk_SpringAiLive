package com.thorben.janssen.piratestoreai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The AI part of the Pirate Store demo, split out into its own application:
 * a Spring AI {@code ChatClient} (RAG over {@code docs/*.md} plus MCP tools
 * fetched from the pirate-store application) exposed as a REST endpoint at
 * {@code POST /chat}. See README, "Architecture".
 */
@SpringBootApplication
public class PirateStoreAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PirateStoreAiApplication.class, args);
    }
}
