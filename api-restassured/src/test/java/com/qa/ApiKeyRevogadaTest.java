package com.qa;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class ApiKeyRevogadaTest extends TestConfig{

    private String createdApiKeyId;

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

    @AfterEach
    void cleanup() {
        if (createdApiKeyId != null) {
            try {
                given()
                    .header("Authorization", "Bearer " + tokenQaPro())
                    .when()
                    .delete("/api-keys/" + createdApiKeyId);
            } catch (Exception ignored) {

            }
        }
    }

    @Test
    @DisplayName("AUTH-05: API key revogada perde o acesso imediatamente (valida controle positivo e ciclo de vida)")
    void deveRevogarApiKeysEBloquearAcessoSubsequente() {
        String token = tokenQaPro();
        String uniqueName = "key-" + UUID.randomUUID();

        Map<?, ?> createResponse = given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("name", uniqueName))
                .when()
                .post("/api-keys")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("plainKey", startsWith("ca_"))
                .extract()
                .as(Map.class);

        createdApiKeyId = (String) createResponse.get("id");
        String plainKey = (String) createResponse.get("plainKey");

        given()
                .header("X-Api-Key", plainKey)
                .when()
                .get("/api-keys")
                .then()
                .statusCode(200)
                .body("$", instanceOf(java.util.List.class));


        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete("/api-keys/" + createdApiKeyId)
                .then()
                .statusCode(204);

        String deleteId = createdApiKeyId;
        createdApiKeyId = null;

        given()
                .header("X-Api-Key", plainKey)
                .when()
                .get("/api-keys")
                .then()
                .statusCode(401)
                .body("error", equalTo("Autenticação necessária."));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete("/api-keys/{id}", deleteId)
                .then()
                .statusCode(409)
                .body("message", equalTo("API key já revogada."));
    }
}