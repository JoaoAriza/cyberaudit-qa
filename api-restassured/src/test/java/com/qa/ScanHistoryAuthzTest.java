package com.qa;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

public class ScanHistoryAuthzTest extends TestConfig {

    private static String tokenEnterprise;
    private static String tokenPro;
    private static String scanId;

    private static String tokenPara(String email, String userId) {
        byte[] secret = get("jwt.secret")
                .getBytes(StandardCharsets.UTF_8);

        SecretKeySpec key = new SecretKeySpec(secret, "HmacSHA256");

        return Jwts.builder()
                .setSubject(email)
                .claim("userId", userId)
                .claim("twoFactorPending", false)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis()
                +60*60*1000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    @BeforeAll
    static void prepararScanDoEnterprise() {
        tokenEnterprise = tokenPara(
                get("emailEnterprise").trim(),
                get("userIdEnterprise").trim()
        );

        tokenPro = tokenPara(
                get("emailPro").trim(),
                get("userIdPro").trim()
        );

        given()
                .header("Authorization", "Bearer " + tokenEnterprise)
                .queryParam("url", "http://wiremock:8080/health")
                .queryParam("active", false)
                .when()
                .get("/scan")
                .then()
                .statusCode(200);

        Response response = given()
                .header("Authorization", "Bearer " + tokenEnterprise)
                .when()
                .get("/history/recent")
                .then()
                .statusCode(200)
                .extract().response();

        scanId = response.path("[0].id");

        assertNotNull(scanId, "O ID do scan do Enterprise não pode ser nulo");
    }

    @Test
    void deveDevolverScanParaODono() {
        given()
                .header("Authorization", "Bearer " + tokenEnterprise)
                .when()
                .get("/history/" + scanId + "/result")
                .then()
                .statusCode(200)
                .body("url", notNullValue());
    }

    @Test
    void deveNegarScanDeOutraConta() {
        Response response = given()
                .header("Authorization", "Bearer " + tokenPro)
                .when()
                .get("/history/" + scanId + "/result")
                .then()
                .statusCode(404)
                .extract().response();

        assertEquals(
                0,
                response.asByteArray().length,
                "A resposta 404 deve ter corpo vazio"
        );
    }

    @Test
    void deveResponder40ParaIdInexistente() {
        Response response = given()
                .header("Authorization", "Bearer " + tokenPro)
                .when()
                .get("/history/00000000-0000-0000-0000-000000000000/result")
                .then()
                .statusCode(404)
                .extract().response();

        assertEquals(
                0,
                response.asByteArray().length,
                "O 404 para ID inexistente deve ter corpo vazio"
        );
    }

    @Test
    void deveResponder401SemToken() {
        given()
                .when()
                .get("/history/" + scanId + "/result")
                .then()
                .statusCode(401)
                .body("error", org.hamcrest.Matchers.equalTo(
                        "Autenticação necessária."
                ));
    }





}
