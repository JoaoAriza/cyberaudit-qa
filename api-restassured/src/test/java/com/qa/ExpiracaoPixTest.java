package com.qa;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class ExpiracaoPixTest extends TestConfig {

    private String tokenQaPay10() {
        byte[] chave = get("jwt.secret").getBytes(StandardCharsets.UTF_8);

        return Jwts.builder()
                .subject(get("emailPay10"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis()
                + 3600000))
                .id(UUID.randomUUID().toString())
                .signWith(Keys.hmacShaKeyFor(chave))
                .compact();
    }

    @BeforeEach
    void setUp() {
        prepararPixVencida(get("emailPay10"));
    }

    @Test
    @DisplayName("PAY-10: Job de expiração de Pix deve expirar a assinatura e rebaixar a conta para FREE")
    void testExpirePixJob() {
        String token = tokenQaPay10();

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/billing/subscription")
                .then()
                .statusCode(200)
                .body("status", equalTo("AUTHORIZED"));


        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .post("/admin/test/expire-pix")
                .then()
                .statusCode(200)
                .body( equalTo("expired"));


        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/billing/subscription")
                .then()
                .statusCode(200)
                .body("status", equalTo("EXPIRED"));


        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/auth/me")
                .then()
                .statusCode(200)
                .body("account.plan", equalTo("FREE"));
    }
}
