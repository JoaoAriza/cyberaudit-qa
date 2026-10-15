package com.qa;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;

import static io.restassured.RestAssured.given;

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
    @Disabled("BUG-03/SCAN-03: scanner nao conecta em HTTP porta nao-padrao; reativar apos fix")
    void deveDetectarXssESqliNoJuiceShop() {
        String token = tokenQaEnterprise();

        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("url", "http://juice-shop:3000/rest/products/search?q=apple")
                .queryParam("active", true)
                .when()
                .get("/scan")
                .then()
                .statusCode(200)
                .body("reflectedXssSuspected", equalTo(true))
                .body("dbErrorLeakageSuspected", equalTo(true));
    }
}
