# messaging

Hội thoại, tin, `seq`, file, fan-out. Schema `messaging` trên cùng instance MySQL. Kết nối WebSocket nằm trong bộ nhớ của process. Media nằm ở module này.

Người giữ: P2 Huy. Module này không đọc schema khác. Hỏi thành viên nhóm qua interface của identity.

Cổng `8082`. Mongo, Redis và cấu hình WebSocket nằm trong `resources/application.yml`.

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
    event/            đóng phong bì event, ghi log, đẩy sang realtime
    realtime/         WebSocket /ws, socket theo user, heartbeat, fan-out qua Redis
    service/          mở hội thoại, gửi, trả lời, chuyển tiếp, xoá, reaction, catch-up
    http/             nhận HTTP, đổi lỗi thành status
  test/               cùng các package trên
```

Chạy: chép `.env.example` thành `.env.local` trong thư mục này rồi điền giá trị, sau đó `mvn spring-boot:run` từ thư mục này. Không điền gì thì dùng Mongo ở `localhost:27017` và Redis ở `localhost:6379`. Không có Redis thì đặt `MESSAGING_REALTIME_BUS=local`, chỉ dùng cho một instance. Test không cần Mongo hay Redis: `mvn test`.

Chạy bằng Docker, gồm Mongo và Redis riêng: `docker compose up --build` trong thư mục này. Service ở `http://localhost:8082`, WebSocket ở `ws://localhost:8082/ws`. `docker compose down -v` xoá luôn dữ liệu Mongo. Compose không đọc `.env.local`, vì trong container `localhost` không phải Mongo hay Redis. Muốn dùng Mongo hay Redis trên cloud thì đặt biến trong shell trước khi chạy compose. Image không chứa `.env.local` và chạy bằng user không phải root.

`GET /actuator/health` trả `UP` khi app và Mongo chạy. Redis không tính vào health, vì Redis tắt thì gửi tin vẫn được.

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `MONGODB_URI` | `mongodb://localhost:27017/messaging` | Chuỗi kết nối Mongo, gồm user, password và tên database |
| `REDIS_HOST`, `REDIS_PORT` | `localhost`, `6379` | Redis cho fan-out realtime |
| `REDIS_USERNAME`, `REDIS_PASSWORD` | trống | Để trống khi Redis không có auth |
| `REDIS_SSL_ENABLED` | `false` | `true` khi Redis bật TLS, ví dụ ElastiCache |
| `MESSAGING_REALTIME_BUS` | `redis` | `local` khi chạy một instance không có Redis |

