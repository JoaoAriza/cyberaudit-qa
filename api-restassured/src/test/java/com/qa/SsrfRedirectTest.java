package com.qa;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

public class SsrfRedirectTest extends TestConfig {

    private String tokenPara (String email, String userId) {
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

    @Test
    void deveBloquearRedirectParaIpInterno() {
        String token = tokenQaPro();

        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("url", "http://redirect-mock:8080/redirect-interno")
                .queryParam("active", false)
                .when()
                .get("/scan")
                .then()
                .statusCode(200)
                .body("finalUrl",equalTo("http://redirect-mock:8080/redirect-interno"))
                .body("headers.error", containsString("Scan bloqueado"))
                .body("headers.error", containsString("169.254.169.254"));
    }
}