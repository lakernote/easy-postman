package com.laker.postman.plugin.kafka.shared;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.Properties;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SaslConfigs;
import org.testng.annotations.Test;

public class KafkaPanelSupportClientPropertiesTest {

    @Test
    public void plaintextShouldNotIncludeSaslSettings() {
        Properties props = KafkaPanelSupport.buildClientProperties(
                "localhost:9092", "", "PLAINTEXT", "PLAIN", "", "", "missing");

        assertEquals(props.get(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG), "localhost:9092");
        assertEquals(props.get(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG), "PLAINTEXT");
        assertFalse(props.containsKey(CommonClientConfigs.CLIENT_ID_CONFIG));
        assertFalse(props.containsKey(SaslConfigs.SASL_JAAS_CONFIG));
    }

    @Test
    public void scramShouldUseScramLoginModuleAndEscapeCredentials() {
        Properties props = KafkaPanelSupport.buildClientProperties(
                "broker:9092", "cid", "SASL_PLAINTEXT", "SCRAM-SHA-256", "u\"1", "p\\w\"d", "missing");

        assertEquals(props.get(CommonClientConfigs.CLIENT_ID_CONFIG), "cid");
        assertEquals(props.get(SaslConfigs.SASL_MECHANISM), "SCRAM-SHA-256");
        String jaas = (String) props.get(SaslConfigs.SASL_JAAS_CONFIG);
        assertTrue(jaas.startsWith("org.apache.kafka.common.security.scram.ScramLoginModule required"));
        assertTrue(jaas.contains("username=\"u\\\"1\""));
        assertTrue(jaas.contains("password=\"p\\\\w\\\"d\""));
    }

    @Test
    public void saslWithoutCredentialsShouldFailWithProvidedMessage() {
        try {
            KafkaPanelSupport.buildClientProperties(
                    "broker:9092", "", "SASL_SSL", "PLAIN", "user", "", "need credentials");
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            assertEquals(ex.getMessage(), "need credentials");
        }
    }
}
