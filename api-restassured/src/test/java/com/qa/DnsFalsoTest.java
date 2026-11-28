package com.qa;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class DnsFalsoTest extends TestConfig {

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

    @ParameterizedTest(name = "SCAN-07: Domínio [{0}] deve retornar risco [{1}] e severidade [{2}]")
    @CsvSource({
            "quarantine.dns-mock.test, LOW, NONE, 0",
            "quarantine-pct50.dns-mock.test, MEDIUM, MEDIUM, -6",
            "reject-pct25.dns-mock.test, LOW, NONE, 0",
            "spf-mais-all.dns-mock.test, HIGH, HIGH, -12",
            "dmarc-none.dns-mock.test, MEDIUM, MEDIUM, -6"
    })
    @DisplayName("SCAN-07: Avaliação de risco de spoofing de e-mail (SPF/DMARC)")
    void deveValidarRiscoDeSpoofingEmailPorDominio(String subdomain, String expectedRisk, String expectedSeverity, int expectedPenalty) {
        String token = tokenQaPro();
        String targetUrl = "http://" + subdomain + ":8080";

        var request = given()
                .header("Authorization", "Bearer " + token)
                .header("Accept-Language", "pt-BR")
                .queryParam("url", targetUrl)
                .queryParam("active", false)
                .when()
                .get("/scan")
                .then()
                .statusCode(200)
                .body("url", equalTo(targetUrl))

                .body("dnsSecurityResult.lookupFailed", equalTo(false))

                .body("dnsSecurityResult.emailSpoofingRisk", equalTo(expectedRisk));

        if ("NONE".equals(expectedSeverity)) {
            request.body("score.issues.id", not(hasItem("DNS_EMAIL_SPOOFING")));
            request.body("score.notes", not(hasItem(containsString("DNS Security"))));
        } else {
            request.body("score.issues.find { it.id == 'DNS_EMAIL_SPOOFING' }.severity", equalTo(expectedSeverity));
            request.body("score.notes", hasItem(allOf(startsWith("DNS Security"), endsWith(": " + expectedPenalty))));        }
    }

    @ParameterizedTest(name = "SCAN-06: Domínio [{0}] deve retornar dkimHintFound=[{1}] e seletor=[{2}]")
    @CsvSource({
        "dkim-hostinger.dns-mock.test, true, hostingermail-a",
        "dkim-revogado.dns-mock.test, false, NOT_DETECTED",
        "dkim-curinga.dns-mock.test, false, NOT_DETECTED"
    })
    @DisplayName("SCAN-06: O scanner deve julgar DKIM apenas quando houver chave válida e reconhecer a Hostinger")
    void deveValidarDkimESeletorPorDominio(String subdomain, boolean expectedDkimFound, String expectedSelector) {
        String token = tokenQaPro();
        String targetUrl = "http://" + subdomain + ":8080";

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

                .body("dnsSecurityResult.lookupFailed", equalTo(false))
                .body("dnsSecurityResult.spfRecord", equalTo("v=spf1 include:_spf.mail.hostinger.com ~all"))
                .body("dnsSecurityResult.dkimHintFound", equalTo(expectedDkimFound))
                .body("dnsSecurityResult.dkimSelector", equalTo(expectedSelector));
    }
}
