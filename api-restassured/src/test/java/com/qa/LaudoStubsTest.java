package com.qa;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class LaudoStubsTest extends TestConfig {

    private String tokenQaPro() {
        byte[] chave = get("jwt.secret")
                .getBytes(StandardCharsets.UTF_8);

        return Jwts.builder()
                .subject(get("emailPro"))
                .claim("userId", get("userIdPro"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis()
                + 3600000))
                .id(UUID.randomUUID().toString())
                .signWith(Keys.hmacShaKeyFor(chave))
                .compact();
    }

    @Test
    @DisplayName("SCAN-09: O scan deve avaliar as flags de segurança dos cookies pelo comportamento/tipo e não pelo nome")
    void deveValidarFlagsDeSegurancaDeCookiesCorretamente() {
        String token = tokenQaPro();
        String targetUrl = "http://cookies-mock:8080";

        given()
                .header("Authorization", "Bearer " + token)
                .header("Accept-Language", "pt-BR")
                .queryParam("url", targetUrl)
                .queryParam("active", false)
                .when()
                .get("/scan")
                .then()
                .statusCode(200)
                .body("url", equalTo(targetUrl))

                // 1. Validação estrutural: garante que a lista de cookies analisados não está vazia
                .body("cookieIssues.size()", equalTo(4))

                // 2. PHPSESSID: Sessão PHP sem HttpOnly e sem SameSite (RISK HIGH)
                .body("cookieIssues.find { it.name == 'PHPSESSID' }.risk", equalTo("HIGH"))
                .body("cookieIssues.find { it.name == 'PHPSESSID' }.problems", hasItems("COOKIE_SEM_HTTPONLY", "COOKIE_SEM_SAMESITE"))

                // 3. connect.sid: Sessão do Express sem Secure (RISK HIGH)
                .body("cookieIssues.find { it.name == 'connect.sid' }.risk", equalTo("HIGH"))
                .body("cookieIssues.find { it.name == 'connect.sid' }.problems", hasItem("COOKIE_SEM_SECURE"))

                // 4. secure_pref: O nome contém 'secure', mas o flag real é false (RISK LOW)
                .body("cookieIssues.find { it.name == 'secure_pref' }.risk", equalTo("LOW"))
                .body("cookieIssues.find { it.name == 'secure_pref' }.secure", equalTo(false))

                // 5. visitante: Não é cookie de sessão (RISK LOW)
                .body("cookieIssues.find { it.name == 'visitante' }.risk", equalTo("LOW"))

                // 6. XSRF-TOKEN: Token CSRF não exige HttpOnly, logo não deve gerar apontamentos em cookieIssues
                .body("cookieIssues.name", not(hasItem("XSRF-TOKEN")))

                // 7. Validações globais do laudo (score e notes)
                .body("score.issues.find {it.id == 'INSECURE_COOKIES' }.severity", equalTo("HIGH"))
                .body("score.notes", hasItem("Cookies com flags de segurança ausentes: -10"));
    }
}
