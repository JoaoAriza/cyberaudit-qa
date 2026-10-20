package com.qa;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.containsString;

public class PagamentoCartaoTest extends TestConfig {

    // Idempotência: devolve a conta de upgrade (qa-pay-card) para FREE antes de cada
    // teste, para o re-run não achar a conta já PRO (409). Ver TestConfig.resetarContaParaFree.
    @BeforeEach
    void reset() {
        resetarContaParaFree(get("emailPayCard"));
    }

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

    private String tokenQaPayCardFree() {
        byte [] chave = get("jwt.secret")
                .getBytes(StandardCharsets.UTF_8);

        return Jwts.builder()
                .subject(get("emailFree"))
                .claim("userIdFree", get("userIdFreePayCard"))
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

    @Test
    void deveRetornarErroQuandoCartaoForRecusado() {
        String token = tokenQaPayCardFree();

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("{\"plan\":\"PRO\",\"cardTokenId\":\"card-recusado\"}")
                .when()
                .post("/billing/checkout/card")
                .then()
                .statusCode(502)
                .body("error", equalTo("502 BAD_GATEWAY"))
                .body("message", containsString("Mercado Pago retornou"));
    }
}