# identity

Tài khoản và session. Schema `identity` trên database PostgreSQL `halozalo` dùng chung. Session id opaque, hết hạn sau 3 ngày, tính từ lúc cấp. Đăng nhập mới không xóa session cũ. Module khác không đọc bảng này. Filter của app gọi `AuthService` trong cùng process.

Người giữ: P1 Ngọc Anh.

Cổng public `8080` khi chạy riêng module này. Database, schema, user và password nằm trong `resources/application.yml`. URL JDBC trỏ vào database `halozalo`. Pool đặt `search_path` là schema `identity`.

```
services/identity/
  pom.xml
  resources/          cổng, database, và schema.sql
  src/
    app/              khởi động Spring Boot
    account/          dữ liệu tài khoản, gồm hash mật khẩu
    session/          dữ liệu session
    auth/             JSON request và response
    password/         hash và so khớp bcrypt
    storage/          interface lưu, và implementation PostgreSQL
    event/            phát event đăng nhập trong process
    service/          đăng ký, đăng nhập, kiểm tra session
    http/             nhận HTTP public, đổi lỗi thành status
  test/               cùng các package trên, mỗi nhóm một test
```

## API

`POST /auth/register` trả 201. `POST /auth/login` trả 200. Cả hai trả `accessToken`, `tokenType`, `expiresIn`, và `user` gồm `userId`, `username`, `email`. Không trả hash mật khẩu.

`GET /me` cần `Authorization: Bearer`. Trả `userId`, `username`, `email`. Kiểm tra session cho module khác là lời gọi `AuthService.currentUserId` trong process, không có endpoint HTTP nội bộ.

Lỗi: `VALIDATION_ERROR` 400, `CONFLICT` 409, `UNAUTHENTICATED` 401.

Đăng nhập thành công thì `LoggingEventPublisher` ghi event `identity.user.logged_in`, payload chỉ có `userId`. Đăng ký không phát event.

## Schema

`resources/schema.sql` tạo schema `identity` và bảng lúc khởi động. `CREATE TABLE IF NOT EXISTS` nên lần chạy sau không xóa dữ liệu. Database `halozalo` phải tồn tại trước khi app nối vào.

`identity.accounts.user_id` và `identity.sessions.session_id` là khóa chính. `identity.accounts.username` và `identity.accounts.email` là unique. `identity.sessions.user_id` tham chiếu `identity.accounts.user_id`. `password_hash` dài 60 ký tự. `expires_at` là `timestamptz`, đến micro giây.

`AuthService` kiểm tra trùng username và email trước khi `INSERT`. Khóa unique chặn lần ghi trùng còn lọt qua. `user_id` và `session_id` là UUID do service cấp.
