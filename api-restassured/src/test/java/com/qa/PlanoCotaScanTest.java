package com.qa;

import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

public class PlanoCotaScanTest extends TestConfig {

    private static String tokenPara(String email, String userId) {
        byte [] secret = get("jwt.secret")
                .getBytes(StandardCharsets.UTF_8);

        SecretKey key = new SecretKeySpec(secret, "HmacSHA256");

        return Jwts.builder()
                .setSubject(email)
                .claim("email", email)
                .claim("userId", userId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(
                        System.currentTimeMillis()
                        + 60 * 60 * 1000
                ))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    void deveBloquearScanAoAtingirCotaDiariaFree() {
        String tokenFree = tokenPara(
                get("emailFree").trim(),
                get("userIdFree").trim()
        );

        Response resposta402 = null;

        int limiteTentativas = 15;

        for (int i = 1; i <= limiteTentativas; i++) {

            Response resposta = given()
                    .header("Authorization", "Bearer " + tokenFree)
                    .queryParam("url", "http://127.0.0.1")
                    .queryParam("active", false)
                    .when()
                    .get("/scan")
                    .then()
                    .extract()
                    .response();

            if (resposta.statusCode() == 402) {
                resposta402 = resposta;
                break;
            }

            assertEquals(
                    403,
                    resposta.statusCode(),
                    "Tentativa " + i
                    + " era esperado 403 antes de atingir a cota "
                    + "ou 402 ao atingir o limite. Corpo: "
                    + resposta.asString()
            );
        }

        assertNotNull(
                resposta402,
                "Nenhuma resposta 402 foi recebida após"
                + limiteTentativas + "tentativas"
        );

        assertEquals(402, resposta402.statusCode());

        resposta402.then()
                .body("error", equalTo("402 PAYMENT_REQUIRED"))
                .body(
                        "message",
                        containsString("Limite diário de 10 scans atingido")
                );
    }
}
