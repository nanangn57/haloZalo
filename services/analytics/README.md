# analytics

Ghi event, đọc bảng tổng hợp. Schema `analytics` trên cùng database PostgreSQL `halozalo` với các module khác.

Người giữ: P1 Ngọc Anh. Chỉ nhận event trong process, không quét bảng schema `messaging`.

Ba type được đếm: `identity.user.logged_in`, `identity.group.created`, `messaging.message.created`. Cùng `eventId` ghi lại thì tổng không tăng. Envelope khác ba type này bị bỏ qua. `GET /stats` đọc `analytics.totals`, trả `logins`, `groupsCreated`, `messagesCreated`.

Identity gọi `ActivityRecorder.record` sau khi transaction login hoặc tạo nhóm đã commit. Messaging gọi cùng recorder với envelope `messaging.message.created` sau khi transaction ghi tin đã commit. Stats không đọc bảng `messaging`.

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

Identity phụ thuộc jar này và quét package `analytics` trong cùng process. `analytics-schema.sql` nằm trên classpath, identity tạo schema lúc khởi động.
