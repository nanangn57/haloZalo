# analytics

Ghi event, đọc bảng tổng hợp. Schema `analytics` trên cùng database PostgreSQL `halozalo` với các module khác.

Người giữ: P1 Ngọc Anh. Chỉ nhận event trong process, không quét bảng schema `messaging`.

Bốn type được đếm: `identity.user.logged_in`, `identity.group.created`, `messaging.message.created`, `messaging.media.uploaded`. Cùng `eventId` ghi lại thì tổng không tăng. Envelope khác bốn type này bị bỏ qua. `GET /stats` đọc `analytics.totals`, trả `logins`, `groupsCreated`, `messagesCreated`, `mediaUploads`.

Identity gọi `ActivityRecorder.record` sau khi transaction login hoặc tạo nhóm đã commit. Messaging gọi cùng recorder sau khi transaction nguồn commit, với `messaging.message.created` và `messaging.media.uploaded`. Stats không đọc bảng `messaging`.

```
services/analytics/
  pom.xml
  resources/analytics-schema.sql
  src/analytics/
    record/     nhận envelope, chống trùng bằng eventId
    store/      bảng recorded_events và totals
    query/      đọc tổng
    http/       GET /stats
  test/
```

Identity phụ thuộc jar này và quét package `analytics` trong cùng process để có `GET /stats`. `ActivityRecorder` không phải `@Component`: auto-configuration đăng ký bean đó sau khi `DataSource` đã có. Gắn `@ConditionalOnBean(DataSource.class)` lên `@Component` thì lúc quét component bean chưa tồn tại, identity không khởi động được. `analytics-schema.sql` nằm trên classpath, identity tạo schema lúc khởi động.
