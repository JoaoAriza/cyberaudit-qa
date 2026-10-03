package com.qa;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class TestConfig {

    private static final Properties properties = new Properties();

    static {
        try (InputStream input = TestConfig.class
                .getClassLoader()
                .getResourceAsStream("config.properties")) {

            if (input == null) {
                throw new RuntimeException("config.properties não encontrado");
            }

            properties.load(input);

        } catch (IOException e) {
            throw new RuntimeException("Erro ao carregar config.properties", e);
        }
    }

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = get("base.uri");
    }

    public static String get(String key) {
        return properties.getProperty(key);
    }
}
