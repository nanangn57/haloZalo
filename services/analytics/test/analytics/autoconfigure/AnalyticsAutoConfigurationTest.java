package analytics.autoconfigure;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.logging.Logger;

import javax.sql.DataSource;

import analytics.record.ActivityRecorder;
import analytics.store.ActivityRepository;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class AnalyticsAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, AnalyticsAutoConfiguration.class))
        .withPropertyValues(
            "spring.datasource.type=" + QuietDataSource.class.getName(),
            "spring.datasource.url=jdbc:postgresql://127.0.0.1:9/halozalo",
            "spring.datasource.username=halozalo",
            "spring.datasource.password=halozalo"
        );

    @Test
    void recorderExistsWhenTheDataSourceComesFromAutoConfiguration() {
        runner.run(context -> {
            assertNotNull(context.getBean(ActivityRecorder.class));
            assertNotNull(context.getBean(ActivityRepository.class));
        });
    }

    @Test
    void recorderIsAbsentWhenTheProcessHasNoDataSource() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AnalyticsAutoConfiguration.class))
            .run(context -> assertTrue(context.getBeansOfType(ActivityRecorder.class).isEmpty()));
    }

    @Test
    void autoConfigurationIsListedForTheClasspath() throws IOException {
        String path = "/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
        try (InputStream in = AnalyticsAutoConfiguration.class.getResourceAsStream(path)) {
            if (in == null) {
                fail(path + " is missing");
            }
            String listed = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(listed.contains(AnalyticsAutoConfiguration.class.getName()));
        }
    }

    public static final class QuietDataSource implements DataSource {
        private final Connection connection = (Connection) Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] { Connection.class },
            (proxy, method, args) -> {
                if ("createStatement".equals(method.getName()) || "prepareStatement".equals(method.getName())) {
                    return statement();
                }
                return quiet(method.getReturnType());
            }
        );

        @Override
        public Connection getConnection() {
            return connection;
        }

        @Override
        public Connection getConnection(String username, String password) {
            return connection;
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }

        public void setUrl(String url) {
        }

        public void setUsername(String username) {
        }

        public void setPassword(String password) {
        }

        public void setDriverClassName(String driverClassName) {
        }

        private static Statement statement() {
            return (Statement) Proxy.newProxyInstance(
                Statement.class.getClassLoader(),
                new Class<?>[] { Statement.class },
                (proxy, method, args) -> quiet(method.getReturnType())
            );
        }

        private static Object quiet(Class<?> type) {
            if (type == boolean.class) {
                return false;
            }
            if (type == int.class) {
                return 0;
            }
            if (type == long.class) {
                return 0L;
            }
            return null;
        }
    }
}
