# Envelope event — bản v1

Mọi event phát trong cùng process, sau khi transaction nguồn commit. Mọi frame WebSocket dùng cùng một phong bì. Analytics chỉ nhận event này, không quét bảng ở schema `messaging`.

## Phong bì

| Field | Kiểu | Ý nghĩa |
|---|---|---|
| `eventId` | UUID | Id của lần phát. Consumer chống trùng bằng field này. |
| `type` | string | Tên sự kiện, dạng `<context>.<tên>`. |
| `occurredAt` | date-time | Lúc việc đã xảy ra trong service nguồn. |
| `producer` | string | `identity`, `messaging`, hoặc `analytics`. |
| `payload` | object | Dữ liệu của `type`. Không nhét cả hàng DB. |

Gửi at-least-once. Consumer xử lý lại cùng `eventId` thì không ghi thêm.

```json
{
  "eventId": "6f1c0e5a-3d2b-4a18-9c0e-1b2a3c4d5e6f",
  "type": "messaging.message.created",
  "occurredAt": "2026-09-28T09:00:00Z",
  "producer": "messaging",
  "payload": {
    "messageId": "8a1b2c3d-4e5f-6789-abcd-ef0123456789",
    "conversationId": "11111111-2222-4333-8444-555555555555",
    "seq": 1,
    "senderId": "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
    "type": "TEXT",
    "body": "hello",
    "clientMsgId": "99999999-8888-4777-8666-555555555555",
    "createdAt": "2026-09-28T09:00:00Z"
  }
}
```

`payload` của `messaging.message.created` là schema `Message` trong [openapi.yaml](openapi.yaml).

## Event có trong bản v1

| type | Khi nào phát | Producer | Payload |
|---|---|---|---|
| `identity.user.logged_in` | Đăng nhập thành công | identity | `{ "userId": "<uuid>" }` |
| `identity.group.created` | Nhóm đã ghi vào schema `identity` | identity | `{ "groupId": "<uuid>", "ownerId": "<uuid>" }` |
| `messaging.message.created` | Tin đã ghi vào schema `messaging` | messaging | `Message` |
| `messaging.message.deleted` | Tin bị xoá lần đầu | messaging | `{ "messageId": "<uuid>", "conversationId": "<uuid>", "deletedAt": "<date-time>" }` |
| `messaging.reaction.added` | Thêm reaction mới | messaging | `{ "messageId": "<uuid>", "conversationId": "<uuid>", "userId": "<uuid>", "code": "<icon>" }` |
| `messaging.reaction.removed` | Gỡ reaction | messaging | cùng payload với `messaging.reaction.added` |
| `messaging.read.updated` | Mốc đã đọc của một user tiến lên | messaging | `{ "conversationId": "<uuid>", "userId": "<uuid>", "lastReadSeq": 1 }` |
| `messaging.media.uploaded` | Upload media đã commit | messaging | `{ "senderId": "<uuid>", "conversationId": "<uuid>", "type": "IMAGE" }` |
| `messaging.typing` | Client báo đang gõ (ephemeral, không persist) | messaging | `{ "conversationId": "<uuid>", "userId": "<uuid>", "typing": true }` |
| `messaging.presence` | Client báo online/offline trong hội thoại (ephemeral) | messaging | `{ "conversationId": "<uuid>", "userId": "<uuid>", "status": "online" }` |

`type` của upload là `IMAGE`, `DOCUMENT`, hoặc `VIDEO`. Analytics đếm envelope upload, không dùng payload của upload.

Analytics đọc `userId`, `ownerId`, `groupId`, `senderId`, và `conversationId` trong payload của login, tạo nhóm, và tin mới để ghi bảng tổng hợp của chính schema `analytics`. Không đọc bảng schema `messaging`.

`code` của reaction nằm trong bộ `like`, `love`, `haha`, `wow`, `sad`, `angry`.

WebSocket `/ws` đẩy `messaging.message.created`, `messaging.message.deleted`, `messaging.reaction.added`, `messaging.reaction.removed`, `messaging.read.updated`, `messaging.typing`, và `messaging.presence`. Frame là nguyên phong bì, không có thêm lớp bọc. `messaging.read.updated` chỉ tới các thiết bị của user đã đọc, không tới thành viên khác.

Client gửi tín hiệu ephemeral trên cùng `/ws` (không qua HTTP):

```json
{ "type": "messaging.typing", "payload": { "conversationId": "<uuid>", "typing": true } }
{ "type": "messaging.presence", "payload": { "conversationId": "<uuid>", "status": "online" } }
```

Server kiểm tra membership rồi fan-out phong bì chuẩn tới thành viên khác của hội thoại. `status` là `online` hoặc `offline`.
