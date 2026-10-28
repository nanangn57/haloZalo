# Việc web và messaging cần sửa

Danh sách để P2 và P3 sửa cho khớp [requirement.md](requirement.md), [timeline.md](timeline.md) và [contract/](contract/). Identity đã có đăng ký, xác minh email, đăng nhập, `GET /me`, filter session và API nhóm trên cổng `8080`. Hai phần kia chưa dùng được output đó, và web hiện không build được.

Đường phải chạy được sau khi sửa:

1. Client gọi `POST /auth/login` trên identity, giữ `accessToken` và `user.userId`.
2. Mọi request sau, kể cả messaging và WebSocket, gửi `Authorization: Bearer` hoặc query `access_token`. Không tự khai `userId`.
3. `POST /groups` trả `groupId`. Hội thoại nhóm dùng đúng id đó. Thành viên lấy từ identity mỗi lần, không giữ bản sao.
4. Gửi tin TEXT hoặc EMOTION vào hội thoại đã mở. Người ngoài nhóm bị 403.

## Chạy chung một database

Ba file Compose không trỏ vào cùng một Postgres, nên nhóm tạo ở identity không nằm trong database mà messaging đang đọc.

| Cách chạy | Postgres | App |
|---|---|---|
| `infra/docker-compose.yml` | cổng `5432`, database `halozalo` | không có process app |
| `services/identity` bằng `mvn` | `localhost:5432` theo `application.yml` | cổng `8080` |
| `services/identity` bằng Docker Compose | Postgres riêng, cổng `5433` | cổng `8080` |
| `services/messaging` bằng Docker Compose hoặc `local-deps.sh` | cổng `5432` | cổng `8082` |

Messaging và `infra/` cùng chiếm cổng `5432`. Chỉ chạy một trong hai. Identity trong Docker dùng cổng `5433`, tách khỏi messaging.

Để cả hai service thấy cùng dữ liệu: một Postgres `halozalo` ở `localhost:5432` (file `infra/docker-compose.yml`), identity và messaging cùng trỏ JDBC vào đó. Không chạy Compose Postgres của từng module song song với `infra/`.

Quyết định đã chốt là một process. Hai cổng `8080` và `8082` chỉ là lúc mỗi module chạy riêng. Khi ghép app, client chỉ gọi một URL.

## Messaging

Người giữ: P2. Chi tiết đề xuất hợp đồng đang nằm ở [services/messaging/contract-proposal.md](../services/messaging/contract-proposal.md). Sửa code ở đây trước. P1 cập nhật `doc/contract/` cho các API đã chạy, không để web gọi path chưa có trong OpenAPI.

### Auth không nhận token của identity

`http.CurrentUser` nhận attribute `userId` nếu có, không thì nhận header `X-User-Id`. Mặc định `messaging.user-header-enabled` là `true`. Login của identity trả session id trong `accessToken`, không trả quyền gửi `X-User-Id`.

- Request messaging phải từ chối khi không có session hợp lệ. Hết hạn hoặc không có dòng thì 401 `UNAUTHENTICATED`, message `Missing or invalid access token`.
- Trong app đã ghép, `MESSAGING_USER_HEADER_ENABLED` phải là `false`. Attribute `userId` do filter của identity gắn sau khi gọi `AuthService.currentUserId`. Tên attribute hai bên đã trùng.
- `GET /ws` phải đọc query `access_token`, đúng [openapi.yaml](contract/openapi.yaml). Handshake hiện không đọc query đó. Trình duyệt không gắn header `Authorization` cho WebSocket, nên client theo hợp đồng không mở được socket.
- `POST /internal/conversations` và `PUT /internal/groups/{groupId}/members` không kiểm token. Bỏ hai path này khi đã gọi trong process. Không đưa chúng vào OpenAPI.

README messaging vẫn ghi filter session chưa có trên `dev`. Filter đã có trong `services/identity`, nhưng chỉ chạy trong process identity. Request vào cổng `8082` không đi qua filter đó.

### Nhóm tạo ở identity không thành hội thoại

`MembershipLookup` chỉ có `isMember(groupId, userId)`. Messaging cần thêm `memberIds(groupId)` để đẩy event, và `groupIdsOf(userId)` để liệt kê hội thoại. Interface phía messaging là `member.GroupMembers`.

Hiện `StandaloneGroupMembers` giữ thành viên trong bộ nhớ, điền bằng `PUT /internal/groups/{groupId}/members`. Tắt process là mất. Tạo nhóm trên identity không gọi sang messaging, nên gửi tin với `groupId` vừa tạo trả 403 `Not a member of this conversation`.

