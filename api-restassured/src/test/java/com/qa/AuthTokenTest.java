package com.qa;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.equalTo;

public class AuthTokenTest extends TestConfig {

    private static final String MENSAGEM_ERRO_ESPERADA =
            "Autenticação necessária.";

    private SecretKey getSigningKey() {
        String secret = TestConfig.get("jwt.secret");

        return Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String gerarToken(Date expiracao) {
        String email = TestConfig.get("emailPro");
        String userId = TestConfig.get("userIdPro");

        Date agora = new Date();

        return Jwts.builder()
                .subject(email)
                .claim("role", "OWNER")
                .claim("userId", userId)
                .claim("name", "QA Pro")
                .claim("twoFactorPending", false)
                .issuedAt(agora)
                .expiration(expiracao)
                .signWith(getSigningKey())
                .compact();
    }

    private String tokenValido() {
        Date expiracao = new Date(
                System.currentTimeMillis() + 60_000
        );

        return gerarToken(expiracao);
    }

    private String tokenExpirado() {
        Date expiracao = new Date(
                System.currentTimeMillis() -60_000
        );

        return gerarToken(expiracao);
    }

    private String tokenAssinaturaAdulterada() {
        String token = tokenValido();

        String[] partes = token.split("\\.");

        String assinatura = partes[2];

        String assinaturaAdulterada =
                assinatura.substring(0, assinatura.length() -1)
                + (assinatura.endsWith("A") ? "B" : "A");

        return partes[0] + "."
                + partes[1] + "."
                + assinaturaAdulterada;
    }

    private String tokenAlgNone() {
        String header = "{\"alg\":\"none\",\"typ\":\"JWT\"}";

        String payload = "{"
                + "\"sub\":\"" + TestConfig.get("emailPro") + "\","
                + "\"role\":\"OWNER\","
                + "\"userId\":\"" + TestConfig.get("userIdPro") + "\","
                + "\"name\":\"QA Pro\","
                + "\"twoFactorPending\":false,"
                + "\"iat\":" + (System.currentTimeMillis() / 1000) + ","
                + "\"exp\":" + ((System.currentTimeMillis() / 1000) + 60)
                + "}";

        Base64.Encoder encoder =
                Base64.getUrlEncoder().withoutPadding();

        String encodeHeader =
                encoder.encodeToString(
                        header.getBytes(StandardCharsets.UTF_8)
                );

        String encodedPayload =
                encoder.encodeToString(
                        payload.getBytes(StandardCharsets.UTF_8)
                );

        return encodeHeader + "." + encodedPayload + ".";
    }

    private String tokenOutroSegredo() {
        String outroSegredo =
                "outro-segredo-de-teste-com-mais-de-32-bytes";

        SecretKey outraChave = Keys.hmacShaKeyFor(
                outroSegredo.getBytes(StandardCharsets.UTF_8)
        );

        Date agora = new Date();

        return Jwts.builder()
                .subject(TestConfig.get("emailPro"))
                .claim("role", "OWNER")
                .claim("userId", TestConfig.get("userIdPro"))
                .claim("name", "QA Pro")
                .claim("twoFactorPending", false)
                .issuedAt(agora)
                .expiration(new Date(
                        System.currentTimeMillis() + 60_000
                ))
                .signWith(outraChave)
                .compact();
    }

    @Test
    @DisplayName("AUTH-03 - Deve aceitar token JWT válido")
    public void deveAceitarTokenValido() {
        String token = tokenValido();

        given()
                .log().uri()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/history/recent")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("AUTH-03 - Deve recusar token JWT expirado")
    public void deveRecusarTokenExpirado() {
        String token = tokenExpirado();

        given()
                .log().uri()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/history/recent")
                .then()
                .statusCode(401)
                .body("error", equalTo(MENSAGEM_ERRO_ESPERADA));
    }

    @Test
    @DisplayName("AUTH-03 - Deve recusar assinatura JWT adulterada")
    public void deveRecusarAssinaturaAdulterada(){

    String token = tokenAssinaturaAdulterada();

    given()
            .log().uri()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/history/recent")
            .then()
            .statusCode(401)
            .body("error", equalTo(MENSAGEM_ERRO_ESPERADA));
    }

    @Test
    @DisplayName("AUTH-03 - Deve recusar token com algoritimo none")
    public void deveRecusarAlgNone() {
        String token = tokenAlgNone();

        given()
                .log().uri()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/history/recent")
                .then()
                .statusCode(401)
                .body("error", equalTo(MENSAGEM_ERRO_ESPERADA));
    }

    @Test
    @DisplayName("AUTH-03 - Deve recusar token assinado com outro segredo")
    public void deveRecusarOutroSegredo() {
        String token = tokenOutroSegredo();

        given()
                .log().uri()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/history/recent")
                .then()
                .statusCode(401)
                .body("error", equalTo(MENSAGEM_ERRO_ESPERADA));
    }

    @Test
    @DisplayName("AUTH-03 - Deve recusar requisiçãp sem token")
    public void deveRecusarSemToken() {

        given()
                .log().uri()
                .when()
                .get("/history/recent")
                .then()
                .statusCode(401)
                .body("error", equalTo(MENSAGEM_ERRO_ESPERADA));
    }
}
