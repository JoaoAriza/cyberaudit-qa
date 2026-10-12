package com.qa;

import io.restassured.http.ContentType;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

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

    @ParameterizedTest(name = "SSRF-03: deve bloquear formato alternativo {0}")
    @ValueSource(strings = {
            "http://2130706433",
            "https://127.1"
    })
    void deveBloquearFormatosAlternativosLoopback(String alvo) {
        String token = tokenQaPro();

        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("url", alvo)
                .queryParam("active", false)
                .when()
                .get("/scan")
                .then()
                .statusCode(403)
                .body("error", equalTo("DOMAIN_BLOCKED"));
    }

    @Test
    @Disabled("BUG-02: guard nao normaliza 0x7f000001; reativar quando corrigido -> deve ser 403")
    void deveBloquarFormatoHexadecimalLoopback() {
        String token = tokenQaPro();

        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("url", "http://0x7f000001")
                .queryParam("active", false)
                .when()
                .get("/scan")
                .then()
                .statusCode(403)
                .body("error", equalTo("DOMAIN_BLOCKED"));
    }

    @ParameterizedTest(name = "SSRF-04: deve bloquear hostname interno {0}")
    @ValueSource (strings = {
            "http://interno-privado.cyberaudit.test",
            "http://interno-loopback.cyberaudit.test",
            "http://interno-metadata.cyberaudit.test"
    })
    void deveBloquearHostnameQueResolvemParaIpsInternos(String alvo) {
        String token = tokenQaPro();

        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("url", alvo)
                .queryParam("active", false)
                .when()
                .get("/scan")
                .then()
                .statusCode(403)
                .body("error", equalTo("DOMAIN_BLOCKED"));
    }

    
}