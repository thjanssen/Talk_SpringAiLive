package com.thorben.janssen.piratestore;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.theme.Theme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Pirate Store demo application. See the project README
 * for the supported startup options and the Spring AI extension point.
 */
@SpringBootApplication
@Push
@Theme("pirate-store")
public class PirateStoreApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(PirateStoreApplication.class, args);
    }
}
