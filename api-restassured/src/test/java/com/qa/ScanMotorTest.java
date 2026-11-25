package com.qa;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

public class ScanMotorTest extends TestConfig {

    private String tokenQaPro() {
        byte[] chave = get("jwt.secret").getBytes(StandardCharsets.UTF_8);

        return Jwts.builder()
                .subject(get("emailPro"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis()
                + 3600000))
                .id(UUID.randomUUID().toString())
                .signWith(Keys.hmacShaKeyFor(chave))
                .compact();
    }

    @Test
    @DisplayName("SCAN-01: Deve executar o scan passivo com sucesso e garantir que os módulos fiquem OK (exceto CT e CVE)")
    void testeScanMotorPassivo() {
        String token = tokenQaPro();

        Map<?, ?> responseBody = given()
                .header("Authorization", "Bearer " + token)
                .header("Accept-Language", "pt-BR")
                .queryParam("url", "http://juice-shop:3000/")
                .queryParam("active", "false")
                .when()
                .get("/scan")
                .then()
                .statusCode(200)
                .body("httpStatus", equalTo(200))
                .extract()
                .as(Map.class);

        Object rawModuleStatus = responseBody.get("moduleStatus");
        assertNotNull(rawModuleStatus, "Contrato quebrado: o campo 'moduleStatus' está ausente na resposta da API.");

        @SuppressWarnings("unchecked")
        Map<String, String> moduleStatus = (Map<String, String>) rawModuleStatus;

        assertFalse(moduleStatus.isEmpty(), "Contrato quebrado: o mapa 'moduleStatus' veio vazio, impedindo a validação dos módulos.");

        for (Map.Entry<String, String> entry : moduleStatus.entrySet()) {
            String moduleName = entry.getKey();
            String status = entry.getValue();

            if ("CERT_TRANSPARENCY".equals(moduleName) || "CVE".equals(moduleName)) {
                continue;
            }

            assertEquals("OK", status, "O módulo [" + moduleName + "] apresentou status inesperado.");
        }
    }
}
