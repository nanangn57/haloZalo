# messaging

Hội thoại, tin, `seq`, file, fan-out. Schema `messaging` trên cùng database PostgreSQL `halozalo`. Kết nối WebSocket nằm trong bộ nhớ của process. Media nằm ở module này.

Người giữ: P2 Huy. Module này không đọc schema khác. Hỏi thành viên nhóm qua interface của identity.

Cổng `8082` khi chạy riêng. Database và cấu hình WebSocket nằm trong `resources/application.yml`.

```
services/messaging/
  pom.xml
  resources/          cấu hình, và db/migration là bảng của schema messaging
  src/
    app/              khởi động Spring Boot
    conversation/     hội thoại DIRECT hoặc GROUP, seq cuối, cursor của danh sách
    member/           ai thuộc hội thoại: DIRECT lưu ở đây, GROUP hỏi identity
    message/          tin, loại tin, nội dung theo loại, trạng thái, kiểm tra nội dung
    reaction/         reaction của một user trên một tin
    api/              JSON request và response, đúng shape trong OpenAPI
    storage/          interface lưu, và implementation Postgres
    event/            đóng phong bì event, ghi log, đẩy sang realtime
    realtime/         WebSocket /ws, socket theo user, heartbeat, gửi trong process
    service/          hội thoại, gửi, trả lời, chuyển tiếp, xoá, reaction, lịch sử, catch-up, đã đọc
    http/             nhận HTTP, biết ai đang gọi, đổi lỗi thành status
  test/               cùng các package trên
```

## Chạy

Chạy trên máy:

```
./local-deps.sh up          # PostgreSQL 16 ở localhost:5432, database halozalo, giống infra/
mvn spring-boot:run         # chạy từ thư mục này để đọc .env.local
./local-deps.sh psql        # xem bảng của schema messaging
./local-deps.sh down        # tắt, giữ dữ liệu; reset thì xoá luôn dữ liệu
```

Dùng `infra/docker-compose.yml` thay cho `local-deps.sh` cũng được: cùng database, cùng user. Hai cái dùng cùng cổng, chỉ chạy một. Flyway tạo schema `messaging` và các bảng khi messaging khởi động, không cần script nào khác.

`.env.local` lấy mẫu từ `.env.example`. Không điền gì thì dùng đúng Postgres của `local-deps.sh` và `infra/`.

`mvn test` không cần Postgres. Test repository chạy SQL thật trên một Postgres tạm do Testcontainers bật, dựng giống app: database `halozalo`, bảng trong schema `messaging`. Máy không có Docker thì test đó tự bỏ qua.

Chạy cả service bằng Docker, gồm Postgres riêng: `docker compose up --build` trong thư mục này. Service ở `http://localhost:8082`, WebSocket ở `ws://localhost:8082/ws`. `docker compose down -v` xoá luôn dữ liệu. Compose không đọc `.env.local`, vì trong container `localhost` không phải Postgres. Muốn dùng Postgres trên cloud thì đặt biến trong shell trước khi chạy compose. Image không chứa `.env.local` và chạy bằng user không phải root.

`GET /actuator/health` trả `UP` khi app và Postgres chạy, `DOWN` 503 khi mất Postgres.

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `POSTGRES_URL` | `jdbc:postgresql://localhost:5432/halozalo` | JDBC URL của database chung. Cloud thường thêm `?sslmode=require` |
| `POSTGRES_USER`, `POSTGRES_PASSWORD` | `halozalo`, `halozalo` | User và password của database |
| `MESSAGING_USER_HEADER_ENABLED` | `true` | Nhận `X-User-Id` khi messaging chạy riêng. Trong app phải là `false`, xem mục Ai đang gọi |