`.env.local` không được commit. Biến môi trường thật, ví dụ trên AWS, thắng giá trị trong file. File đọc kiểu properties: không đặt dấu nháy, dấu `\` trong giá trị phải viết `\\`.

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

`GET /conversations/{conversationId}/messages?afterSeq=N&limit=M` là catch-up. Trả `{ messages, hasMore }`, `messages` là `Message` của OpenAPI, xếp theo `seq` tăng dần, chỉ gồm tin có `seq > afterSeq`. `afterSeq` mặc định 0, `limit` mặc định 100, tối đa 200. Còn `hasMore` thì gọi tiếp với `seq` cuối vừa nhận. Chỉ thành viên gọi được.

Tin đã xoá vẫn có trong catch-up, với `status: "DELETED"` và `deletedAt`, không có `body`. Nhờ vậy client thấy đủ mọi `seq`, không chờ một tin không bao giờ tới. `hasMore: false` nghĩa là đã đủ, kể cả khi `seq` có lỗ do hai lần retry chạy đồng thời. Query đi theo index `(conversationId, seq)`.

Catch-up chỉ trả tin mới hơn `afterSeq`. Tin cũ hơn bị xoá, hoặc reaction đổi trên tin cũ, khi client đang offline thì catch-up chưa báo.

Body có thể thêm `replyTo`: id một tin trong cùng hội thoại, sai thì 400. Response có `replyTo` khi tin là trả lời, tin thường giữ đúng shape cũ.

`POST /conversations/{conversationId}/forwards` nhận `messageId` (tin muốn chuyển tiếp) và `clientMsgId`. Trả `Message` có `forwardedFrom`, 201 hoặc 200 giống gửi tin. Tin đã xoá thì 400. Người gửi không ở hội thoại của tin gốc thì 403, tin không tồn tại thì 404.

`DELETE /conversations/{conversationId}/messages/{messageId}` xoá mềm, trả 204. Xoá lại vẫn 204. Không phải người gửi thì 403. Tin không thuộc hội thoại trong path thì 404.

`PUT /conversations/{conversationId}/messages/{messageId}/reactions/{code}` thêm reaction, trả 204. Thêm lại cùng mã vẫn 204, không tạo dòng mới. `DELETE` cùng path bỏ reaction, trả 204 kể cả khi chưa có. `GET .../messages/{messageId}/reactions` trả `{ "reactions": [{ userId, code, createdAt }] }`, cũ trước. Mã ngoài bộ emotion thì 400. Tin đã xoá không nhận reaction mới.

User lấy từ header `X-User-Id`, gateway gắn sau khi hỏi Identity. Thiếu header thì 401.

`POST /internal/conversations` nhận `conversationId` (không bắt buộc), `type` và `memberIds`, trả 201. Endpoint này không có trong OpenAPI public. Trùng id thì 409.

Lỗi: `VALIDATION_ERROR` 400, `UNAUTHENTICATED` 401, `FORBIDDEN` 403, `NOT_FOUND` 404, `CONFLICT` 409. Hội thoại không tồn tại cũng trả 403, giống người ngoài, để không dò được id. Đề xuất đổi hợp đồng nằm ở [contract-proposal.md](contract-proposal.md).

## Event

Mọi event dùng phong bì trong `doc/contract/events.md`. Event được ghi log (chỗ của event bus sau này) và đẩy qua `/ws`. Event chỉ phát sau khi ghi xong, và chỉ khi có gì đổi: xoá lại, thêm reaction trùng, hoặc bỏ reaction chưa có thì không phát.

| type | Khi nào | Payload |
|---|---|---|
| `messaging.message.created` | Tin mới, kể cả tin chuyển tiếp | `Message` của OpenAPI |
| `messaging.message.deleted` | Tin bị xoá lần đầu | `{ messageId, conversationId, deletedAt }`, không có nội dung |
| `messaging.reaction.added` | Thêm reaction mới | `{ messageId, conversationId, userId, code }` |
| `messaging.reaction.removed` | Bỏ reaction đang có | `{ messageId, conversationId, userId, code }` |

## WebSocket

`GET /ws` nâng cấp lên WebSocket. Gateway kiểm `access_token` với Identity rồi gắn `X-User-Id`, giống HTTP. Thiếu header thì 401, không nâng cấp. Mỗi frame là nguyên phong bì, không bọc thêm. Không dùng STOMP hay SockJS.

Đi một chiều: server đẩy, client gửi tin qua HTTP vì `clientMsgId` làm retry an toàn. Client gửi gì lên cũng chỉ được tính là còn sống. Frame client gửi lên tối đa 8KB.

Đường đi của một event:

1. Ghi Mongo xong, `EnvelopeEventPublisher` đóng phong bì và đọc thành viên hội thoại.
2. Phát `{ recipients, envelope }` lên Redis channel `messaging.realtime`.
3. Mọi instance nhận và gửi cho socket của người nhận đang mở ở instance đó.

Người gửi cũng là thành viên, nên thiết bị khác của người gửi nhận được tin. Thiết bị vừa gửi cũng nhận lại, client bỏ trùng theo `messageId` hoặc `clientMsgId`.

| Tình huống | Cách xử lý |
|---|---|
| Một user mở nhiều thiết bị, nhiều tab | Mỗi user giữ nhiều socket, mọi socket đều nhận |
| Mạng di động rớt không gửi close frame | Server ping mỗi 25 giây. Không thấy pong hay frame nào trong 60 giây thì đóng, mã 1011 |
| Client đọc chậm | Mỗi socket gửi tuần tự, tối đa 10 giây và 512KB chờ. Quá thì đóng, mã 1011 |
| Deploy hoặc tắt instance | `server.shutdown: graceful`, đóng mọi socket bằng 1001 trước khi tắt web server |
| Redis hoặc fan-out lỗi | Không làm hỏng request: tin đã ghi, trả 201 bình thường. Lettuce từ chối lệnh ngay khi mất kết nối, timeout 2 giây |
| Client lỡ frame | Redis pub/sub không lưu. Client thấy lỗ `seq` hoặc vừa kết nối lại thì gọi catch-up với `seq` cuối đã có |

Nhận mã đóng 1001 hoặc 1011, hoặc mất kết nối, thì client nên kết nối lại với backoff có jitter, rồi gọi catch-up. Ping và pong là frame điều khiển: trình duyệt và `java.net.http` tự trả pong, client không cần code thêm.

Thứ tự: event của cùng một instance đi theo thứ tự ghi. Giữa hai instance thì không chắc, nên client xếp tin theo `seq`, không theo lúc frame đến.

Đã chạy thử hai instance dùng chung Redis: tin gửi ở instance A tới socket ở instance B. Tắt Redis thì gửi tin vẫn 201 trong vài mili giây. Bật lại Redis thì tự đẩy tiếp. Tin lỡ trong lúc Redis tắt lấy lại đủ bằng catch-up, kể cả tin đã bị xoá.
