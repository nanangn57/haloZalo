# identity

Tài khoản, xác minh email, và session. Schema `identity` trên database PostgreSQL `halozalo` dùng chung. Link xác minh hết hạn sau 2 phút. Session id opaque, hết hạn sau 3 ngày, tính từ lúc cấp. Đăng nhập mới không xóa session cũ. Module khác không đọc bảng này. Filter của app gọi `AuthService` trong cùng process.

Người giữ: P1 Ngọc Anh.

Cổng public `8080` khi chạy riêng module này. Database, schema, user và password nằm trong `resources/application.yml`. URL JDBC trỏ vào database `halozalo`. Pool đặt `search_path` là schema `identity`.

Web chạy Vite (thường `http://localhost:5173`) gọi API từ origin khác. `CorsConfig` cho phép origin khớp `identity.cors.allowed-origin-patterns` và trả preflight `OPTIONS` trước `SessionFilter`. Response 401 vẫn có `Access-Control-Allow-Origin`, nên trình duyệt đọc được lỗi thay vì báo CORS. Request không gửi `Origin` (curl, mobile) không đi qua nhánh này.

Chạy bằng Docker, gồm PostgreSQL riêng: `docker compose up --build` trong thư mục này. Context build là `services/` để image gồm cả analytics. App ở `http://localhost:8080`. `docker compose down` giữ dữ liệu. `docker compose down -v` xóa dữ liệu. Trong container, JDBC trỏ vào service `postgres`. Link xác minh vẫn là `http://localhost:8080` vì người dùng mở trên máy mình. Mặc định `identity.mail.mode` là `log`: mail ghi ra log, lấy link bằng `docker compose logs -f identity`. Gmail đặt trong `.env.local` (copy từ `.env.example`), chạy app từ thư mục này để Spring đọc file. File đó không được commit và không vào image. `docker compose` không đọc `.env.local`; muốn gửi từ container thì export cùng các biến trong shell trước khi chạy compose. Cổng 5432 trên máy thuộc `infra/docker-compose.yml`, nên Postgres của compose này mở ở `127.0.0.1:5433`. Image chạy bằng user không phải root.

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
    mail/             gửi mail xác minh
    storage/          interface lưu, và implementation PostgreSQL
    event/            phát event đăng nhập trong process
    service/          đăng ký, xác minh email, đăng nhập, kiểm tra session, nhóm
    http/             nhận HTTP public, filter session, đổi lỗi thành status
  test/identity/      cùng các package trên, mỗi nhóm một test
```

## API

`POST /auth/register` trả 202 với `email`. Chưa tạo tài khoản và chưa cấp token. Mail chứa link `GET /auth/verify?token=`, hết hạn sau 120 giây. Bấm link trong hạn thì trả `userId`, `username`, `email` và xóa bản chờ. Hết hạn hoặc token lạ thì 400. `POST /auth/login` chỉ thành công sau khi đã verify, trả 200 với `accessToken`, `tokenType`, `expiresIn`, và `user`. Không trả hash mật khẩu.

`GET /me` cần `Authorization: Bearer`. Trả `userId`, `username`, `email`. `GET /users?q=` tìm đúng một user theo username, hoặc theo email khi `q` có `@`. Thiếu session thì 401, không trả user. Không có thì 404.

`SessionFilter` bỏ qua đăng ký, xác minh email, và đăng nhập. Path còn lại gọi `AuthService.currentUserId` và gắn `userId` vào request. WebSocket `/ws` lấy session từ query `access_token`, không lấy từ header. Request vào một nhóm hoặc một hội thoại đã mở bị 403 khi user không thuộc nhóm hoặc hội thoại đó. `POST /conversations/direct` và danh sách không cần membership sẵn. Kiểm tra session cho module khác là lời gọi `currentUserId` trong process, không có endpoint HTTP nội bộ.

`POST /groups` body `{ name }` trả 201 với `groupId`, `name`, `ownerId`, `role`. Người tạo là owner. `GET /groups` trả các nhóm của user hiện tại. `POST /groups/{groupId}/members` body `{ userId }` trả 201, role `MEMBER`. `DELETE /groups/{groupId}/members/{userId}` trả 204: owner xoá người khác, thành viên tự rời. Owner không tự rời.

Lỗi: `VALIDATION_ERROR` 400, `CONFLICT` 409, `UNAUTHENTICATED` 401.

Đăng nhập thành công thì `LoggingEventPublisher` ghi event `identity.user.logged_in`, payload chỉ có `userId`. Tạo nhóm xong, sau khi transaction commit, phát `identity.group.created` với `groupId` và `ownerId`. Cả hai được đưa vào analytics trong cùng process. Đăng ký không phát event. `GET /stats` đọc số đã gom.

## Schema

`resources/schema.sql` tạo schema `identity` và bảng lúc khởi động. `CREATE TABLE IF NOT EXISTS` nên lần chạy sau không xóa dữ liệu. Database `halozalo` phải tồn tại trước khi app nối vào.

`identity.accounts.user_id`, `identity.verifications.token`, và `identity.sessions.session_id` là khóa chính. `identity.accounts.username`, `identity.accounts.email`, `identity.verifications.username`, và `identity.verifications.email` là unique. `identity.sessions.user_id` tham chiếu `identity.accounts.user_id`. Bảng `verifications` không tham chiếu `accounts`: dòng này tồn tại trước khi có tài khoản. `password_hash` dài 60 ký tự. `created_at` và `expires_at` là `timestamptz`, đến micro giây. `created_at` là lúc Identity tạo dòng. Bảng đã có từ trước được thêm cột này lúc khởi động; dòng cũ nhận `now()` vì không còn mốc tạo thật.

`AuthService` kiểm tra trùng username và email trên tài khoản và trên bản chờ còn hạn trước khi `INSERT`. Khóa unique chặn lần ghi trùng còn lọt qua. `user_id`, `token`, và `session_id` là UUID do service cấp. Bản chờ hết hạn thì lần đăng ký sau xóa dòng cũ. URL public của link nằm ở `identity.public-base-url` trong `application.yml`.
