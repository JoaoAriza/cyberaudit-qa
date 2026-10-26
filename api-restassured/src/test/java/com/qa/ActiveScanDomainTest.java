package com.qa;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.equalTo;

public class ActiveScanDomainTest extends TestConfig {

    private static final String ERRO_DOMINIO_NAO_VERIFICADO =
            "ACCOUNT_DOMAIN_NOT_VERIFIED";

    private static final String ERRO_SEM_AUTENTICACAO =
            "401 UNAUTHORIZED";

    private SecretKey getSigningKey() {
        String secret = TestConfig.get("jwt.secret");

        return Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String tokenValido() {
        String email = TestConfig.get("emailPro");

        Date agora = new Date();

        Date expiracao = new Date(
                System.currentTimeMillis() + 60_000
        );

        return Jwts.builder()
                .subject(email)
                .claim("role", "OWNER")
                .claim("name", "QA Pro")
                .claim("twoFactorPending", false)
                .issuedAt(agora)
                .expiration(expiracao)
                .signWith(getSigningKey())
                .compact();
    }

    @Test
    @DisplayName("DOM-01 - Deve recusar scan ativo em domínio não verificado")
    public void deveRecusarScanAtivoEmDominioNaoVerificado() {
        String token = tokenValido();

        given()
                .log().uri()
                .queryParam(
                        "url",
                        TestConfig.get("scanHostNaoVerificado")
                )
                .queryParam("active", true)
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/scan")
                .then()
                .statusCode(403)
                .body("error", equalTo(ERRO_DOMINIO_NAO_VERIFICADO))
                .body("host", equalTo("juice-shop:3000"));
    }

    @Test
    @DisplayName("DOM-01 - Deve exigir autenticação para scan ativo")
    public void deveExigirAutenticacaoNoScanAtivo() {

        given()
                .log().uri()
                .queryParam(
                        "url",
                        TestConfig.get("scanHostNaoVerificado")
                )
                .queryParam("active", true)
                .when()
                .get("/scan")
                .then()
                .statusCode(401)
                .body("error", equalTo(ERRO_SEM_AUTENTICACAO));
    }
}
