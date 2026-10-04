# Quyết định kiến trúc — bản v1

Chốt cho tuần 1, phục vụ [#2](https://github.com/nanangn57/haloZalo/issues/2). Hợp đồng gọi được nằm ở [openapi.yaml](openapi.yaml). Sự kiện nằm ở [events.md](events.md).

Người giữ hợp đồng: P1 (Ngọc Anh). Một process Spring Boot, một instance MySQL, mỗi module một schema. Module không đọc bảng schema khác. Muốn dữ liệu của nhau thì gọi interface trong cùng process, hoặc nhận event phát trong process. Thư mục mỗi người sửa nằm ở [README](../../README.md).

## Bounded context

| Context | Việc sở hữu | Store | Người |
|---|---|---|---|
| Identity & Group | Tài khoản, token, nhóm, thành viên | Schema `identity` | P1 |
| Analytics | Ghi event, đọc bảng tổng hợp | Schema `analytics` | P1 |
| Messaging & Media | Hội thoại, tin, `seq`, file, fan-out | Schema `messaging`. Socket nằm trong bộ nhớ của process | P2 |
| Client | Web và mobile dùng chung một client-core | Cache trên thiết bị | P3 giữ core, P4 gắn mobile |

Ba schema nằm trên cùng một instance MySQL. Không có foreign key xuyên schema. Client chỉ gọi HTTP và WebSocket của app, không gọi vào từng module.

## Đã chốt

- **Kiến trúc:** modular monolith. Một process, một lệnh deploy, ba schema. Module gọi nhau bằng interface. Event phát trong process sau khi transaction nguồn commit. Nhiều process, Mongo và Redis bị bỏ vì hạng 1 trong [requirement.md](../requirement.md) cần lên cloud bằng một lệnh. Microservice cắt nhỏ bị loại vì cùng lý do. Hạng 2 giữ ranh giới bằng schema và interface: module không đọc bảng schema khác.
- **Realtime:** WebSocket trên cùng process. Ghi DB xong mới phát tin. Mục tiêu text: p95 từ lúc gửi đến lúc hiện dưới 500ms.
- **Chống trùng:** at-least-once cộng `clientMsgId`. Cùng người gửi, cùng `clientMsgId` thì trả lại đúng tin đã ghi, không tạo `seq` mới.
- **Thứ tự:** server gán `seq` từng hội thoại, bắt đầu từ 1, không để client tự đánh.
- **Đã đọc:** server giữ mốc đã đọc theo user và hội thoại. Đọc ở một thiết bị thì thiết bị kia hết unread.
- **Emotion hạng 1:** một tin riêng, `type = EMOTION`, `body` là mã icon. Bộ mã: `like`, `love`, `haha`, `wow`, `sad`, `angry`.
- **Auth:** Access token là session id opaque, không phải JWT, không chứa `userId`. Hết hạn sau 3 ngày (259200 giây) kể từ lúc cấp, không gia hạn khi còn dùng. Identity giữ bảng session trong schema `identity`. Module khác không đọc bảng đó. Bản này chưa có API đăng xuất.

Mỗi lần đăng ký hoặc đăng nhập, Identity thêm một dòng (`sessionId`, `userId`, `expiresAt`) và trả `sessionId` trong `accessToken`. Dòng cũ giữ đến khi hết hạn, để web và mobile cùng đăng nhập.

Request có `Authorization: Bearer`, hoặc WebSocket có query `access_token`, thì filter trong cùng process gọi `AuthService`. Identity trả `userId` khi còn dòng và còn hạn. Thiếu session, không có dòng, hoặc hết hạn thì app trả 401 và dừng. Hợp lệ thì gắn `userId` rồi vào handler. Không có API HTTP nội bộ để hỏi session.
- **Thành viên:** Messaging cần biết ai thuộc nhóm thì gọi interface của Identity trong cùng process. Messaging không đọc schema `identity` và không giữ bản sao thành viên.
- **Release:** AWS. Mỗi tuần một URL public. Deploy bằng một lệnh từ repo: một process và một MySQL. P4 giữ lệnh đó.

## Bản này cố ý chưa có

API tạo hội thoại, nhóm, tìm user, lịch sử, catch-up, media, reaction, dashboard, refresh token, FCM.

Cho đến khi có API tạo hội thoại, Messaging nhận `conversationId` do client sinh (UUID) để ghi tin. Shape của `Message` không đổi khi API hội thoại được thêm.
