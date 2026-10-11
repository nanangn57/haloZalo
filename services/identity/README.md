# identity

Tài khoản, xác minh email, và session. Schema `identity` trên database PostgreSQL `halozalo` dùng chung. Link xác minh hết hạn sau 2 phút. Session id opaque, hết hạn sau 3 ngày, tính từ lúc cấp. Đăng nhập mới không xóa session cũ. Module khác không đọc bảng này. Filter của app gọi `AuthService` trong cùng process.

Người giữ: P1 Ngọc Anh.

Cổng public `8080` khi chạy riêng module này. Database, schema, user và password nằm trong `resources/application.yml`. URL JDBC trỏ vào database `halozalo`. Pool đặt `search_path` là schema `identity`.

Chạy bằng Docker, gồm PostgreSQL riêng: `docker compose up --build` trong thư mục này. App ở `http://localhost:8080`. `docker compose down` giữ dữ liệu. `docker compose down -v` xóa dữ liệu. Trong container, JDBC trỏ vào service `postgres`. Link xác minh vẫn là `http://localhost:8080` vì người dùng mở trên máy mình. Mail ghi ra log, lấy link bằng `docker compose logs -f identity`. Cổng 5432 trên máy thuộc `infra/docker-compose.yml`, nên Postgres của compose này mở ở `127.0.0.1:5433`. Image chạy bằng user không phải root.

```
services/identity/
  pom.xml
  Dockerfile
  docker-compose.yml
  resources/          cổng, database, và schema.sql
  src/
    app/              khởi động Spring Boot
    account/          dữ liệu tài khoản, gồm hash mật khẩu
    verification/     dữ liệu bản chờ xác minh email
    session/          dữ liệu session
    auth/             JSON request và response
    password/         hash và so khớp bcrypt
    mail/             gửi mail xác minh
    storage/          interface lưu, và implementation PostgreSQL
    event/            phát event đăng nhập trong process
    service/          đăng ký, xác minh email, đăng nhập, kiểm tra session
    http/             nhận HTTP public, đổi lỗi thành status
  test/               cùng các package trên, mỗi nhóm một test
```

## API

`POST /auth/register` trả 202 với `email`. Chưa tạo tài khoản và chưa cấp token. Mail chứa link `GET /auth/verify?token=`, hết hạn sau 120 giây. Bấm link trong hạn thì trả `userId`, `username`, `email` và xóa bản chờ. Hết hạn hoặc token lạ thì 400. `POST /auth/login` chỉ thành công sau khi đã verify, trả 200 với `accessToken`, `tokenType`, `expiresIn`, và `user`. Không trả hash mật khẩu.

`GET /me` cần `Authorization: Bearer`. Trả `userId`, `username`, `email`. Kiểm tra session cho module khác là lời gọi `AuthService.currentUserId` trong process, không có endpoint HTTP nội bộ.

Lỗi: `VALIDATION_ERROR` 400, `CONFLICT` 409, `UNAUTHENTICATED` 401.

Đăng nhập thành công thì `LoggingEventPublisher` ghi event `identity.user.logged_in`, payload chỉ có `userId`. Đăng ký không phát event.

## Schema

`resources/schema.sql` tạo schema `identity` và bảng lúc khởi động. `CREATE TABLE IF NOT EXISTS` nên lần chạy sau không xóa dữ liệu. Database `halozalo` phải tồn tại trước khi app nối vào.

`identity.accounts.user_id`, `identity.verifications.token`, và `identity.sessions.session_id` là khóa chính. `identity.accounts.username`, `identity.accounts.email`, `identity.verifications.username`, và `identity.verifications.email` là unique. `identity.sessions.user_id` tham chiếu `identity.accounts.user_id`. Bảng `verifications` không tham chiếu `accounts`: dòng này tồn tại trước khi có tài khoản. `password_hash` dài 60 ký tự. `created_at` và `expires_at` là `timestamptz`, đến micro giây. `created_at` là lúc Identity tạo dòng. Bảng đã có từ trước được thêm cột này lúc khởi động; dòng cũ nhận `now()` vì không còn mốc tạo thật.

`AuthService` kiểm tra trùng username và email trên tài khoản và trên bản chờ còn hạn trước khi `INSERT`. Khóa unique chặn lần ghi trùng còn lọt qua. `user_id`, `token`, và `session_id` là UUID do service cấp. Bản chờ hết hạn thì lần đăng ký sau xóa dòng cũ. URL public của link nằm ở `identity.public-base-url` trong `application.yml`.
