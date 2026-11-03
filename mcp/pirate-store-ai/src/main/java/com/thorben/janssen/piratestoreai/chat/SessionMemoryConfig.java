package com.thorben.janssen.piratestoreai.chat;

import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.SessionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This application has no database, so the conversations are kept in memory
 * and are lost on restart.
 */
@Configuration
class SessionMemoryConfig {

    @Bean
    SessionService sessionService() {
        return DefaultSessionService.builder()
                .sessionRepository(InMemorySessionRepository.builder().build())
                .build();
    }
}
