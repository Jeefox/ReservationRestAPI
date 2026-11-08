package grevcev;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationContractTest {
    @Test
    void devIsExplicitAndProductionHasNoDemoCredentials() throws Exception {
        Properties common = properties("application.properties");
        Properties prod = properties("application-prod.properties");
        assertThat(common).doesNotContainKey("spring.profiles.active").doesNotContainKey("spring.profiles.default");
        assertThat(common.getProperty("server.port")).isEqualTo("8085");
        assertThat(prod.getProperty("app.jwt.secret")).isEqualTo("${JWT_SECRET}");
        assertThat(prod.getProperty("spring.datasource.password")).isEqualTo("${DB_PASSWORD}");
        assertThat(prod.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
    }

    private Properties properties(String path) throws Exception {
        var properties = new Properties();
        try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
            properties.load(input);
        }
        return properties;
    }
}
