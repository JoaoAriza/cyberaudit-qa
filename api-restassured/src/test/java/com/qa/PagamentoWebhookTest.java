package com.qa;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class PagamentoWebhookTest extends TestConfig {

    private static final String WEBHOOK_PAYLOAD = "{\"type\":\"payment\", \"data\":{\"id\":\"qualquer\"}}";

    @Test
    @DisplayName("PAY-08: Deve retornar 401 ao enviar webhook com assinatura inválida")
    void testWebhookAssinaturaInvalida() {
        given()
                .contentType(ContentType.JSON)
                .header("x-signature", "ts=123, v1=assinaturafalsa")
                .body(WEBHOOK_PAYLOAD)
                .when()
                .post("/billing/webhook")
                .then()
                .statusCode(401)
                .body(equalTo("invalid signature"));
    }


}
