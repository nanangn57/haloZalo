# analytics

Ghi event, đọc bảng tổng hợp. Schema `analytics` trên cùng database PostgreSQL `halozalo` với các module khác (local: Compose; cloud: Amazon RDS).

Người giữ: P1 Ngọc Anh. Chỉ nhận event trong process, không quét bảng schema `messaging`.

## Vì sao nhiều bảng?

Không phải copy bảng identity. Đây là **rollup OLAP** tách lớp:

| Lớp | Việc | Ví dụ |
|---|---|---|
| A. Ledger | Chống trùng theo `eventId` | `recorded_events` |
| B. Fact cá nhân | Self-stats theo `from`/`to` | `user_activity_by_day` |
| C. Rollup platform | Admin KPI / chart rẻ | `groups_by_day`, `media_by_day`, `active_users_by_day` |
| D. Dimension / cache | Cohort, top group, lifetime | `known_groups`, `user_registrations`, `user_activity` |

Một số bảng C **trùng ý nghĩa** với SUM từ B (vd. `messages_by_day` ≈ tổng `messages_sent`). Giữ tạm để admin query O(days) không O(users×days). Mục tiêu tối ưu: dần **derive C từ B** khi không còn field riêng; không thêm bảng mới chỉ để mirror counter.

Snapshot (`friends.count`) đọc identity live — không dùng counter analytics dễ lệch seed/unfriend.

## Event

Types chính: `identity.user.logged_in`, `identity.group.created`, `messaging.message.created`, `messaging.media.uploaded`, plus registered/verified/friend.*. Cùng `eventId` ghi lại thì tổng không tăng. `group.created` dùng id ổn định theo `groupId`; `known_groups` đảm bảo mỗi nhóm chỉ tăng counter một lần.

Identity / messaging gọi `ActivityRecorder.record` sau khi transaction nguồn commit. Monolith `IdentityAnalyticsReconcile` backfill idempotent identity → analytics lúc startup.

```
services/analytics/
  pom.xml
  resources/analytics-schema.sql
  src/analytics/
    record/     nhận envelope, chống trùng bằng eventId
    store/      ledger + rollups
    query/      đọc tổng / dashboard
    http/       GET /stats, /me/activity, /stats/dashboard
  test/
```

`ActivityRecorder` không phải `@Component`: auto-configuration đăng ký bean sau khi `DataSource` có. `analytics-schema.sql` nằm trên classpath, app tạo schema lúc khởi động.
