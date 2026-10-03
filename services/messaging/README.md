# messaging

Hội thoại, tin, `seq`, file, fan-out. PostgreSQL cho hội thoại và tin, Redis cho fan-out realtime giữa các instance. Media nằm ở service này.

Người giữ: P2 Huy. Service này không đọc bảng của service khác.

Cổng `8082`. Postgres, Redis và cấu hình WebSocket nằm trong `resources/application.yml`.

```
services/messaging/
  pom.xml
  resources/          cấu hình, và db/migration là schema Postgres
  src/
    app/              khởi động Spring Boot
    conversation/     hội thoại DIRECT hoặc GROUP, thành viên, seq cuối
    message/          tin, loại tin, nội dung theo loại, trạng thái, kiểm tra nội dung
    reaction/         reaction của một user trên một tin
    api/              JSON request và response, đúng shape trong OpenAPI
    storage/          interface lưu, và implementation Postgres
    event/            đóng phong bì event, ghi log, đẩy sang realtime
    realtime/         WebSocket /ws, socket theo user, heartbeat, fan-out qua Redis
    service/          hội thoại, gửi, trả lời, chuyển tiếp, xoá, reaction, lịch sử, catch-up
    http/             nhận HTTP, đổi lỗi thành status
  test/               cùng các package trên
```

Chạy trên máy:

```
./local-deps.sh up          # Postgres 17 ở localhost:5432, Redis 8 ở localhost:6379
mvn spring-boot:run         # chạy từ thư mục này để đọc .env.local
./local-deps.sh psql        # mở psql vào database messaging
./local-deps.sh down        # tắt, giữ dữ liệu; reset thì xoá luôn dữ liệu
```

`.env.local` lấy mẫu từ `.env.example`. Không điền gì thì dùng đúng Postgres và Redis của `local-deps.sh`. Không có Redis thì đặt `MESSAGING_REALTIME_BUS=local`, chỉ dùng cho một instance.

`mvn test` không cần Postgres hay Redis. Test repository chạy SQL thật trên một Postgres tạm do Testcontainers bật, và tự bỏ qua khi máy không có Docker.

Chạy cả service bằng Docker, gồm Postgres và Redis riêng: `docker compose up --build` trong thư mục này. Service ở `http://localhost:8082`, WebSocket ở `ws://localhost:8082/ws`. `docker compose down -v` xoá luôn dữ liệu Postgres. Compose và `local-deps.sh` dùng cùng cổng, chỉ chạy một trong hai. Compose không đọc `.env.local`, vì trong container `localhost` không phải Postgres hay Redis. Muốn dùng Postgres hay Redis trên cloud thì đặt biến trong shell trước khi chạy compose. Image không chứa `.env.local` và chạy bằng user không phải root.

`GET /actuator/health` trả `UP` khi app và Postgres chạy, `DOWN` 503 khi mất Postgres. Redis không tính vào health, vì Redis tắt thì gửi tin vẫn được.

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `POSTGRES_URL` | `jdbc:postgresql://localhost:5432/messaging` | JDBC URL. Cloud thường thêm `?sslmode=require` |
| `POSTGRES_USER`, `POSTGRES_PASSWORD` | `messaging`, `messaging` | User và password của database |
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

`MessageContent` là sealed interface, mỗi `MessageType` một record: `Text`, `Emotion`, `Image`, `Video`, `Document`. `type` là discriminator. Thêm loại mới là thêm một record, một nhánh trong `ContentValidator`, `MessageContentJson` và `MessageResponse`, và thêm loại vào CHECK của cột `messages.type` bằng một migration mới. Không có bảng riêng cho từng loại.

`metadata` chỉ giữ thông tin phụ. Người gửi, loại, `replyTo`, `forwardedFrom`, trạng thái và thời gian luôn là field riêng.

`replyTo` trỏ tới một tin trong cùng hội thoại. `forwardedFrom` trỏ tới tin gốc đầu tiên, kể cả khi chuyển tiếp một tin đã chuyển tiếp. Tin chuyển tiếp có `messageId` và `seq` của riêng nó. Chỉ chuyển tiếp được tin của hội thoại mình đang ở.

`status` là `SENT`, `DELETED` hoặc `ERROR`. Không có `read` trên tin: mốc đã đọc giữ theo user và hội thoại, theo decisions.md.

Xoá là xoá mềm: `status = DELETED`, `deletedAt` được ghi, dòng vẫn còn vì tin khác có thể trỏ tới. Chỉ người gửi xoá được.

Reaction dùng bộ mã của emotion. Một user một mã một lần trên một tin.

## PostgreSQL

Schema nằm ở `resources/db/migration`. Flyway chạy lúc khởi động, mỗi file một lần theo số version. Đổi schema thì thêm file `V2__...sql`, không sửa file đã chạy.

