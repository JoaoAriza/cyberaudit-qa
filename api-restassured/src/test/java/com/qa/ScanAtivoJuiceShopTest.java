package com.qa;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

public class ScanAtivoJuiceShopTest extends TestConfig {

    private String tokenQaEnterprise() {
        byte[] chave = get("jwt.secret")
                .getBytes(StandardCharsets.UTF_8);

        return Jwts.builder()
                .subject(get("emailEnterprise"))
                .claim("userId", get("userIdEnterprise"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .id(UUID.randomUUID().toString())
                .signWith(Keys.hmacShaKeyFor(chave))
                .compact();
    }

    @Test
    @DisplayName("SCAN-03: Modo ativo deve detectar vazamento de erro de banco (SQLi) no Juice Shop")
    void deveDetectarSqlInjectionNoJuiceShop() {
        String token = tokenQaEnterprise();
        String targetUrl = "http://juice-shop:3000/rest/products/search?q=apple";

        given()
                .header("Authorization", "Bearer " + token)
                .header("Accept-Language", "pt-BR")
                .queryParam("url", targetUrl)
                .queryParam("active", true)
                .when()
                .get("/scan")
                .then()
                .statusCode(200)
                .body("url", equalTo(targetUrl))
                .body("dbErrorLeakageSuspected", equalTo(true))
                .body("score.issues.find {it.id == 'DB_ERROR_LEAKAGE_SUSPECTED' }.severity", equalTo("HIGH"));
    }

    @Test
    @DisplayName("SCAN-03: Modo ativo deve detectar XSS refletido no mock de busca")
    void deveDetectarReflectedXssNoMock() {
        String token = tokenQaEnterprise();
        String targetUrl = "http://eco-mock:8080/busca?q=apple";

        given()
                .header("Authorization", "Bearer " + token)
                .header("Accept-Language", "pt-BR")
                .queryParam("url", targetUrl)
                .queryParam("active", true)
                .when()
                .get("/scan")
                .then()
                .statusCode(200)
                .body("url", equalTo(targetUrl))
                .body("reflectedXssSuspected", equalTo(true))
                .body("score.issues.find {it.id == 'REFLECTED_XSS_SUSPECTED' }.severity", equalTo("HIGH"));
    }

    @Test
    @DisplayName("SCAN-03: Controle negativo deve provar que a sonda não acusa XSS quando o valor é escapado")
    void deveValidarControleNegativoXssEscapado() {
        String token = tokenQaEnterprise();
        String targetUrl = "http://eco-escapado-mock:8080/busca?q=apple";

        given()
                .header("Authorization", "Bearer " + token)
                .header("Accept-Language", "pt-BR")
                .queryParam("url", targetUrl)
                .queryParam("active", true)
                .when()
                .get("/scan")
                .then()
                .statusCode(200)
                .body("url", equalTo(targetUrl))
                .body("xssProbePerformed", equalTo(true))
                .body("reflectedXssSuspected", equalTo(false))
                .body("score.issues.id", not(hasItem("REFLECTED_XSS_SUSPECTED")));
    }
}
