package com.qa;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.containsString;

public class PagamentoPixTest extends TestConfig {

    private String tokenQaPayPix() {
    byte [] chave = get("jwt.secret")
            .getBytes(StandardCharsets.UTF_8);

    return Jwts.builder()
            .subject(get("emailPayPix"))
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis()
            + 3600000))
            .id(UUID.randomUUID().toString())
            .signWith(Keys.hmacShaKeyFor(chave))
            .compact();
    }

    private String tokenQaFree() {
        byte [] chave = get("jwt.secret")
                .getBytes(StandardCharsets.UTF_8);

        return Jwts.builder()
                .subject(get("emailFree"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis()
                        + 3600000))
                .id(UUID.randomUUID().toString())
                .signWith(Keys.hmacShaKeyFor(chave))
                .compact();
    }

    private String tokenQaPayNoCpf() {
        byte [] chave = get("jwt.secret")
                .getBytes(StandardCharsets.UTF_8);

        return Jwts.builder()
                .subject(get("emailPayNoCpf"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis()
                        + 3600000))
                .id(UUID.randomUUID().toString())
                .signWith(Keys.hmacShaKeyFor(chave))
                .compact();
    }

    @Test
    void deveCriarPagamentoPixComQrCode() {
        String token = tokenQaPayPix();

        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {
                            "plan": "PRO",
                            "cpf": "111.444.777-35"
                        }
                        """)
                .when()
                .post("/billing/checkout/pix")
                .then()
                .statusCode(200)
                .body("status", equalTo("pending"))
                .body("qrCode", notNullValue())
                .body("qrCodeBase64", notNullValue());
    }

    @Test
    @DisplayName("PAY-13: Pix com CPF de 11 digitos mas com digito verificar inválido deve retornar bad request (400)")
    void deveRetornarErroCpfDvInvalido() {
        String token = tokenQaFree();

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "plan": "PRO",
                            "cpf": "11111111111"
                        }
                        """)
                .when()
                .post("/billing/checkout/pix")
                .then()
                .statusCode(400)
                .body("error", equalTo("400 BAD_REQUEST"))
                .body("message", containsString("dígito verificador não confere"));
    }

    @Test
    @DisplayName("PAY-14: Checkout de cartão de crédito sem o campo CPF deve autorizar com sucesso (200)")
    void testCartaoSemCpfAutorizado() {
        String token = tokenQaPayNoCpf();

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token)
                .body("""
                        {
                            "plan": "PRO",
                            "cardTokenId": "card-ok"
                        }
                        """)
                .when()
                .post("/billing/checkout/card")
                .then()
                .statusCode(200)
                .body("plan", equalTo("PRO"))
                .body("status", equalTo("AUTHORIZED"));
    }
}
