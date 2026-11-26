# Quyết định kiến trúc — bản v1

Chốt cho tuần 1, phục vụ [#2](https://github.com/nanangn57/haloZalo/issues/2). Hợp đồng gọi được nằm ở [openapi.yaml](openapi.yaml). Sự kiện nằm ở [events.md](events.md).

Người giữ hợp đồng: P1 (Ngọc Anh). Một process Spring Boot, một database PostgreSQL `halozalo` (ba schema). Local: Postgres trong Compose. Cloud: Amazon RDS for PostgreSQL. Module không đọc bảng schema khác. Muốn dữ liệu của nhau thì gọi interface trong cùng process, hoặc nhận event phát trong process. Thư mục mỗi người sửa nằm ở [README](../../README.md).

## Bounded context

| Context | Việc sở hữu | Store | Người |
|---|---|---|---|
| Identity & Group | Tài khoản, token, nhóm, thành viên | Schema `identity` | P1 |
| Analytics | Ghi event, đọc bảng tổng hợp | Schema `analytics` | P1 |
| Messaging & Media | Hội thoại, tin, `seq`, file, fan-out | Schema `messaging`. Socket nằm trong bộ nhớ của process | P2 |
| Client | Web và mobile dùng chung một client-core | Cache trên thiết bị | P3 giữ core, P4 gắn mobile |

Ba schema nằm trên cùng database PostgreSQL `halozalo` (Compose local hoặc Amazon RDS trên cloud). Không có foreign key xuyên schema. Client chỉ gọi HTTP và WebSocket của app, không gọi vào từng module.

## Đã chốt

- **Kiến trúc:** modular monolith. Một process, một lệnh deploy, ba schema. Module gọi nhau bằng interface. Event phát trong process sau khi transaction nguồn commit. Nhiều process, Mongo và Redis bị bỏ vì hạng 1 trong [requirement.md](../requirement.md) cần lên cloud bằng một lệnh. Microservice cắt nhỏ bị loại vì cùng lý do. Hạng 2 giữ ranh giới bằng schema và interface: module không đọc bảng schema khác.
- **Realtime:** WebSocket trên cùng process. Ghi DB xong mới phát tin. Mục tiêu text: p95 từ lúc gửi đến lúc hiện dưới 500ms.
- **Chống trùng:** at-least-once cộng `clientMsgId`. Cùng người gửi, cùng `clientMsgId` thì trả lại đúng tin đã ghi, không tạo `seq` mới.
- **Thứ tự:** server gán `seq` từng hội thoại, bắt đầu từ 1, không để client tự đánh.
- **Đã đọc:** server giữ mốc đã đọc theo user và hội thoại. Đọc ở một thiết bị thì thiết bị kia hết unread.
- **Emotion hạng 1:** một tin riêng, `type = EMOTION`, `body` là mã icon. Bộ mã: `like`, `love`, `haha`, `wow`, `sad`, `angry`.
- **Auth:** Access token là session id opaque, không phải JWT, không chứa `userId`. Session **gắn device**: `POST /auth/login` và `GET /auth/verify` **require** `deviceId` (client lưu bền trên thiết bị), optional `deviceLabel` / `userAgent`. Unique `(user_id, device_id)` — login lại cùng device **rotate** token, không tạo thêm dòng. Hết hạn **idle 30 ngày** (gia hạn khi còn dùng; touch throttle 5 phút), absolute max **90 ngày** từ `createdAt`. Logout/revoke mới cắt phiên. Identity giữ bảng session (`device_id`, `device_label`, `user_agent`, `last_seen_at`). Module khác không đọc bảng đó. KPI analytics `logins` đếm event `identity.user.logged_in` (số lần đăng nhập), **khác** số device đang sống.
- **Đăng xuất / phiên:** `POST /auth/logout` xoá session hiện tại. `GET /auth/sessions` liệt kê phiên đang sống (Settings). `DELETE /auth/sessions/{sessionId}` revoke từ xa. Client logout phải gọi server rồi mới xoá token local. Activity dashboard **không** liệt kê devices.
- **Đăng nhập:** chỉ bằng **email** + mật khẩu (không dùng username làm login) + `deviceId`. Đăng ký nhận `email`, `password`, `displayName`; `username` nội bộ derive từ email (phần trước `@`, uniquify nếu trùng).
- **Đổi / quên mật khẩu:** `POST /auth/password/forgot` gửi link qua email; `POST /auth/password/reset` với token; `POST /auth/password/change` khi đã đăng nhập (mật khẩu cũ + mới; tuỳ chọn revoke phiên khác).

Đăng ký theo `identity.registration.activation` (`IDENTITY_REGISTRATION_ACTIVATION`):

- `email` (mặc định): gửi link xác minh. Link công khai là `{IDENTITY_PUBLIC_BASE_URL}/auth/verify?token=` (mặc định cloud: `http://<EC2_IP>/auth/verify?token=` — **không dùng domain**). Link hết hạn sau **2 phút** (120 giây). Bấm link trong hạn thì Identity tạo tài khoản **và cấp session** (`TokenResponse`) — client vào app đã đăng nhập. Hết hạn: báo lỗi + `POST /auth/verify/resend` (rate-limit). Đăng ký lại sau khi hết hạn thì được; link cũ không dùng được. `RegisterAccepted.status = PENDING_VERIFICATION`. Local: `IDENTITY_MAIL_MODE=log`. Cloud: `IDENTITY_MAIL_MODE=smtp` qua Spring JavaMail / **SMTP Gmail** (không SES).
- `immediate` (ops hiếm): Identity tạo tài khoản ngay trong `POST /auth/register`, không gửi mail. `RegisterAccepted.status = ACTIVE`.

Mỗi lần đăng nhập hoặc verify thành công, Identity upsert session theo `deviceId` và trả `accessToken`. Nhiều thiết bị cùng lúc được phép (mỗi device một session).

Request có `Authorization: Bearer`, hoặc WebSocket có query `access_token`, thì filter trong cùng process gọi `AuthService`. Identity trả `userId` khi còn dòng, còn hạn, và tài khoản **không bị ban**. Thiếu session / hết hạn / banned → 401.
- **Thành viên nhóm:** Chỉ hai role: `OWNER` | `MEMBER` (không còn `ADMIN` nhóm). Người tạo = owner. `POST /groups` nhận `memberIds` tuỳ chọn để thêm thành viên ngay lúc tạo (cùng transaction; phát `member_added` cho từng người). Owner thêm/xoá thành viên, chuyển quyền owner (`POST /groups/{groupId}/transfer-owner`), giải tán nhóm (`DELETE /groups/{groupId}`), sửa tên/ảnh/mô tả nhóm. **Tên nhóm tuỳ chọn:** để trống thì client hiện tên theo mọi thành viên gồm owner (tối đa 2–3 người + "…"); có tên đặt tay thì dùng tên đó. Member có thể rời. Owner rời được **sau khi** đã chuyển owner; nếu còn một mình thì giải tán. Add thành viên: **add thẳng** (không lời mời accept). Messaging hỏi membership qua `MembershipLookup` trong process.
- **Profile và bạn bè:** `GET /me` / `PATCH /me` — `displayName`, `avatar`, `bio` tuỳ chọn. Friend: gửi / chấp nhận / **từ chối** / **huỷ lời mời đã gửi**; `GET /friends/requests` (incoming + outgoing); `DELETE /friends/{userId}` unfriend; block/unblock. Avatar mặc định: client hiện chữ cái viết tắt khi không có URL.
- **Tin nhắn chờ (người lạ):** Chat 1-1 với người chưa là bạn vào hàng “message request”; chỉ hiện đầy đủ sau khi accept kết bạn hoặc accept request. Block chặn gửi tin.
- **Platform role:** `platformRole` (`USER` | `ADMIN`) khác group owner. Bootstrap qua `identity.platform-admins`. System admin có `POST /admin/users/{userId}/ban` và unban.
- **Dashboard:** `GET /me/activity?from=&to=` — self-stats (tin gửi gồm 1-1 + nhóm; KPI + series theo ngày, heatmap, friends, top conversations). `friends.count` là snapshot bạn hiện tại từ identity (không dùng counter analytics có thể lệch seed/unfriend); `requestsSent` / `requestsReceived` / `acceptRate` theo event trong khoảng — reconcile backfill idempotent (event id ổn định `frs`/`fra`) khi friendship tồn tại mà analytics chưa ghi. `groupsCreated` đếm event `identity.group.created` (id ổn định `grp` theo `groupId`); reconcile backfill từng nhóm còn thiếu kể cả khi `groups_by_day` đã có dòng; `known_groups` chặn double-count nếu event cũ từng dùng UUID ngẫu nhiên. `GET /stats` / `GET /stats/dashboard?from=&to=` chỉ platform ADMIN (DAU/WAU/MAU, funnel verify, social, media by type). `GET /admin/users/{userId}` — tóm tắt hồ sơ + activity cho admin. Không có dashboard thống kê theo nhóm. Devices trên UI lấy từ `GET /auth/sessions`, không nằm trong analytics.
- **Analytics store (layered, không nhân bản domain):** Ledger `recorded_events`. Fact cá nhân chính: `user_activity_by_day` (+ heatmap / top conversation). Rollup platform theo ngày (`*_by_day`) chỉ giữ khi cần field/aggregate admin không có trong fact cá nhân (vd. `groups_by_day.members_sum`, `media_by_day` theo type). Cohort funnel: `user_registrations` / `user_verifications`. Snapshot quan hệ đọc identity, không tin counter lifetime. Không đọc schema `messaging`.
- **Media:** Upload `POST /media/uploads`, tải `GET /media/{mediaId}`. Local disk / cloud S3. Types: `IMAGE` / `DOCUMENT` / `VIDEO` / `AUDIO` (voice note). Caption trên image/video. Giới hạn: text 4KB, image 5MB, document 10MB, video 20MB, audio 5MB.
- **Hội thoại:** Mở trước khi gửi. Direct: `POST /conversations/direct`. Group: `groupId` = `conversationId`. Prefs per-user: pin, mute, archive. Search tin trong hội thoại: `GET …/messages?q=`. Thu hồi tin: không hạn thời gian; chỉ sender; delete-for-everyone. React-on-message qua reactions API (khác tin `EMOTION`). Reply / forward giữ như OpenAPI. Seen nhóm = read marker hội thoại (không list người xem).
- **Tin nhắn chờ (cài đặt):** `request_status` `OPEN`/`PENDING` theo từng thành viên của chat 1-1. Người mở `OPEN`, người nhận `PENDING` nếu chưa là bạn. Accept bằng `POST /conversations/{id}/accept-request`, gửi tin trả lời, hoặc khi hai bên thành bạn (kiểm tra lúc mở/gửi, chưa nghe event kết bạn). Block (một trong hai chiều) → 403 khi mở/gửi. Messaging hỏi `FriendshipLookup` / `BlockLookup` của identity qua adapter trong `services/app`.
- **System message:** `MessageType.SYSTEM` (không sender). Identity phát membership events; `GroupSystemMessages` trong app ghi tin hệ thống vào hội thoại nhóm (“A added B”, “A left”, transfer, dissolve).
- **Dashboard theo ngày:** `from`/`to` lọc thật qua bảng `*_by_day` / `active_users_by_day` / `user_activity_by_day`. `messaging.message.created` mang `conversationType` + `recipientIds` để đếm `messagesReceived` và split direct/group. Identity phát `registered` / `verified` / `friend.request_*` cho funnel và social KPIs. `verifyFunnel` là cohort theo ngày đăng ký trong khoảng: registered → verified (cùng cohort) → firstMessage (cùng cohort đã gửi ≥1 tin). `Dashboard.heatmap` = tổng `user_hour_weekday` toàn platform. Monolith có reconcile idempotent identity→analytics (bù thiếu khi event cũ chưa ghi); không quét schema `messaging`.
- **Tên/avatar trong hội thoại:** Client lookup `GET /users/lookup?ids=` (`displayName`, `avatar`). Không có avatar thì hiện chữ cái.
- **Push khi offline:** Expo Push khi không có socket. Mute conversation → không push. Đăng ký device token như OpenAPI.
- **Release / infra (đã chốt):** Không dùng domain công khai. Public URL = `http://<EC2_IP>` (WS: `ws://<EC2_IP>/…`). EC2 `app` + nginx HTTP, RDS PostgreSQL, S3 media, mail SMTP Gmail. Không Cloudflare, không Let's Encrypt, không SES. Local: Compose Postgres + mail `log`.

## Bản này cố ý chưa có (P3 / ngoài scope)

- Refresh token riêng (session idle sliding đã có; không cấp refresh token).
- FCM native đầy đủ ngoài đường Expo Push.
- Promote `platformRole` bằng API (bootstrap chỉ qua config).
- Call/video, sticker/GIF phong phú, story, invite link/QR, poll, dịch tin, export chat, Redis multi-node.