`.env.local` không được commit. Biến môi trường thật, ví dụ trên AWS, thắng giá trị trong file. File đọc kiểu properties: không đặt dấu nháy, dấu `\` trong giá trị phải viết `\\`.

## Ai đang gọi

`http.CurrentUser` là nơi duy nhất trả lời câu hỏi này, cho cả HTTP lẫn handshake `/ws`:

1. Request attribute `userId`. Filter session của app gắn attribute này sau khi `AuthService` kiểm access token. Filter này của P1, chưa có trên `dev`.
2. Nếu không có attribute và `MESSAGING_USER_HEADER_ENABLED=true` thì đọc header `X-User-Id`. Chỉ để chạy messaging riêng lúc chưa có app.

Không có user thì 401, `/ws` không nâng cấp. Trong app phải đặt `MESSAGING_USER_HEADER_ENABLED=false`, nếu không client nào cũng tự xưng là user khác được. User không phải UUID thì coi như không có.

## Thành viên

`member.ConversationMembers` trả lời ai thuộc một hội thoại:

- DIRECT: hai người được lưu trong bảng `direct_members`. Messaging sở hữu hội thoại 1-1 nên giữ hai người đó.
- GROUP: `conversationId` là id nhóm trong identity. Thành viên hỏi qua interface `member.GroupMembers` ngay lúc cần, không lưu bản sao. Người bị owner xoá khỏi nhóm thì lần gọi tiếp theo đã bị 403, không nhận event nữa, và hội thoại biến khỏi danh sách của họ.

`GroupMembers` cần ba việc: `isMember`, `memberIds` (để đẩy event cho cả nhóm), `groupIdsOf` (để lấy danh sách hội thoại). Trong app, P1 gắn interface này vào `MembershipLookup` của identity.

Tạm thời: `StandaloneGroupMembers` giữ thành viên nhóm trong bộ nhớ, để messaging chạy riêng được. Đặt thành viên bằng `PUT /internal/groups/{groupId}/members` với `{ "memberIds": [...] }`. Không ghi vào database, mất khi tắt process. Xoá `StandaloneGroupMembers` và `StandaloneGroupController` cùng lúc thêm adapter thật.

## Mô hình

```
Conversation (DIRECT: đúng 2 người, lưu ở messaging; GROUP: nhóm của identity)
└── Message
    └── MessageReaction
