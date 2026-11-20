package storage;

import media.Media;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MemoryMedia implements MediaRepository {
    private final Map<String, Media> rows = new ConcurrentHashMap<>();

    @Override
    public void insert(Media media) {
        rows.put(media.mediaId(), media);
    }

    @Override
    public Media findById(String mediaId) {
        return rows.get(mediaId);
    }

    public int size() {
        return rows.size();
    }
}
