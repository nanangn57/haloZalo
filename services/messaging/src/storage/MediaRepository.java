package storage;

import media.Media;

public interface MediaRepository {
    void insert(Media media);

    /**
     * Null when there is no such media.
     */
    Media findById(String mediaId);
}
