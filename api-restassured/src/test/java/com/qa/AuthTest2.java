package com.qa;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

public class AuthTest extends TestConfig {
    private static final String MENSAGEM_ERRO_ESPERADA = "Autenticação necessária.";

    @Test
    @DisplayName("AUTH-02 - Deve retornar 401 e mensagem genérica quando a senha estiver errada")
    public void deveRetornarErroComSenhaErrada() {
        Map<String, String> payload = new HashMap<>();
        payload.put("email", TestConfig.get("emailPro"));
        payload.put("password", "SenhaIncorreta123");

        given()
                .log().uri()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(401)
                .body("error", equalTo(MENSAGEM_ERRO_ESPERADA));
    }

    @Test
    @DisplayName("AUTH-02 - Deve retornar 401 e a mesma mensagem genérica quando o usuário não existir")
    public void deveRetornarErroComUsuarioInexistente() {
        Map<String, String> payload = new HashMap<>();
        payload.put("email", "usuario.nao.existe@cyberaudit.test");
        payload.put("password", TestConfig.get("password"));

        given()
                .log().uri()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(401)
                .body("error", equalTo(MENSAGEM_ERRO_ESPERADA));
    }

    @Test
    @DisplayName("AUTH-01 - Deve retornar 200 - Login válido")
    public void deveRetornarLoginValido(){
        Map<String, String> payload = new HashMap<>();
        payload.put("email", TestConfig.get("emailPro"));
        payload.put("password", TestConfig.get("password"));

        given()
                .log().uri()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .body("requires2fa", equalTo(true));
    }
}