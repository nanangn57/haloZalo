package identity.app;

import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.logging.Logger;

import javax.sql.DataSource;

import analytics.record.ActivityRecorder;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import identity.event.LoggingEventPublisher;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class IdentityApplicationStartupTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(IdentityApplication.class)
        .withPropertyValues(
            "spring.main.web-application-type=none",
            "spring.datasource.type=" + QuietDataSource.class.getName(),
            "spring.datasource.url=jdbc:postgresql://127.0.0.1:9/halozalo",
            "spring.datasource.username=halozalo",
            "spring.datasource.password=halozalo",
            "identity.public-base-url=http://localhost:8080"
        );

    @Test
    void eventPublisherReceivesTheActivityRecorder() {
        runner.run(context -> {
            assertNotNull(context.getBean(ActivityRecorder.class));
            assertNotNull(context.getBean(LoggingEventPublisher.class));
        });
    }

    public static final class QuietDataSource implements DataSource {
        private final Connection connection = (Connection) Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] { Connection.class },
            (proxy, method, args) -> {
                if ("createStatement".equals(method.getName())) {
                    return (Statement) Proxy.newProxyInstance(
                        Statement.class.getClassLoader(),
                        new Class<?>[] { Statement.class },
                        (statement, statementMethod, statementArgs) -> {
                            if (statementMethod.getReturnType() == boolean.class) {
                                return false;
                            }
                            if (statementMethod.getReturnType() == int.class) {
                                return 0;
                            }
                            return null;
                        }
                    );
                }
                if (method.getReturnType() == boolean.class) {
                    return false;
                }
                if (method.getReturnType() == int.class) {
                    return 0;
                }
                return null;
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
    }
}
