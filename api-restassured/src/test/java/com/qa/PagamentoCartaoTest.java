package com.qa;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class PagamentoCartaoTest extends TestConfig {

    private String tokenQaPayCard() {
        byte [] chave = get("jwt.secret")
                .getBytes(StandardCharsets.UTF_8);

        return Jwts.builder()
                .subject(get("emailPayCard"))
                .claim("userId", get("userIdPayCard"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis()
                + 3600000))
                .id(UUID.randomUUID().toString())
                .signWith(Keys.hmacShaKeyFor(chave))
                .compact();
    }

    @Test
    void deveAutorizarPagamentoComCartao() {
        String token = tokenQaPayCard();

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"plan\":\"PRO\",\"cardTokenId\":\"card-ok\"}")
                .when()
                .post("/billing/checkout/card")
                .then()
                .statusCode(200)
                .body("plan", equalTo("PRO"))
                .body("status", equalTo("AUTHORIZED"));
    }
}
