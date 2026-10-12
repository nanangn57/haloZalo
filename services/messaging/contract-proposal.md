# Đề xuất đổi hợp đồng messaging

Gửi P1. Code trong module này đã có những phần dưới, nhưng HTTP vẫn giữ đúng `openapi.yaml` hiện tại cho tới khi P1 sửa `doc/contract/`.

Messaging đã theo decisions.md mới: một process, schema `messaging` trên database PostgreSQL `halozalo`, không Mongo, không Redis, socket trong bộ nhớ, thành viên nhóm hỏi identity và không giữ bản sao.

## Cần P1 khi ghép vào app

1. **Attribute user.** Messaging đọc request attribute `userId` (xem `http.CurrentUser`). Filter session của app cần gắn đúng tên đó, cho cả HTTP lẫn handshake `/ws` có query `access_token`. App phải đặt `messaging.user-header-enabled=false`, nếu không client tự gửi `X-User-Id` là giả được user.
2. **`MembershipLookup` cần thêm hai hàm.** Messaging dùng interface `member.GroupMembers`: `isMember(groupId, userId)`, `memberIds(groupId)` để đẩy event cho cả nhóm, và `groupIdsOf(userId)` để lấy danh sách hội thoại. `MembershipLookup` hiện chỉ có `isMember`. Khi có đủ, app gắn một adapter `GroupMembers` gọi `MembershipLookup`, rồi xoá `StandaloneGroupMembers` và `StandaloneGroupController` của messaging.
3. **Mở hội thoại nhóm khi identity tạo nhóm.** Hội thoại GROUP dùng id nhóm làm `conversationId`. Khi identity tạo nhóm, app gọi messaging mở hội thoại đó trong process (hiện là `MessageService.openConversation(groupId, GROUP, null)`), hoặc phát event `identity.group.created` cho messaging nghe. Thêm và xoá thành viên không cần báo messaging: messaging hỏi identity mỗi lần.
4. **Lỗi 403 `FORBIDDEN`.** F-B9 cần chặn người ngoài hội thoại. `ErrorResponse.code` hiện chỉ có `VALIDATION_ERROR`, `UNAUTHENTICATED`, `CONFLICT`. `sendMessage` cần thêm response 403.
5. **Hội thoại phải tồn tại trước khi gửi.** decisions.md cho phép `conversationId` do client sinh mà không cần tạo trước. Với kiểm tra thành viên, client phải mở hội thoại trước, bằng `POST /conversations/direct` (đã có, xem mục Hội thoại). Cần sửa câu đó trong decisions.md.
6. **`infra/postgres/03-dummy.sql` hỏng.** Bảng `identity.accounts` giờ có `created_at NOT NULL`, nhưng file mẫu không chèn cột đó, nên container Postgres của `infra/` dừng lúc khởi tạo. Việc của P4 hoặc P1, không thuộc messaging. Messaging không cần file này: Flyway tự tạo schema `messaging`.

## Endpoint trả lời, chuyển tiếp, xoá

Đã chạy trong module, chưa có trong OpenAPI:

- `SendMessageRequest` thêm `replyTo` (uuid, không bắt buộc). Hiện contract để `additionalProperties: false`.
- `POST /conversations/{conversationId}/forwards`, body `{ messageId, clientMsgId }`, trả `Message`, 201 hoặc 200.
- `DELETE /conversations/{conversationId}/messages/{messageId}`, trả 204.
- Lỗi mới `NOT_FOUND` 404, cho tin không tồn tại.
- Event `messaging.message.deleted`, payload `{ messageId, conversationId, deletedAt }`. Đã phát (log), cần thêm vào `events.md`.

## Hội thoại

