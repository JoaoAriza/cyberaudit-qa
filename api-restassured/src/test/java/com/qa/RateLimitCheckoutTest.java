package com.qa;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RateLimitCheckoutTest extends TestConfig {

    private String tokenQaPay09() {
        byte[] chave = get("jwt.secret").getBytes(StandardCharsets.UTF_8);

        return Jwts.builder()
                .subject(get("emailPay09"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis()
                +3600000))
                .id(UUID.randomUUID().toString())
                .signWith(Keys.hmacShaKeyFor(chave))
                .compact();
    }

    @Test
    @DisplayName("PAY-09: Deve retornar 429 Too Many Requests ao exceder o limite de 5 tentativas de checkout por minuto")
    void testRateLimitCheckoutCartao() {
        String token = tokenQaPay09();
        String payload = "{\"plan\":\"PRO\",\"cardTokenId\":\"card-recusado\"}";

        boolean found429 = false;
        Response response = null;

        for (int i = 0; i < 8; i++) {
            response = given()
                    .header("Authorization", "Bearer " + token)
                    .contentType(ContentType.JSON)
                    .body(payload)
                    .when()
                    .post("/billing/checkout/card")
                    .andReturn();

                    if (response.getStatusCode() == 429) {
                        found429 = true;
                        break;
                    }
        }

        assertTrue(found429, "O limite de taxa (rate limit) de 429 não foi atingido dentro das 8 tentativas");

        response.then()
                .statusCode(429)
                .body("error", equalTo("429 TOO_MANY_REQUESTS"))
                .body("message", containsString("Muitas tentativas de checkout"));
    }
}