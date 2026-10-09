package com.qa;

import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

public class PlanoPdfTest extends TestConfig {

    private static String tokenPara(String email, String userId) {
        byte[] secret = get("jwt.secret")
                .getBytes(StandardCharsets.UTF_8);

        SecretKeySpec key = new SecretKeySpec(secret, "HmacSHA256");

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
    void deveBloquearPdfNoPlanoFree() {
        String tokenFree = tokenPara(
                get("emailFree").trim(),
                get("userIdFree").trim()
        );

        given()
                .header("Authorization", "Bearer " + tokenFree)
                .queryParam("url", "http://wiremock:8080?health")
                .queryParam("active", false)
                .when()
                .get("/scan/report/pdf")
                .then()
                .statusCode(402)
                .body("error", equalTo("402 PAYMENT_REQUIRED"))
                .body("message", containsString("Exportação de PDF requer plano PRO"));
    }

    @Test
    void deveExigirAutenticacaoNoPdf() {
        Response response = given()
                .queryParam("url", "http://wiremock:8080/health")
                .queryParam("active", false)
                .when()
                .get("/scan/report/pdf")
                .then()
                .statusCode(401)
                .body("error", equalTo("Autenticação necessária."))
                .extract().response();

        assertEquals(401, response.statusCode());
    }
}