Đã chạy trong module, chưa có trong OpenAPI. Web và mobile cần trước khi mở được chat (#9, tuần 3).

- `POST /conversations/direct`, body `{ userId }`, trả `Conversation`: 201 khi tạo, 200 khi đã có. Một cặp user một hội thoại DIRECT. Với chính mình thì 400.
- `GET /conversations?cursor=&limit=`, trả `{ conversations: Conversation[], nextCursor: string | null }`, hoạt động gần nhất trước. `limit` mặc định 50, tối đa 100.
- `GET /conversations/{conversationId}`, trả `Conversation`, 403 với người ngoài.
- Schema `Conversation`: `conversationId`, `type` (`DIRECT` | `GROUP`), `memberIds`, `lastSeq`, `createdAt`, và không bắt buộc `lastMessageAt`, `lastMessage` (`Message`).
- Tạo nhóm vẫn là việc của identity, xem mục 3 ở trên. Với GROUP, `memberIds` là thành viên identity trả về lúc gọi.
- Messaging không kiểm tra `userId` có tồn tại. Nếu cần, app hoặc client kiểm với identity trước.

## Lịch sử và catch-up

Đã chạy trong module, chưa có trong OpenAPI. Timeline tuần 5 cần P1 sửa OpenAPI trước #11.

- `GET /conversations/{conversationId}/messages?afterSeq=&beforeSeq=&limit=`, trả `{ messages: Message[], hasMore: boolean }`, luôn theo `seq` tăng dần.
- Không có `afterSeq`: lịch sử, trang mới nhất, `beforeSeq` để xem cũ hơn. Có `afterSeq`: catch-up, tin sau mốc đó. Cả hai cùng lúc thì 400.
- `limit` mặc định 100, tối đa 200. Sai thì 400.
- Tin đã xoá trả `status: "DELETED"`, `deletedAt`, không có `body`. Vì vậy `body` trong `TextMessage` và `EmotionMessage` phải thành không bắt buộc khi `status` là `DELETED`.
- Chưa có: xoá và reaction trên tin cũ hơn `afterSeq` lúc client offline. Muốn đủ thì cần thêm một bộ đếm thay đổi theo hội thoại, hoặc client tải lại trang tin đang hiện khi kết nối lại.
- Mốc đã đọc (tuần 5) vẫn chưa có. Catch-up không đổi gì ở mốc đó.

## Đã đọc và unread (tuần 5, #11)

Đã chạy trong module, chưa có trong OpenAPI và `events.md`. Mốc không nằm trên tin, đúng như timeline tuần 5 yêu cầu.

- `PUT /conversations/{conversationId}/read`, body `{ seq: integer >= 0 }`, trả `{ conversationId, lastReadSeq, unreadCount }`, 200. Mốc chỉ tiến; `seq` quá tin mới nhất thì lấy tin mới nhất. 403 với người ngoài, 400 khi thiếu hoặc âm.
- Schema `Conversation` thêm `lastReadSeq` và `unreadCount` (của người gọi). `unreadCount` không tính tin của chính mình và tin đã xoá.
- Gửi tin tự đánh dấu đã đọc tới tin của mình.
- Event `messaging.read.updated`, payload `{ conversationId, userId, lastReadSeq }`. Chỉ đẩy tới thiết bị của user đó qua `/ws`, không tới thành viên khác, vì yêu cầu là đồng bộ giữa thiết bị của một người, không phải "đã xem" cho người khác. Nếu sau này muốn hiện "đã xem" thì cần chốt riêng.
- Chưa có: tổng unread của mọi hội thoại cho badge của app. Client cộng từ danh sách, hoặc thêm một endpoint nếu cần.

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

Đã chạy trong module, chưa có trong OpenAPI. Bảng `message_reactions` có unique `(message_id, user_id, code)`, bộ mã giống emotion.

- `PUT /conversations/{conversationId}/messages/{messageId}/reactions/{code}`, trả 204, gọi lại không đổi gì.
- `DELETE` cùng path, trả 204.
- `GET /conversations/{conversationId}/messages/{messageId}/reactions`, trả `{ reactions: [{ userId, code, createdAt }] }`.

Event `messaging.reaction.added` và `messaging.reaction.removed`, payload `{ messageId, conversationId, userId, code }`. Đã phát (log), cần thêm vào `events.md`. Analytics có thể đếm reaction từ hai event này.

## Event mới và WebSocket

`/ws` đã có trong module và đã đẩy cả bốn event: `messaging.message.created`, `messaging.message.deleted`, `messaging.reaction.added`, `messaging.reaction.removed`. `events.md` hiện nói `/ws` chỉ đẩy `messaging.message.created`, cần sửa câu đó. Ba event mới chỉ phát khi có thay đổi thật, nên client áp dụng thẳng.

Không còn gateway riêng: filter session của app kiểm `access_token` trước handshake `/ws`, xem mục 1 ở trên. Load balancer trên cloud cần idle timeout dài hơn 60 giây, vì server ping 25 giây một lần.

Cần ghi vào hợp đồng cho client-core (P3):

- Mã đóng 1001 khi deploy, 1011 khi socket chết hoặc client đọc không kịp. Gặp mã nào cũng kết nối lại với backoff có jitter rồi gọi catch-up.
- Frame có thể tới hai lần, hoặc tới cả thiết bị vừa gửi. Client bỏ trùng theo `messageId`, xếp theo `seq`.
