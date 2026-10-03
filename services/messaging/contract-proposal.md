# Đề xuất đổi hợp đồng messaging

Gửi P1. Code trong service này đã có những phần dưới, nhưng HTTP vẫn giữ đúng `openapi.yaml` hiện tại cho tới khi P1 sửa `doc/contract/`.

## Cần chốt sớm vì đang chạy

0. **Store của messaging đổi từ Mongo sang PostgreSQL.** `decisions.md`, README gốc và `timeline.md` đang ghi "Mongo cho tin". Timeline nói store của mỗi hộp do cả nhóm chốt qua `doc/contract/`, nên P1 cần sửa các file đó. Lý do: đề messaging ban đầu thiết kế theo bảng quan hệ và JSONB; khoá ngoại giữa tin, trả lời, chuyển tiếp và reaction; và gán `seq` cùng transaction với `INSERT` nên `seq` không còn lỗ. Hợp đồng HTTP và event không đổi. P4 cần Postgres thay Mongo khi deploy, ví dụ RDS; biến môi trường nằm ở `.env.example`.

1. **Header user từ gateway.** Messaging đọc `X-User-Id`. decisions.md nói gateway gắn `userId` nhưng chưa nói tên header.
2. **Lỗi 403 `FORBIDDEN`.** F-B9 cần chặn người ngoài hội thoại. `ErrorResponse.code` hiện chỉ có `VALIDATION_ERROR`, `UNAUTHENTICATED`, `CONFLICT`. `sendMessage` cần thêm response 403.
3. **Thành viên hội thoại.** Messaging giữ bản sao `memberIds` trong collection `conversations`, tức là chọn đường "nhận event thành viên rồi giữ bản sao" của timeline. Tạm thời hội thoại được mở bằng `POST /internal/conversations`. Khi Identity có nhóm, cần event kiểu `identity.group.member_added` / `member_removed` để đồng bộ bản sao này.
4. **Hội thoại phải tồn tại trước khi gửi.** decisions.md cho phép `conversationId` do client sinh mà không cần tạo trước. Với kiểm tra thành viên, client phải mở hội thoại trước. Cần sửa câu đó, hoặc thêm API tạo hội thoại public.

## Endpoint trả lời, chuyển tiếp, xoá

Đã chạy trong service, chưa có trong OpenAPI:

- `SendMessageRequest` thêm `replyTo` (uuid, không bắt buộc). Hiện contract để `additionalProperties: false`.
- `POST /conversations/{conversationId}/forwards`, body `{ messageId, clientMsgId }`, trả `Message`, 201 hoặc 200.
- `DELETE /conversations/{conversationId}/messages/{messageId}`, trả 204.
- Lỗi mới `NOT_FOUND` 404, cho tin không tồn tại.
- Event `messaging.message.deleted`, payload `{ messageId, conversationId, deletedAt }`. Đã phát (log), cần thêm vào `events.md`.

## Catch-up

Đã chạy trong service, chưa có trong OpenAPI. Timeline tuần 5 cần P1 sửa OpenAPI trước #11.

- `GET /conversations/{conversationId}/messages?afterSeq=&limit=`, trả `{ messages: Message[], hasMore: boolean }`.
- `afterSeq` mặc định 0, `limit` mặc định 100, tối đa 200. Sai thì 400.
- Tin đã xoá trả `status: "DELETED"`, `deletedAt`, không có `body`. Vì vậy `body` trong `TextMessage` và `EmotionMessage` phải thành không bắt buộc khi `status` là `DELETED`.
- Chưa có: xoá và reaction trên tin cũ hơn `afterSeq` lúc client offline. Muốn đủ thì cần thêm một bộ đếm thay đổi theo hội thoại, hoặc client tải lại trang tin đang hiện khi kết nối lại.
- Mốc đã đọc (tuần 5) vẫn chưa có. Catch-up không đổi gì ở mốc đó.

## Shape `Message` cho bản sau

Đề xuất thêm vào `MessageBase`, đều không bắt buộc nên client cũ không vỡ. `replyTo` và `forwardedFrom` đã được trả khi có giá trị:

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `replyTo` | uuid, nullable | Tin được trả lời, cùng hội thoại |
| `forwardedFrom` | uuid, nullable | Tin gốc đầu tiên |
| `status` | `SENT` \| `DELETED` \| `ERROR` | Tin đã xoá trả `DELETED`, không trả `body` |
| `deletedAt` | date-time, nullable | |
| `metadata` | object, nullable | Thông tin phụ |

Loại tin media dùng `content` có cấu trúc thay cho `body` chuỗi:

- `IMAGE`: `url`, `thumbnailUrl?`, `width?`, `height?`, `caption?`
- `VIDEO`: `url`, `thumbnailUrl?`, `width?`, `height?`, `duration?`, `caption?`
- `DOCUMENT`: `url`, `fileName`, `mimeType`, `size` (tối đa 10MB)

## Reaction (E3, hạng 2)

Đã chạy trong service, chưa có trong OpenAPI. Collection `message_reactions` có unique `(messageId, userId, code)`, bộ mã giống emotion.

- `PUT /conversations/{conversationId}/messages/{messageId}/reactions/{code}`, trả 204, gọi lại không đổi gì.
- `DELETE` cùng path, trả 204.
- `GET /conversations/{conversationId}/messages/{messageId}/reactions`, trả `{ reactions: [{ userId, code, createdAt }] }`.

Event `messaging.reaction.added` và `messaging.reaction.removed`, payload `{ messageId, conversationId, userId, code }`. Đã phát (log), cần thêm vào `events.md`. Analytics có thể đếm reaction từ hai event này.

## Event mới và WebSocket

`/ws` đã có trong service và đã đẩy cả bốn event: `messaging.message.created`, `messaging.message.deleted`, `messaging.reaction.added`, `messaging.reaction.removed`. `events.md` hiện nói `/ws` chỉ đẩy `messaging.message.created`, cần sửa câu đó. Ba event mới chỉ phát khi có thay đổi thật, nên client áp dụng thẳng.

Cần P1 và gateway:

- Gateway nhận `access_token` ở query `/ws`, hỏi Identity, rồi chuyển handshake sang messaging kèm `X-User-Id`. Gateway phải xoá `X-User-Id` do client tự gửi, ở cả HTTP lẫn `/ws`, nếu không thì ai cũng giả được user.
- Gateway phải chuyển tiếp WebSocket: header `Upgrade`, idle timeout dài hơn 60 giây, và không đóng kết nối có ping 25 giây một lần.

Cần ghi vào hợp đồng cho client-core (P3):

- Mã đóng 1001 khi deploy, 1011 khi socket chết hoặc client đọc không kịp. Gặp mã nào cũng kết nối lại với backoff có jitter rồi gọi catch-up.
- Frame có thể tới hai lần, hoặc tới cả thiết bị vừa gửi. Client bỏ trùng theo `messageId`, xếp theo `seq`.
