package com.thorben.janssen.piratestore;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** Smoke test: the full Spring context, including all Vaadin views, must start without errors. */
@SpringBootTest
class PirateStoreApplicationTests {

    @Test
    void contextLoads() {
        // Intentionally empty: a failing context load already fails this test.
    }
}
