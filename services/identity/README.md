# identity

Tài khoản, xác minh email, và session. Schema `identity` trên database PostgreSQL `halozalo` dùng chung (local: Compose; cloud: Amazon RDS). Link xác minh hết hạn sau 2 phút. Session id opaque, gắn `deviceId` (unique `(user_id, device_id)`); idle 30 ngày (gia hạn khi còn dùng), absolute max 90 ngày. Login/verify cùng device rotate token. Module khác không đọc bảng này. Filter của app gọi `AuthService` trong cùng process.

Người giữ: P1 Ngọc Anh.

Cổng public `8080` khi chạy riêng module này. Database, schema, user và password nằm trong `resources/application.yml`. URL JDBC trỏ vào database `halozalo`. Pool đặt `search_path` là schema `identity`. Stack monolith khuyến nghị: `infra/scripts/local-up.sh` (hoặc cloud qua `infra/scripts/deploy-ec2.sh` + RDS).

Web chạy Vite (thường `http://localhost:5173`) gọi API từ origin khác. `CorsConfig` cho phép origin khớp `identity.cors.allowed-origin-patterns` và trả preflight `OPTIONS` trước `SessionFilter`. Response 401 vẫn có `Access-Control-Allow-Origin`, nên trình duyệt đọc được lỗi thay vì báo CORS. Request không gửi `Origin` (curl, mobile) không đi qua nhánh này.

**Mail xác minh:** `identity.mail.mode` = `log` (local, in ra log app) hoặc `smtp` (Gmail/SMTP qua `spring.mail` / `SPRING_MAIL_*`). Cloud dùng Gmail app password trong `.env`.

Stack khuyến nghị: `infra/scripts/local-up.sh`. Có thể chạy riêng module bằng Docker trong thư mục này (`docker compose up --build`); Postgres module mở `127.0.0.1:5433` để tránh đụng cổng `infra`. Local lấy link verify từ log (`IDENTITY_MAIL_MODE=log`).

```
services/identity/
  pom.xml
  Dockerfile
  docker-compose.yml
  resources/          cổng, database, và schema.sql
  src/identity/
    app/              khởi động Spring Boot
    account/          dữ liệu tài khoản, gồm hash mật khẩu
    verification/     dữ liệu bản chờ xác minh email
    session/          dữ liệu session
    access/           quyền vào nhóm và hội thoại, trước handler
    group/            dữ liệu nhóm, quyền theo role
    auth/             JSON request và response
    password/         hash và so khớp bcrypt
    mail/             MailSender: LoggingMailSender (log) hoặc SmtpMailSender (smtp)
    storage/          interface lưu, và implementation PostgreSQL
    event/            phát event đăng nhập trong process
    service/          đăng ký, xác minh email, đăng nhập, kiểm tra session, nhóm
    http/             nhận HTTP public, filter session, đổi lỗi thành status
  test/identity/      cùng các package trên, mỗi nhóm một test
```

## API

`POST /auth/register` trả 202 với `email` và `status`. `IDENTITY_REGISTRATION_ACTIVATION=email` (mặc định): `status=PENDING_VERIFICATION`, chưa tạo tài khoản; mail tạo link `IDENTITY_PUBLIC_BASE_URL/auth/verify?token=` (cloud: trang web gọi `GET /api/identity/auth/verify`), hết hạn 900 giây. Bấm link trong hạn thì trả `userId`, `username`, `email` và xóa bản chờ. `IDENTITY_REGISTRATION_ACTIVATION=immediate`: `status=ACTIVE`, tạo tài khoản ngay (ops). `POST /auth/login` cần tài khoản đã tạo, trả 200 với `accessToken`, `tokenType`, `expiresIn`, và `user`. Không trả hash mật khẩu.

`GET /me` cần `Authorization: Bearer`. Trả `userId`, `username`, `email`. `GET /users?q=` tìm đúng một user theo username, hoặc theo email khi `q` có `@`. Thiếu session thì 401, không trả user. Không có thì 404. `GET /users/lookup?ids=a,b` trả `{ users: [{ userId, username }] }` theo thứ tự id gửi lên, bỏ id trùng và id không có tài khoản, không trả email. 1 đến 100 UUID, sai thì 400. Client dùng để hiện tên thành viên hội thoại.

`SessionFilter` bỏ qua đăng ký, xác minh email, và đăng nhập. Path còn lại gọi `AuthService.currentUserId` và gắn `userId` vào request. WebSocket `/ws` lấy session từ query `access_token`, không lấy từ header. Request vào một nhóm hoặc một hội thoại đã mở bị 403 khi user không thuộc nhóm hoặc hội thoại đó. `POST /conversations/direct` và danh sách không cần membership sẵn. Kiểm tra session cho module khác là lời gọi `currentUserId` trong process, không có endpoint HTTP nội bộ.

`POST /groups` body `{ name }` trả 201 với `groupId`, `name`, `ownerId`, `role`. Người tạo là owner. `GET /groups` trả các nhóm của user hiện tại. `GET /groups/{groupId}/members` trả `{ members: [{ userId, username, role }] }` cho thành viên của nhóm đó; người ngoài nhóm thì 403. `POST /groups/{groupId}/members` body `{ userId }` trả 201, role `MEMBER`. `DELETE /groups/{groupId}/members/{userId}` trả 204: owner xoá người khác, thành viên tự rời. Owner không tự rời.

Lỗi: `VALIDATION_ERROR` 400, `CONFLICT` 409, `UNAUTHENTICATED` 401.

Đăng nhập thành công thì `LoggingEventPublisher` ghi event `identity.user.logged_in`, payload chỉ có `userId`. Tạo nhóm xong, sau khi transaction commit, phát `identity.group.created` với `groupId` và `ownerId`. Cả hai được đưa vào analytics trong cùng process. Đăng ký không phát event. `GET /stats` đọc số đã gom.

## Schema

`resources/schema.sql` tạo schema `identity` và bảng lúc khởi động. `CREATE TABLE IF NOT EXISTS` nên lần chạy sau không xóa dữ liệu. Database `halozalo` phải tồn tại trước khi app nối vào.

`identity.accounts.user_id`, `identity.verifications.token`, và `identity.sessions.session_id` là khóa chính. `identity.accounts.username`, `identity.accounts.email`, `identity.verifications.username`, và `identity.verifications.email` là unique. `identity.sessions.user_id` tham chiếu `identity.accounts.user_id`. Bảng `verifications` không tham chiếu `accounts`: dòng này tồn tại trước khi có tài khoản. `password_hash` dài 60 ký tự. `created_at` và `expires_at` là `timestamptz`, đến micro giây. `created_at` là lúc Identity tạo dòng. Bảng đã có từ trước được thêm cột này lúc khởi động; dòng cũ nhận `now()` vì không còn mốc tạo thật.

`AuthService` kiểm tra trùng username và email trên tài khoản và trên bản chờ còn hạn trước khi `INSERT`. Khóa unique chặn lần ghi trùng còn lọt qua. `user_id`, `token`, và `session_id` là UUID do service cấp. Bản chờ hết hạn thì lần đăng ký sau xóa dòng cũ. URL public của link nằm ở `identity.public-base-url` trong `application.yml`.
