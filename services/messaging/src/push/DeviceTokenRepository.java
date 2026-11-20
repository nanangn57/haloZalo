package push;

import java.util.List;

public interface DeviceTokenRepository {
    void upsert(String userId, String token, String platform);

    void delete(String userId, String token);

    List<String> tokensOf(String userId);
}
