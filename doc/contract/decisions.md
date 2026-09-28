# Quyết định kiến trúc — bản v1

Chốt cho tuần 1, phục vụ [#2](https://github.com/nanangn57/haloZalo/issues/2). Hợp đồng gọi được nằm ở [openapi.yaml](openapi.yaml). Sự kiện nằm ở [events.md](events.md).

Người giữ hợp đồng: P1 (Ngọc Anh). Service không đọc bảng của service khác. Muốn dữ liệu của nhau thì gọi HTTP hoặc nhận event.

## Bounded context

| Context | Việc sở hữu | Store | Người |
|---|---|---|---|
| Identity & Group | Tài khoản, token, nhóm, thành viên | MySQL | P1 |
| Analytics | Ghi event, đọc bảng tổng hợp | MySQL | P1 |
| Messaging & Media | Hội thoại, tin, `seq`, file, fan-out | Mongo cho tin, Redis cho kết nối realtime | P2 |
| Client | Web và mobile dùng chung một client-core | Cache trên thiết bị | P3 giữ core, P4 gắn mobile |

API gateway là cửa public duy nhất. Client không gọi thẳng vào từng service.

## Đã chốt

- **Realtime:** WebSocket. Ghi DB xong mới phát tin. Mục tiêu text: p95 từ lúc gửi đến lúc hiện dưới 500ms.
- **Chống trùng:** at-least-once cộng `clientMsgId`. Cùng người gửi, cùng `clientMsgId` thì trả lại đúng tin đã ghi, không tạo `seq` mới.
- **Thứ tự:** server gán `seq` từng hội thoại, bắt đầu từ 1, không để client tự đánh.
- **Đã đọc:** server giữ mốc đã đọc theo user và hội thoại. Đọc ở một thiết bị thì thiết bị kia hết unread.
- **Emotion hạng 1:** một tin riêng, `type = EMOTION`, `body` là mã icon. Bộ mã: `like`, `love`, `haha`, `wow`, `sad`, `angry`.
- **Auth:** Identity là nơi duy nhất ký token. Access token JWT HS256, hết hạn sau 3600 giây. Gateway tự kiểm chữ ký, không đọc bảng user.
- **Release:** AWS. Mỗi tuần một URL public. Deploy bằng một lệnh từ repo. P4 giữ lệnh đó.

## Bản này cố ý chưa có

API tạo hội thoại, nhóm, tìm user, lịch sử, catch-up, media, reaction, dashboard, refresh token, FCM.

Cho đến khi có API tạo hội thoại, Messaging nhận `conversationId` do client sinh (UUID) để ghi tin. Shape của `Message` không đổi khi API hội thoại được thêm.
