# messaging

Hội thoại, tin, `seq`, file, fan-out. Mongo cho tin, Redis cho kết nối realtime. Media nằm ở service này.

Người giữ: P2 Huy. Service này không đọc bảng của service khác.

Cổng `8082`. Mongo URI nằm trong `resources/application.yml`.

```
services/messaging/
  pom.xml
  resources/          cổng và Mongo URI
  src/
    app/              khởi động Spring Boot
    conversation/     hội thoại DIRECT hoặc GROUP, thành viên, seq cuối
    message/          tin, loại tin, nội dung theo loại, trạng thái, kiểm tra nội dung
    reaction/         reaction của một user trên một tin
    api/              JSON request và response, đúng shape trong OpenAPI
    storage/          interface lưu, và implementation Mongo
    event/            phát messaging.message.created
    service/          mở hội thoại, gửi, trả lời, chuyển tiếp, xoá, reaction
    http/             nhận HTTP, đổi lỗi thành status
  test/               cùng các package trên
```

Chạy: `mvn spring-boot:run` khi có Mongo ở `localhost:27017`. Test không cần Mongo: `mvn test`.

## Mô hình

```
Conversation (DIRECT: đúng 2 người, GROUP: từ 3 người)
├── memberIds
└── Message
    └── MessageReaction
```

Tin không có `receiverId`. Người nhận là thành viên hội thoại. Người gửi phải là thành viên.

`MessageContent` là sealed interface, mỗi `MessageType` một record: `Text`, `Emotion`, `Image`, `Video`, `Document`. `type` là discriminator. Thêm loại mới là thêm một record, một nhánh trong `ContentValidator`, `MongoMessageRepository` và `MessageResponse`. Không có bảng riêng cho từng loại.

`metadata` chỉ giữ thông tin phụ. Người gửi, loại, `replyTo`, `forwardedFrom`, trạng thái và thời gian luôn là field riêng.

`replyTo` trỏ tới một tin trong cùng hội thoại. `forwardedFrom` trỏ tới tin gốc đầu tiên, kể cả khi chuyển tiếp một tin đã chuyển tiếp. Tin chuyển tiếp có `messageId` và `seq` của riêng nó. Chỉ chuyển tiếp được tin của hội thoại mình đang ở.

`status` là `SENT`, `DELETED` hoặc `ERROR`. Không có `read` trên tin: mốc đã đọc giữ theo user và hội thoại, theo decisions.md.

Xoá là xoá mềm: `status = DELETED`, `deletedAt` được ghi, dòng vẫn còn vì tin khác có thể trỏ tới. Chỉ người gửi xoá được.

Reaction dùng bộ mã của emotion. Một user một mã một lần trên một tin.

## Mongo

| Collection | Khoá | Index |
|---|---|---|
| `conversations` | `_id` = conversationId | — |
| `messages` | `_id` = messageId | unique `(senderId, clientMsgId)`, unique `(conversationId, seq)` |
| `message_reactions` | `_id` = reactionId | unique `(messageId, userId, code)` |

Index được tạo lúc khởi động. `seq` tăng bằng `$inc` trên `conversations.lastSeq`, nên mỗi hội thoại bắt đầu từ 1. Hai lần gửi cùng `clientMsgId` chạy đồng thời thì một lần thắng nhờ index unique. Lần thua trả tin đã ghi, nhưng `seq` nó đã lấy bỏ trống.

## API

`POST /conversations/{conversationId}/messages` nhận `SendMessageRequest` của OpenAPI, chỉ `TEXT` và `EMOTION`. Trả `Message` của OpenAPI: 201 khi tin mới, 200 khi trùng `clientMsgId`. Ghi xong mới log `messaging.message.created`.

Body có thể thêm `replyTo`: id một tin trong cùng hội thoại, sai thì 400. Response có `replyTo` khi tin là trả lời, tin thường giữ đúng shape cũ.

`POST /conversations/{conversationId}/forwards` nhận `messageId` (tin muốn chuyển tiếp) và `clientMsgId`. Trả `Message` có `forwardedFrom`, 201 hoặc 200 giống gửi tin. Tin đã xoá thì 400. Người gửi không ở hội thoại của tin gốc thì 403, tin không tồn tại thì 404.

`DELETE /conversations/{conversationId}/messages/{messageId}` xoá mềm, trả 204. Xoá lại vẫn 204. Không phải người gửi thì 403. Tin không thuộc hội thoại trong path thì 404.

`PUT /conversations/{conversationId}/messages/{messageId}/reactions/{code}` thêm reaction, trả 204. Thêm lại cùng mã vẫn 204, không tạo dòng mới. `DELETE` cùng path bỏ reaction, trả 204 kể cả khi chưa có. `GET .../messages/{messageId}/reactions` trả `{ "reactions": [{ userId, code, createdAt }] }`, cũ trước. Mã ngoài bộ emotion thì 400. Tin đã xoá không nhận reaction mới.

User lấy từ header `X-User-Id`, gateway gắn sau khi hỏi Identity. Thiếu header thì 401.

`POST /internal/conversations` nhận `conversationId` (không bắt buộc), `type` và `memberIds`, trả 201. Endpoint này không có trong OpenAPI public. Trùng id thì 409.

Lỗi: `VALIDATION_ERROR` 400, `UNAUTHENTICATED` 401, `FORBIDDEN` 403, `NOT_FOUND` 404, `CONFLICT` 409. Hội thoại không tồn tại cũng trả 403, giống người ngoài, để không dò được id. Đề xuất đổi hợp đồng nằm ở [contract-proposal.md](contract-proposal.md).

## Event

Mọi event dùng phong bì trong `doc/contract/events.md`. Bản này chỉ ghi log, chưa có bus và WebSocket. Event chỉ phát sau khi ghi xong, và chỉ khi có gì đổi: xoá lại, thêm reaction trùng, hoặc bỏ reaction chưa có thì không phát.

| type | Khi nào | Payload |
|---|---|---|
| `messaging.message.created` | Tin mới, kể cả tin chuyển tiếp | `Message` của OpenAPI |
| `messaging.message.deleted` | Tin bị xoá lần đầu | `{ messageId, conversationId, deletedAt }`, không có nội dung |
| `messaging.reaction.added` | Thêm reaction mới | `{ messageId, conversationId, userId, code }` |
| `messaging.reaction.removed` | Bỏ reaction đang có | `{ messageId, conversationId, userId, code }` |
