package com.qa;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class SsrfVerifyCheckTest extends TestConfig{

    private String tokenQaPro() {
        byte[] chave = get("jwt.secret").getBytes(StandardCharsets.UTF_8);

        return Jwts.builder()
                .subject(get("emailPro"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis()
                + 3600000))
                .id(UUID.randomUUID().toString())
                .signWith(Keys.hmacShaKeyFor(chave))
                .compact();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "127.0.0.1:8081",
            "localhost:8081",
            "169.254.169.254:80",
            "[::1]:443",
            "127.0.0.1/",
            "127.0.0.1#",
            "127.0.0.1 "
    })
    @DisplayName("SSRF-07: Deve bloquear tentativas de varredura em hosts internos ou malformados com 403 DOMAIN_BLOCKED")
    void testBloqueioSsrfHostsInternos(String host) {
        String token = tokenQaPro();
        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("host", host)
                .when()
                .get("/scan/verify-check")
                .then()
                .statusCode(403)
                .body("error", equalTo("DOMAIN_BLOCKED"));
    }

    @Test
    @DisplayName("SSRF-07: Controle positivo deve permitir porta em host permitido retornando 200 e verified = false")
    void testControleHostPermitido() {
        String token = tokenQaPro();
        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("host", "juice-shop:3000")
                .when()
                .get("/scan/verify-check")
                .then()
                .statusCode(200)
                .body("verified", equalTo(false));
    }
}
