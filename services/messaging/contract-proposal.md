# Đề xuất đổi hợp đồng messaging

Gửi P1. Code trong service này đã có những phần dưới, nhưng HTTP vẫn giữ đúng `openapi.yaml` hiện tại cho tới khi P1 sửa `doc/contract/`.

## Cần chốt sớm vì đang chạy

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

`events.md` nói WebSocket `/ws` bản v1 chỉ đẩy `messaging.message.created`. Để thiết bị khác thấy xoá và reaction ngay, `/ws` cần đẩy thêm ba event trên. Ba event chỉ phát khi có thay đổi thật, nên client áp dụng thẳng, không cần tự lọc trùng theo nội dung.
