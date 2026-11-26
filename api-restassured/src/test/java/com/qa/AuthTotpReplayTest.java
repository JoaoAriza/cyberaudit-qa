package com.qa;

import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

public class AuthTotpReplayTest extends TestConfig {

    @Test
    @DisplayName("AUTH-04: Código TOTP deve ser de uso único, bloqueando reutilização (replay attack) e códigos inválidos")
    void deveValidarSegurancaETrechoDeUsoUnicoDoTotp() throws Exception {
        String email = get("emailFree");
        String password = get("password");
        String secret  = get("totpSecret");

        // -------------------------------------------------------------
        // PASSO 1: Login inicial para obter o token pré-auth (requires2fa: true)
        // -------------------------------------------------------------

        JsonPath loginResponse = given()
                .contentType("application/json")
                .body(Map.of("email", email, "password", password))
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .body("requires2fa", equalTo(true))
                .body("token", notNullValue())
                .extract()
                .jsonPath();

        String preAuthToken = loginResponse.getString("token");

        given()
                .header("Authorization", "Bearer " + preAuthToken)
                .when()
                .get("/auth/me")
                .then()
                .statusCode(403);

        // -------------------------------------------------------------
        // PASSO 2: Tentar verificar com código inválido ("000000") -> 422
        // -------------------------------------------------------------

        given()
                .header("Authorization", "Bearer " + preAuthToken)
                .contentType("application/json")
                .body(Map.of("code", "000000", "method", "TOTP"))
                .when()
                .post("/auth/2fa/verify")
                .then()
                .statusCode(422)
                .body("message", equalTo("Código TOTP inválido."));

        // -------------------------------------------------------------
        // PASSO 3: Controle Positivo - Gerar e usar um código TOTP válido -> 200 (Sessão aberta)
        // -------------------------------------------------------------

        long currentWindow = System.currentTimeMillis() / 1000L / 30L;
        String validTotpCode = new DefaultCodeGenerator(HashingAlgorithm.SHA1).generate(secret, currentWindow);

        JsonPath verifyResponse = given()
                .header("Authorization", "Bearer " + preAuthToken)
                .contentType("application/json")
                .body(Map.of("code", validTotpCode, "method", "TOTP"))
                .when()
                .post("/auth/2fa/verify")
                .then()
                .statusCode(200)
                .body("requires2fa", equalTo(false))
                .body("token", notNullValue())
                .extract()
                .jsonPath();

        String sessionToken = verifyResponse.getString("token");

        given()
                .header("Authorization", "Bearer " + sessionToken)
                .when()
                .get("/auth/me")
                .then()
                .statusCode(200)
                .body("email", equalTo(email));

        // -------------------------------------------------------------
        // PASSO 4: Ataque de Replay - Fazer novo login (novo pré-auth) e tentar reutilizar o MESMO código -> 422
        // -------------------------------------------------------------

        String newPreAuthToken = given()
                .contentType("application/json")
                .body(Map.of("email", email, "password", password))
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .body("requires2fa", equalTo(true))
                .extract()
                .path("token");

        given()
                .header("Authorization", "Bearer " + newPreAuthToken)
                .contentType("application/json")
                .body(Map.of("code", validTotpCode, "method", "TOTP"))
                .when()
                .post("/auth/2fa/verify")
                .then()
                .statusCode(422)
                .body("message", equalTo("Código TOTP já utilizado. Aguarde o próximo código no aplicativo."));

        // -------------------------------------------------------------
        // PASSO 5: Tentar enviar a requisição sem o campo "code" -> 422
        // -------------------------------------------------------------

        given()
                .header("Authorization", "Bearer " + newPreAuthToken)
                .contentType("application/json")
                .body(Map.of("method", "TOTP"))
                .when()
                .post("/auth/2fa/verify")
                .then()
                .statusCode(422)
                .body("message", equalTo("Código TOTP inválido."));
    }
}
