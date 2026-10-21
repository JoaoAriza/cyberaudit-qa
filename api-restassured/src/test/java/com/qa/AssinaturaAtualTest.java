package com.qa;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class AssinaturaAtualTest extends TestConfig {

    private String tokenQaPay15() {
        byte[] chave = get("jwt.secret").getBytes(StandardCharsets.UTF_8);

        return Jwts.builder()
                .subject(get("emailPay15"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis()
                + 3600000))
                .id(UUID.randomUUID().toString())
                .signWith(Keys.hmacShaKeyFor(chave))
                .compact();
    }

    @Test
    @DisplayName("PAY-15: Deve retornar a assinatura AUTHORIZED ignorando a PENDING mais recente")
    void testAssinaturaAtualPrefereAuthorized() {
        String token = tokenQaPay15();

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/billing/subscription")
                .then()
                .statusCode(200)
                .body("status", equalTo("AUTHORIZED"))
                .body("plan", equalTo("PRO"));
    }
}
