package com.qa;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RelatorioPdfTest extends TestConfig{

    private String tokenQaEnterprise() {
        byte [] chave = get("jwt.secret").getBytes(StandardCharsets.UTF_8);

        return Jwts.builder()
                .subject(get("emailEnterprise"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis()
                + 3600000))
                .id(UUID.randomUUID().toString())
                .signWith(Keys.hmacShaKeyFor(chave))
                .compact();
    }

    @Test
    @DisplayName("REP-01: Usuário Enterprise/Pro com permissão deve baixar um PDF válido contendo os achados do scan")
    void testDownloadRelatorioPdf() {
        String token = tokenQaEnterprise();

        byte[] pdfBytes = given()
                .header("Authorization", "Bearer " + token)
                .queryParam("url", "http://wiremock:8080/health")
                .queryParam("active", "false")
                .when()
                .get("/scan/report/pdf")
                .then()
                .statusCode(200)
                .contentType("application/pdf")
                .extract()
                .asByteArray();

        assertTrue(pdfBytes.length > 4, "O arquivo retornado está vazio");
        String magicBytes = new String(pdfBytes, 0, 4, StandardCharsets.US_ASCII);
        assertEquals("%PDF", magicBytes, "O arquivo retornado não é um PDF válido");

        try (PDDocument document = PDDocument.load(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            assertTrue(text.contains("wiremock:8080/health"), "O PDF não contém o alvo esperado");
            assertTrue(
                    text.contains("SCORE") || text.contains("Strict-Transport-Security") || text.contains("Security"),
                    "O PDF não contem os achados esperados do laudo."
            );
        } catch (Exception e) {
            throw new RuntimeException("Erro ao processar e extrair texto do PDF via PDFBox", e);
        }
    }

    @Test
    @DisplayName("REP-02: O resultado do scan deve conter o mapeamento de compliance da LGPD e ISO 27001 com status coerentes")
    void testMapeamentoComplianceScan() {
        String token = tokenQaEnterprise();

        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("url", "http://wiremock:8080/health")
                .queryParam("active", "false")
                .when()
                .get("/scan")
                .then()
                .statusCode(200)
                .body("compliance", notNullValue())
                .body("compliance.lgpdItems.size()", greaterThan(0))
                .body("compliance.isoItems.size()", greaterThan(0))
                .body("compliance.lgpdItems.find { it.reference == 'Art. 46' }.status", equalTo("FAIL"))
                .body("compliance.isoItems.find { it.reference == 'A.8.24' }.status", equalTo("FAIL"));
    }
}
