package com.thorben.janssen.piratestoreai.chat;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Called by pirate-store's {@code SupportAssisstantService} -- the only
 * client this endpoint is meant for; there is no authentication in this demo
 * (see project guardrails), so it should not be exposed beyond a trusted
 * network in a real deployment.
 */
@RestController
public class SupportChatController {

    private final SupportChatService supportChatService;

    public SupportChatController(SupportChatService supportChatService) {
        this.supportChatService = supportChatService;
    }

    @PostMapping(path = "/chat", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<ChatEvent> chat(@Valid @RequestBody ChatRequest request) {
        return supportChatService.respond(request);
    }

    @PostMapping(path = "/chat/decision", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<ChatEvent> decide(@Valid @RequestBody DecisionRequest request) {
        return supportChatService.decide(request);
    }
}
