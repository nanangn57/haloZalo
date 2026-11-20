package storage;

import media.Media;
import message.MessageType;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public final class PostgresMediaRepository implements MediaRepository {
    private final JdbcTemplate jdbc;

    public PostgresMediaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Media media) {
        jdbc.update(
            "INSERT INTO media (id, conversation_id, uploader_id, kind, file_name, mime_type, size_bytes, width, height, "
                + "created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            Sql.uuid(media.mediaId()), Sql.uuid(media.conversationId()), Sql.uuid(media.uploaderId()),
            media.kind().name(), media.fileName(), media.mimeType(), media.size(), media.width(), media.height(),
            Sql.time(media.createdAt()));
    }

    @Override
    public Media findById(String mediaId) {
        List<Media> found = jdbc.query(
            "SELECT id::text AS id, conversation_id::text AS conversation_id, uploader_id::text AS uploader_id, kind, "
                + "file_name, mime_type, size_bytes, width, height, created_at FROM media WHERE id = ?",
            (row, index) -> new Media(
                row.getString("id"),
                row.getString("conversation_id"),
                row.getString("uploader_id"),
                MessageType.valueOf(row.getString("kind")),
                row.getString("file_name"),
                row.getString("mime_type"),
                row.getLong("size_bytes"),
                row.getObject("width", Integer.class),
                row.getObject("height", Integer.class),
                Sql.instant(row, "created_at")),
            Sql.uuid(mediaId));
        return found.isEmpty() ? null : found.get(0);
    }
}
