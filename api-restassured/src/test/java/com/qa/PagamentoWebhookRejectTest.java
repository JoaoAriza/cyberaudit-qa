package com.qa;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class PagamentoWebhookRejectTest extends TestConfig {

    private String tokenQaPay07() {
        byte [] chave = get("jwt.secret").getBytes(StandardCharsets.UTF_8);

        return Jwts.builder()
                .subject(get("emailPay07"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis()
                + 3600000))
                .id(UUID.randomUUID().toString())
                .signWith(Keys.hmacShaKeyFor(chave))
                .compact();
    }

    @Test
    @DisplayName("PAY-07: Webhook de pagamento rejeitado com assinatura válida deve ser no-op e manter a assinatura AUTHORIZED")
    void testWebhookPagamentoRejeitadoNaoAlteraSubscription() {
    String token = tokenQaPay07();
    String requestId = "req-pay07";
    String ts = "1";
    String dataId = "pay07-sub";

    given()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/billing/subscription")
            .then()
            .statusCode(200)
            .body("status", equalTo("AUTHORIZED"))
            .body("plan", equalTo("PRO"));

    String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + ts + ";";
    String secret = get("mpWebhookSecret");

    String v1;
    try {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256_HMAC.init(secretKey);
        byte[] hash = sha256_HMAC.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
        v1 = HexFormat.of().formatHex(hash).toLowerCase();
    } catch (Exception e) {
        throw new RuntimeException("Erro ao calcular HMAC-SHA256", e);
    }

    String webhookBody = "{\"type\":\"payment\", \"data\":{\"id\":\"" + dataId + "\"}}";

    given()
            .contentType(ContentType.JSON)
            .header("x-request-id", requestId)
            .header("x-signature", "ts=" + ts + ",v1=" + v1)
            .body(webhookBody)
            .when()
            .post("/billing/webhook")
            .then()
            .statusCode(200)
            .body(equalTo("ok"));

    given()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/billing/subscription")
            .then()
            .statusCode(200)
            .body("status", equalTo("AUTHORIZED"))
            .body("plan", equalTo("PRO"));
    }
}
