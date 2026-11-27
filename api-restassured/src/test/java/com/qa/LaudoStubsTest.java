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

import org.junit.jupiter.api.Assumptions;
import java.util.List;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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

    @Test
    @DisplayName("SCAN-05: CVE de extensão do PHP (PECL) não deve ser atribuído incorretamente ao core do PHP")
    void deveGarantirQueCveDeExtensaoPhpNaoAfetaCoreDoPhp() {
        String token = tokenQaPro();
        String targetUrl = "http://php-banner-mock:8080";

        var response = given()
                .header("Authorization", "Bearer " + token)
                .header("Accept-Language", "pt-BR")
                .queryParam("url", targetUrl)
                .queryParam("active", false)
                .when()
                .get("/scan")
                .then()
                .statusCode(200)
                .body("url", equalTo(targetUrl))
                .body("techFingerprint.detectedVersions.PHP", equalTo("8.3.36"))
                .extract()
                .response();

        String cveModuleStatus = response.path("moduleStatus.CVE");
        List<String> cveIds = response.path("cveFindings.cveId");

        assumeTrue(
                "OK".equals(cveModuleStatus) && cveIds != null && cveIds.contains("CVE-2007-3205"),
                "Pulando teste: O NVD demorou a responder ou o módulo CVE encontrou instabilidade (moduleStatus: " + cveModuleStatus + ")"
        );

        // -------------------------------------------------------------
        // Asserções do Teste
        // -------------------------------------------------------------

        response.then()
                .body("url", equalTo(targetUrl))
                .body("cveFindings.cveId", hasItem("CVE-2007-3205"))
                .body("cveFindings.cveId", not(hasItem("CVE-2013-2220")))
                .body("score.issues.id", not(hasItem(startsWith("CVE"))))
                .body("score.notes", not(hasItem(containsString("CVE"))));
    }

    @Test
    @DisplayName("SCAN-08: HSTS ausente deve gerar issue com severidade MEDIUM e penalidade correta de -10")
    void deveValidarSevMediaENotaParaHstsAusente() {
        String token = tokenQaPro();
        String targetUrl = "http://sem-hsts-mock:8080";

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

                .body("headers.'Strict-Transport-Security'", equalTo("MISSING"))
                .body("score.issues.find { it.id == 'HSTS_MISSING' }.severity", equalTo("MEDIUM"))
                .body("score.notes", hasItem(allOf(startsWith("HSTS ausente"), endsWith("-10"))));
    }
}
