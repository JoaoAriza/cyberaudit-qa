package com.qa;

import io.restassured.http.ContentType;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class SsrfGuardTest extends TestConfig {

    private String tokenPara(String email, String userId) {
        byte[] chave = get("jwt.secret").getBytes(StandardCharsets.UTF_8);

        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis()
                + 3600000))
                .id(UUID.randomUUID().toString())
                .signWith(Keys.hmacShaKeyFor(chave))
                .compact();
    }

    private String tokenQaPro() {
        return tokenPara(get("emailPro"), get("userIdPro"));
    }

    @ParameterizedTest(name = "SSRF-01: deve bloquear alvo local {0}")
    @ValueSource(strings = {
            "http://127.0.0.1",
            "http://localhost",
            "http://[::1]"
    })
    void deveBloquearAlvosLoopback(String alvo) {
        String token = tokenQaPro();

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .queryParam("url", alvo)
                .queryParam("active", false)
                .when()
                .get("/scan")
                .then()
                .statusCode(403)
                .body("error", equalTo("DOMAIN_BLOCKED"));
    }

    @ParameterizedTest(name = "SSRF-02: deve bloquear alvo privado {0}")
    @ValueSource(strings = {
            "http://169.254.169.254",
            "http://10.0.0.1",
            "http://192.168.1.1"
    })
    void deveBloquearAlvosInternosEPrivados(String alvo) {
        String token = tokenQaPro();

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .queryParam("url", alvo)
                .queryParam("active", false)
                .when()
                .get("/scan")
                .then()
                .statusCode(403)
                .body("error", equalTo("DOMAIN_BLOCKED"));
    }
}