# Đề xuất đổi hợp đồng messaging

Gửi P1. Code trong service này đã có những phần dưới, nhưng HTTP vẫn giữ đúng `openapi.yaml` hiện tại cho tới khi P1 sửa `doc/contract/`.

## Cần chốt sớm vì đang chạy

1. **Header user từ gateway.** Messaging đọc `X-User-Id`. decisions.md nói gateway gắn `userId` nhưng chưa nói tên header.
2. **Lỗi 403 `FORBIDDEN`.** F-B9 cần chặn người ngoài hội thoại. `ErrorResponse.code` hiện chỉ có `VALIDATION_ERROR`, `UNAUTHENTICATED`, `CONFLICT`. `sendMessage` cần thêm response 403.
3. **Thành viên hội thoại.** Messaging giữ bản sao `memberIds` trong collection `conversations`, tức là chọn đường "nhận event thành viên rồi giữ bản sao" của timeline. Tạm thời hội thoại được mở bằng `POST /internal/conversations`. Khi Identity có nhóm, cần event kiểu `identity.group.member_added` / `member_removed` để đồng bộ bản sao này.
4. **Hội thoại phải tồn tại trước khi gửi.** decisions.md cho phép `conversationId` do client sinh mà không cần tạo trước. Với kiểm tra thành viên, client phải mở hội thoại trước. Cần sửa câu đó, hoặc thêm API tạo hội thoại public.

## Shape `Message` cho bản sau

Đề xuất thêm vào `MessageBase`, đều không bắt buộc nên client cũ không vỡ:

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

Đã có collection `message_reactions` với unique `(messageId, userId, code)` và bộ mã giống emotion. Khi làm E3 cần thêm API thêm/bỏ reaction và một event cập nhật để fan-out tới mọi thiết bị.
