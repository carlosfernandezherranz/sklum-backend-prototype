package com.sklum.mocks;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * One consolidated mock service standing in for Commerce Core, Payment, and two Carrier
 * partners (Fig. 2 / Fig. 4 of the system design). Route groups, not separate processes —
 * see CLAUDE.md "Mock topology" decision. Holds its own in-memory state, deliberately
 * separate from the backend's Postgres: these are two different systems in real life.
 */
@SpringBootApplication
public class MockBackendsApplication {
    public static void main(String[] args) {
        SpringApplication.run(MockBackendsApplication.class, args);
    }
}