| Bảng | Khoá | Ràng buộc |
|---|---|---|
| `conversations` | `id` | `type` là `DIRECT` hoặc `GROUP`; `last_seq` là `seq` của tin mới nhất |
| `conversation_members` | `(conversation_id, user_id)` | index `user_id` cho danh sách hội thoại của một user |
| `messages` | `id` | unique `(sender_id, client_msg_id)`, unique `(conversation_id, seq)`; `content` và `metadata` là JSONB; `reply_to`, `forwarded_from` là khoá ngoại tới `messages` |
| `message_reactions` | `id` | unique `(message_id, user_id, code)`; `message_id` là khoá ngoại tới `messages` |

User nằm ở Identity, nên `user_id` và `sender_id` chỉ là UUID, không có khoá ngoại sang service khác.

Gửi tin là một transaction: tăng `conversations.last_seq` rồi `INSERT` tin. `UPDATE` khoá dòng hội thoại, nên tin trong một hội thoại nhận `seq` lần lượt, bắt đầu từ 1. Trùng `clientMsgId` thì rollback cả transaction, nên `seq` không bao giờ có lỗ. Test đã chạy 100 lần gửi song song và 10 lần retry song song trên Postgres thật.

Mọi id là UUID và được trả về chữ thường. Client hay gateway gửi chữ hoa vẫn được hiểu là cùng một id.

## API

`POST /conversations/{conversationId}/messages` nhận `SendMessageRequest` của OpenAPI, chỉ `TEXT` và `EMOTION`. Trả `Message` của OpenAPI: 201 khi tin mới, 200 khi trùng `clientMsgId`. Ghi xong mới log `messaging.message.created`.

### Hội thoại

`POST /conversations/direct` nhận `{ userId }` của người kia, trả hội thoại 1-1 giữa hai người: 201 khi vừa tạo, 200 khi đã có. Mỗi cặp user chỉ có một hội thoại DIRECT, ai mở trước cũng vậy, kể cả khi hai người mở cùng lúc: ràng buộc unique `direct_key` trong Postgres giữ điều đó. Mở với chính mình thì 400. Service không hỏi Identity xem `userId` có tồn tại không.

`GET /conversations?cursor=&limit=` trả `{ conversations, nextCursor }`: hội thoại của người gọi, hoạt động gần nhất trước, mỗi hội thoại kèm `lastMessage`. `limit` mặc định 50, tối đa 100. Còn trang sau thì gửi lại `nextCursor` làm `cursor`; trang cuối có `nextCursor: null`. Hai query cho mỗi trang, không phụ thuộc số hội thoại.

`GET /conversations/{conversationId}` trả một hội thoại, chỉ cho thành viên.

Mỗi hội thoại có `conversationId`, `type`, `memberIds`, `lastSeq`, `createdAt`, và khi đã có tin thì `lastMessageAt`, `lastMessage` (shape `Message`). Tin cuối đã xoá thì hiện như ô trống. Chưa có số tin chưa đọc: cần mốc đã đọc của tuần 5.

Tin tới một hội thoại mà client chưa biết (người kia vừa mở rồi gửi) thì client gọi `GET /conversations/{conversationId}` để lấy thông tin. Không có event riêng khi mở hội thoại.

Nhóm vẫn mở bằng `POST /internal/conversations`, vì nhóm thuộc Identity.

### Tin trong hội thoại

`GET /conversations/{conversationId}/messages` trả `{ messages, hasMore }`, `messages` là `Message` của OpenAPI, luôn xếp theo `seq` tăng dần. `limit` mặc định 100, tối đa 200. Chỉ thành viên gọi được. Có hai cách dùng:

- Lịch sử: không có `afterSeq`. Trả trang mới nhất. Muốn xem cũ hơn thì gửi `beforeSeq` là `seq` nhỏ nhất vừa nhận. `hasMore` nghĩa là còn tin cũ hơn.
- Catch-up: `afterSeq` là `seq` cuối client đã có. Trả tin cũ nhất sau mốc đó. `hasMore` nghĩa là còn tin mới hơn, gọi tiếp với `seq` cuối vừa nhận.

Gửi cả `afterSeq` và `beforeSeq` thì 400.

Tin đã xoá vẫn có trong cả hai cách, với `status: "DELETED"` và `deletedAt`, không có `body`. Nhờ vậy client thấy đủ mọi `seq`, không chờ một tin không bao giờ tới. `hasMore: false` nghĩa là đã đủ. Query đi theo ràng buộc unique `(conversation_id, seq)`.

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

1. Transaction ghi Postgres commit xong, `EnvelopeEventPublisher` đóng phong bì và đọc thành viên hội thoại.
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