Khi một process:

- Adapter `GroupMembers` gọi identity, không đọc bảng schema `identity`.
- Xoá `StandaloneGroupMembers` và `StandaloneGroupController` trong cùng một thay đổi.
- Hội thoại `GROUP` dùng id nhóm làm `conversationId`. Lúc identity tạo nhóm thì mở hội thoại đó trong process (`MessageService.openConversation`). Thêm hoặc xoá thành viên không cần báo messaging, vì messaging hỏi lại mỗi lần.
- Chat 1-1: `POST /conversations/direct` không kiểm user kia có trong `identity.accounts`. User không tồn tại thì 404, cùng mã identity đang trả khi thêm thành viên.

### Gửi tin lệch câu đang viết trong hợp đồng

`decisions.md` và `POST /conversations/{conversationId}/messages` vẫn nói bản này chưa có API tạo hội thoại, client tự sinh `conversationId`. Code trả 403 nếu hội thoại chưa mở.

Giữ hành vi code (phải mở hội thoại trước, có kiểm tra thành viên). P1 sửa câu đó trong `decisions.md` và OpenAPI, thêm response 403 cho path gửi tin. Web không tự sinh UUID rồi gửi tin.

Các path dưới đây đã chạy trong messaging và chưa có trong OpenAPI. Web chưa gọi cho đến khi P1 ghi vào `doc/contract/`:

- `POST /conversations/direct`, `GET /conversations`, `GET /conversations/{conversationId}`
- `GET /conversations/{conversationId}/messages` (lịch sử và catch-up)
- `PUT /conversations/{conversationId}/read`
- `POST /conversations/{conversationId}/forwards`
- `DELETE /conversations/{conversationId}/messages/{messageId}`
- `PUT`, `DELETE`, `GET` reaction trên một tin

`/ws` đang đẩy thêm `messaging.message.deleted`, `messaging.reaction.added`, `messaging.reaction.removed`, `messaging.read.updated`. `events.md` mới cho `messaging.message.created`. P1 bổ sung event trước khi client xử lý các frame đó. `messaging.read.updated` chỉ tới socket của người vừa đọc.

Shape tin thường vẫn là `Message` hiện tại. `replyTo`, `forwardedFrom`, `status`, `deletedAt` chỉ xuất hiện khi có giá trị. Tin đã xoá không có `body`.

## Web

Người giữ: P3. `npm run build` trong `clients/web` phải qua được trước khi nối API.

### Project không build

`clients/web/ui/App.tsx` import `./pages/ChatPage` và `./pages/ContactPage`. Hai file không có.

`LoginForm` và `LoginPage` truyền `onEmailChange`. `LoginFormProps` trong `clients/web/ui/types/auth.ts` khai báo `onUsernameChange`. TypeScript dừng ở chỗ này.

`clients/web/ui/types/user.ts` dùng `id: number`. Hợp đồng dùng `userId` là UUID. Chưa dùng type này để gọi API.

### Login không gọi identity

`LoginPage` so với `admin@gmail.com` / `123456` rồi `navigate("/contacts")`. Không có request.

`POST /auth/login` nhận `{ login, password }`. `login` là username hoặc email. Password từ 8 đến 72 ký tự, nên `123456` bị 400 nếu gửi thật. Lỗi 401 đọc `error.code` = `UNAUTHENTICATED`.

Sau 200, giữ `accessToken`, `tokenType`, `expiresIn`, `user`. `GET /me` gửi `Authorization: Bearer`. Link Register đang là `href="#"`, chưa gọi `POST /auth/register` rồi `GET /auth/verify?token=`.

### Chưa có client dùng chung

`clients/core` chỉ có README. Web và mobile phải gọi API qua đó: giữ token, gắn Bearer, mở `/ws?access_token=`, gửi lại cùng `clientMsgId` khi retry, catch-up khi socket đóng 1001 hoặc 1011.

Web không tự bịa user, không gửi `X-User-Id`, không gọi `/internal/...`.

## Xong khi

- Một Postgres `halozalo`. Identity và messaging cùng trỏ vào đó. Tạo nhóm trên identity thì dòng nằm ở schema `identity`, hội thoại nhóm nằm ở schema `messaging` với cùng id.
- Login thật trên web. `GET /me` và gửi tin đều từ chối request không token.
- Owner thêm member trên identity. Member gửi được tin trong nhóm. User ngoài nhóm nhận 403.
- `npm run build` của web thành công. `mvn test` của messaging vẫn xanh.