```

Tin không có `receiverId`. Người nhận là thành viên hội thoại. Người gửi phải là thành viên.

`MessageContent` là sealed interface, mỗi `MessageType` một record: `Text`, `Emotion`, `Image`, `Video`, `Document`. `type` là discriminator. Thêm loại mới là thêm một record, một nhánh trong `ContentValidator`, `MessageContentJson` và `MessageResponse`, và thêm loại vào CHECK của cột `messages.type` bằng một migration mới. Không có bảng riêng cho từng loại.

`metadata` chỉ giữ thông tin phụ. Người gửi, loại, `replyTo`, `forwardedFrom`, trạng thái và thời gian luôn là field riêng.

`replyTo` trỏ tới một tin trong cùng hội thoại. `forwardedFrom` trỏ tới tin gốc đầu tiên, kể cả khi chuyển tiếp một tin đã chuyển tiếp. Tin chuyển tiếp có `messageId` và `seq` của riêng nó. Chỉ chuyển tiếp được tin của hội thoại mình đang ở.

`status` là `SENT`, `DELETED` hoặc `ERROR`. Không có `read` trên tin: mốc đã đọc giữ theo user và hội thoại, theo decisions.md.

Xoá là xoá mềm: `status = DELETED`, `deletedAt` được ghi, dòng vẫn còn vì tin khác có thể trỏ tới. Chỉ người gửi xoá được.

Reaction dùng bộ mã của emotion. Một user một mã một lần trên một tin.

## PostgreSQL

Bảng nằm ở schema `messaging` của database `halozalo`. Kết nối đặt `search_path` là `messaging`, nên SQL không ghi tên schema và không đọc schema khác. Không có khoá ngoại sang schema khác: user id và group id chỉ là UUID.

Bảng tạo bởi `resources/db/migration`. Flyway chạy lúc khởi động, tạo schema nếu chưa có, giữ lịch sử ở `messaging.flyway_schema_history`, mỗi file một lần theo số version. Đổi bảng thì thêm file `V2__...sql`, không sửa file đã chạy.

| Bảng | Khoá | Ràng buộc |
|---|---|---|
| `conversations` | `id` | `type` là `DIRECT` hoặc `GROUP`; `last_seq` là `seq` của tin mới nhất; `last_message_at` cho thứ tự danh sách; unique `direct_key` chỉ cho DIRECT |
| `direct_members` | `(conversation_id, user_id)` | hai người của một hội thoại DIRECT; index `user_id` cho danh sách hội thoại |
| `messages` | `id` | unique `(sender_id, client_msg_id)`, unique `(conversation_id, seq)`; `content` và `metadata` là JSONB; `reply_to`, `forwarded_from` là khoá ngoại tới `messages` |
| `message_reactions` | `id` | unique `(message_id, user_id, code)`; `message_id` là khoá ngoại tới `messages` |
| `read_markers` | `(conversation_id, user_id)` | `last_read_seq`: `seq` cao nhất user đã đọc trong hội thoại |

Gửi tin là một transaction: tăng `last_seq`, ghi `last_message_at`, rồi `INSERT` tin. `UPDATE` khoá dòng hội thoại, nên tin trong một hội thoại nhận `seq` lần lượt, bắt đầu từ 1. Trùng `clientMsgId` thì rollback cả transaction, nên `seq` không bao giờ có lỗ. Test đã chạy 100 lần gửi song song và 10 lần retry song song trên Postgres thật.

Mọi id là UUID và được trả về chữ thường. Gửi chữ hoa vẫn được hiểu là cùng một id.

## API

`POST /conversations/{conversationId}/messages` nhận `SendMessageRequest` của OpenAPI, chỉ `TEXT` và `EMOTION`. Trả `Message` của OpenAPI: 201 khi tin mới, 200 khi trùng `clientMsgId`. Ghi xong mới phát `messaging.message.created`.

Body có thể thêm `replyTo`: id một tin trong cùng hội thoại, sai thì 400. Response có `replyTo` khi tin là trả lời, tin thường giữ đúng shape cũ.

### Hội thoại

`POST /conversations/direct` nhận `{ userId }` của người kia, trả hội thoại 1-1 giữa hai người: 201 khi vừa tạo, 200 khi đã có. Mỗi cặp user chỉ có một hội thoại DIRECT, ai mở trước cũng vậy, kể cả khi hai người mở cùng lúc: ràng buộc unique `direct_key` trong Postgres giữ điều đó. Mở với chính mình thì 400. Messaging không hỏi identity xem `userId` có tồn tại không.

`GET /conversations?cursor=&limit=` trả `{ conversations, nextCursor }`: hội thoại 1-1 của người gọi và hội thoại của các nhóm identity nói họ đang ở, hoạt động gần nhất trước, mỗi hội thoại kèm `lastMessage`. `limit` mặc định 50, tối đa 100. Còn trang sau thì gửi lại `nextCursor` làm `cursor`; trang cuối có `nextCursor: null`.

`GET /conversations/{conversationId}` trả một hội thoại, chỉ cho thành viên.

Mỗi hội thoại có `conversationId`, `type`, `memberIds`, `lastSeq`, `createdAt`, `lastReadSeq`, `unreadCount`, và khi đã có tin thì `lastMessageAt`, `lastMessage` (shape `Message`). Với GROUP, `memberIds` là thành viên identity trả về lúc đó. Tin cuối đã xoá thì hiện như ô trống. `lastReadSeq` và `unreadCount` là của người gọi, xem mục Đã đọc.

Tin tới một hội thoại mà client chưa biết (người kia vừa mở rồi gửi) thì client gọi `GET /conversations/{conversationId}` để lấy thông tin. Không có event riêng khi mở hội thoại.

`POST /internal/conversations` không có trong OpenAPI public. DIRECT: `{ conversationId?, type: "DIRECT", memberIds: [a, b] }`. GROUP: `{ conversationId: <id nhóm>, type: "GROUP" }`, không gửi `memberIds` vì thành viên thuộc identity; gửi thì 400. Trả 201, trùng id hoặc trùng cặp thì 409. Khi identity tạo nhóm, app gọi việc này trong process.

### Đã đọc

Mốc đã đọc giữ theo user và hội thoại, không nằm trên tin, theo decisions.md. Mọi thiết bị của một user dùng chung một mốc.

`PUT /conversations/{conversationId}/read` nhận `{ seq }`: user đã thấy tới tin có `seq` đó. Trả `{ conversationId, lastReadSeq, unreadCount }`, 200. Chỉ thành viên gọi được. `seq` thiếu hoặc âm thì 400.

- Mốc chỉ tiến, không lùi: thiết bị cũ gửi `seq` nhỏ hơn thì mốc giữ nguyên, vẫn trả 200. Hai thiết bị gửi cùng lúc thì mốc dừng ở `seq` lớn nhất.
- `seq` lớn hơn tin mới nhất thì tính bằng tin mới nhất.
- Gửi tin cũng là đã đọc tới tin của mình.
- Mốc tiến thì phát `messaging.read.updated` tới các thiết bị của chính user đó, không tới thành viên khác. Thiết bị kia nhận event là tắt unread, không cần hỏi lại.

`unreadCount` là số tin của người khác có `seq` lớn hơn mốc, không tính tin đã xoá. Có trong `GET /conversations` (mọi hội thoại của trang, một query), `GET /conversations/{conversationId}` và `POST /conversations/direct`.

### Tin trong hội thoại

`GET /conversations/{conversationId}/messages` trả `{ messages, hasMore }`, `messages` là `Message` của OpenAPI, luôn xếp theo `seq` tăng dần. `limit` mặc định 100, tối đa 200. Chỉ thành viên gọi được. Có hai cách dùng:

- Lịch sử: không có `afterSeq`. Trả trang mới nhất. Muốn xem cũ hơn thì gửi `beforeSeq` là `seq` nhỏ nhất vừa nhận. `hasMore` nghĩa là còn tin cũ hơn.
- Catch-up: `afterSeq` là `seq` cuối client đã có. Trả tin cũ nhất sau mốc đó. `hasMore` nghĩa là còn tin mới hơn, gọi tiếp với `seq` cuối vừa nhận.

Gửi cả `afterSeq` và `beforeSeq` thì 400.

Tin đã xoá vẫn có trong cả hai cách, với `status: "DELETED"` và `deletedAt`, không có `body`. Nhờ vậy client thấy đủ mọi `seq`, không chờ một tin không bao giờ tới. `hasMore: false` nghĩa là đã đủ. Query đi theo ràng buộc unique `(conversation_id, seq)`.

Catch-up chỉ trả tin mới hơn `afterSeq`. Tin cũ hơn bị xoá, hoặc reaction đổi trên tin cũ, khi client đang offline thì catch-up chưa báo.

`POST /conversations/{conversationId}/forwards` nhận `messageId` (tin muốn chuyển tiếp) và `clientMsgId`. Trả `Message` có `forwardedFrom`, 201 hoặc 200 giống gửi tin. Tin đã xoá thì 400. Người gửi không ở hội thoại của tin gốc thì 403, tin không tồn tại thì 404.

`DELETE /conversations/{conversationId}/messages/{messageId}` xoá mềm, trả 204. Xoá lại vẫn 204. Không phải người gửi thì 403. Tin không thuộc hội thoại trong path thì 404.

`PUT /conversations/{conversationId}/messages/{messageId}/reactions/{code}` thêm reaction, trả 204. Thêm lại cùng mã vẫn 204, không tạo dòng mới. `DELETE` cùng path bỏ reaction, trả 204 kể cả khi chưa có. `GET .../messages/{messageId}/reactions` trả `{ "reactions": [{ userId, code, createdAt }] }`, cũ trước. Mã ngoài bộ emotion thì 400. Tin đã xoá không nhận reaction mới.

Lỗi: `VALIDATION_ERROR` 400, `UNAUTHENTICATED` 401, `FORBIDDEN` 403, `NOT_FOUND` 404, `CONFLICT` 409. Hội thoại không tồn tại cũng trả 403, giống người ngoài, để không dò được id. Đề xuất đổi hợp đồng nằm ở [contract-proposal.md](contract-proposal.md).

## Event

Mọi event dùng phong bì trong `doc/contract/events.md`. Event phát trong process sau khi transaction ghi tin commit, được ghi log và đẩy qua `/ws`. Event chỉ phát khi có gì đổi: xoá lại, thêm reaction trùng, hoặc bỏ reaction chưa có thì không phát.

| type | Khi nào | Payload |
|---|---|---|
| `messaging.message.created` | Tin mới, kể cả tin chuyển tiếp | `Message` của OpenAPI |
| `messaging.message.deleted` | Tin bị xoá lần đầu | `{ messageId, conversationId, deletedAt }`, không có nội dung |
| `messaging.reaction.added` | Thêm reaction mới | `{ messageId, conversationId, userId, code }` |
| `messaging.reaction.removed` | Bỏ reaction đang có | `{ messageId, conversationId, userId, code }` |
| `messaging.read.updated` | Mốc đã đọc của user tiến lên. Chỉ gửi tới thiết bị của user đó | `{ conversationId, userId, lastReadSeq }` |

## WebSocket

`GET /ws` nâng cấp lên WebSocket. User lấy giống HTTP, qua `CurrentUser`; trong app, filter session kiểm query `access_token` trước khi nâng cấp. Không có user thì 401, không nâng cấp. Mỗi frame là nguyên phong bì, không bọc thêm. Không dùng STOMP hay SockJS.

Đi một chiều: server đẩy, client gửi tin qua HTTP vì `clientMsgId` làm retry an toàn. Client gửi gì lên cũng chỉ được tính là còn sống. Frame client gửi lên tối đa 8KB.

Đường đi của một event:

1. Transaction ghi Postgres commit xong, `EnvelopeEventPublisher` đóng phong bì và hỏi `ConversationMembers` ai thuộc hội thoại. Với GROUP là thành viên identity lúc đó. `messaging.read.updated` chỉ có một người nhận: chính user đã đọc.
2. Gửi thẳng cho socket của những người đó, vì mọi socket nằm trong cùng process.

Người gửi cũng là thành viên, nên thiết bị khác của người gửi nhận được tin. Thiết bị vừa gửi cũng nhận lại, client bỏ trùng theo `messageId` hoặc `clientMsgId`.

| Tình huống | Cách xử lý |
|---|---|
| Một user mở nhiều thiết bị, nhiều tab | Mỗi user giữ nhiều socket, mọi socket đều nhận |
| Mạng di động rớt không gửi close frame | Server ping mỗi 25 giây. Không thấy pong hay frame nào trong 60 giây thì đóng, mã 1011 |
| Client đọc chậm | Mỗi socket gửi tuần tự, tối đa 10 giây và 512KB chờ. Quá thì đóng, mã 1011 |
| Deploy hoặc tắt process | `server.shutdown: graceful`, đóng mọi socket bằng 1001 trước khi tắt web server |
| Gửi frame lỗi | Không làm hỏng request: tin đã ghi, trả 201 bình thường |
| Client lỡ frame | Frame không được lưu. Client thấy lỗ `seq` hoặc vừa kết nối lại thì gọi catch-up với `seq` cuối đã có |

Nhận mã đóng 1001 hoặc 1011, hoặc mất kết nối, thì client nên kết nối lại với backoff có jitter, rồi gọi catch-up. Ping và pong là frame điều khiển: trình duyệt và `java.net.http` tự trả pong, client không cần code thêm.

Socket nằm trong bộ nhớ của một process, đúng như decisions.md. Chạy hai process cùng lúc thì socket ở process này không nhận event của process kia; muốn vậy phải thêm một bus giữa các process.
