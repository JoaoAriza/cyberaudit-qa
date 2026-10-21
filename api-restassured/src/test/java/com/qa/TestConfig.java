package com.qa;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Properties;

public class TestConfig {

    private static final Properties properties = new Properties();

    static {
        try (InputStream input = TestConfig.class
                .getClassLoader()
                .getResourceAsStream("config.properties")) {

            if (input == null) {
                throw new RuntimeException("config.properties não encontrado");
            }

            properties.load(input);

        } catch (IOException e) {
            throw new RuntimeException("Erro ao carregar config.properties", e);
        }
    }

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = get("base.uri");
    }

    public static String get(String key) {
        return properties.getProperty(key);
    }

    /**
     * Reseta a conta do usuário com este e-mail de volta para FREE e apaga as
     * assinaturas dela — housekeeping para os testes de pagamento que fazem upgrade
     * (PAY-01, PAY-06, PAY-14). Sem isto, re-rodar no mesmo ambiente acha a conta já
     * PRO e o checkout responde 409. Chame num {@code @BeforeEach} do teste de upgrade,
     * passando o e-mail da conta dedicada (ex.: {@code get("emailPayCard")}).
     *
     * Fala direto com o Postgres do ambiente de teste (db.* no config.properties). É
     * só limpeza de estado — o caso em si continua exercitando a API por HTTP.
     */
    public static void resetarContaParaFree(String email) {
        String deleteSubs = "DELETE FROM subscriptions WHERE account_id = "
                + "(SELECT account_id FROM app_users WHERE email = ?)";
        String freeAccount = "UPDATE accounts SET plan = 'FREE' WHERE id = "
                + "(SELECT account_id FROM app_users WHERE email = ?)";
        try (Connection conn = DriverManager.getConnection(
                get("db.url"), get("db.user"), get("db.password"))) {
            try (PreparedStatement ps = conn.prepareStatement(deleteSubs)) {
                ps.setString(1, email);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(freeAccount)) {
                ps.setString(1, email);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao resetar conta para FREE: " + email, e);
        }
    }

    /**
     * Prepara o cenário do PAY-10: deixa a conta do e-mail em PRO com UMA assinatura
     * Pix AUTHORIZED cujo {@code current_period_end} já está no passado (vencida). O
     * job de expiração ({@code /admin/test/expire-pix}) deve então rebaixá-la para
     * FREE/EXPIRED. Chamar num {@code @BeforeEach} para o teste ser idempotente.
     */
    public static void prepararPixVencida(String email) {
        String delSubs = "DELETE FROM subscriptions WHERE account_id = "
                + "(SELECT account_id FROM app_users WHERE email = ?)";
        String setPro = "UPDATE accounts SET plan = 'PRO' WHERE id = "
                + "(SELECT account_id FROM app_users WHERE email = ?)";
        String insSub = "INSERT INTO subscriptions "
                + "(id, account_id, plan, status, payment_method, amount, currency, "
                + " mp_payment_id, created_at, current_period_end, updated_at) "
                + "SELECT gen_random_uuid(), account_id, 'PRO', 'AUTHORIZED', 'PIX', 19.99, 'BRL', "
                + " 'pay10-' || gen_random_uuid(), now() - interval '2 days', "
                + " now() - interval '1 day', now() "
                + "FROM app_users WHERE email = ?";
        try (Connection conn = DriverManager.getConnection(
                get("db.url"), get("db.user"), get("db.password"))) {
            for (String sql : new String[]{delSubs, setPro, insSub}) {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, email);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao preparar Pix vencida: " + email, e);
        }
    }
}
