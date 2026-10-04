Note: thứ tự 1 cần làm trước, 2 làm khi còn thời gian

# 1. Function
**Nhóm A — Quản lý tài khoản và nhóm**

| ID | Chức năng | Đề xuất |
|---|---|---|
| F-A1 | Đăng ký tài khoản (email/username + password) | 1 |
| F-A2 | Đăng nhập, cấp token; API bảo vệ phải chặn request không token | 1 |
| F-A3 | Xem / sửa profile cơ bản (tên hiển thị, avatar tuỳ chọn) | 2 |
| F-A4 | Tạo nhóm, người tạo là owner | 1 |
| F-A5 | Owner thêm / xoá thành viên; thành viên tự rời nhóm | 1 |
| F-A6 | Xem danh sách nhóm của mình | 1 |
| F-A7 | Tìm user để bắt đầu chat 1-1 | 1 |
| F-A8 | Danh sách bạn bè (gửi / chấp nhận lời mời) | 2 |
| F-A9 | Role nhóm ngoài owner–member (ví dụ admin) | 2 |

**Nhóm B — Chat 1-1 và chat nhóm**

| ID | Chức năng | Đề xuất |
|---|---|---|
| F-B1 | Chat 1-1 giữa 2 user | 1 |
| F-B2 | Chat trong nhóm | 1 |
| F-B3 | Loại tin: **text** | 1 |
| F-B3e | Loại tin: **emotion** — chọn 1 phương án (E1–E5) ở bảng ngay dưới | E1 (1), E3 (2) |
| F-B4 | Loại tin: **image** | 1 |
| F-B5 | Loại tin: **document** | 1 |
| F-B6 | Loại tin: **video** | 1 |
| F-B7 | Tải lịch sử hội thoại gần nhất | 1 |
| F-B8 | Tin mới hiện lên không cần refresh trang / không cần mở lại app | 1 |
| F-B9 | Không phải thành viên thì không gửi được vào nhóm (chặn ở server) | 1 |
| F-B10 | Không nhân đôi tin khi refresh / retry (`clientMsgId`) | 1 *(mobile: mạng 4G retry nhiều hơn desktop rất nhiều)* |
| F-B11 | Unread count, typing, presence | 2 |
| F-B11d | **Đồng bộ multi-device**: 1 account đăng nhập web + mobile cùng lúc; gửi ở thiết bị này thì hiện ở thiết bị kia; đọc ở một bên thì unread bên kia tự tắt | 1 *(mobile — GV yêu cầu đồng bộ)* |
| F-B11c | **Catch-up sau khi mất kết nối**: app bị OS đóng socket lúc chạy nền, mở lại phải lấy đủ tin còn thiếu, không trùng, không mất | 1 *(mobile)* |
| F-B12 | Push notification khi app mobile không chạy nền (FCM) | 2 *(mobile)* |

| Phương án | Người dùng thấy gì | Ảnh hưởng kỹ thuật | Công sức |
|---|---|---|---|
| **E1 — Emotion là 1 loại tin riêng** *(đề xuất)* | Chọn 1 icon trong bộ có sẵn, gửi thành 1 tin riêng trong hội thoại | Thêm `type = EMOTION`, body là mã icon. Chỉ là 1 handler mới trong pipeline gửi, không đổi schema | Thấp |
| **E3 — Reaction gắn lên tin nhắn** | Bấm tim/like lên một tin nhắn cụ thể, giống Zalo/Messenger | Thêm entity `reaction` (ai, tin nào, loại gì), phải fan-out **cập nhật** một tin đã gửi, phải nghĩ lại unread và thống kê. **(mobile)** Còn phải fan-out cập nhật đó tới mọi thiết bị đang mở → tốn hơn hẳn so với trước | Cao |

**Nhóm C — Thống kê hoạt động người dùng**

| ID | Chức năng | Đề xuất |
|---|---|---|
| F-C1 | Ghi nhận activity event (login, gửi tin, tạo nhóm, upload media) | 1 |
| F-C2 | Thống kê đọc từ bảng tổng hợp / event, **không** quét toàn bộ message mỗi request | 1 (nguyên tắc kiến trúc) |
| F-C3 | Dashboard: tin/ngày, user hoạt động, top nhóm | 2 |
| F-C4 | Trang "hoạt động của tôi" cho từng user | 2 |

# 2. Non-function

| Rank (đề xuất) | Characteristic | Vì sao quan trọng với sản phẩm này | Mục tiêu đề xuất | Đánh đổi chính |
|---|---|---|---|---|
| **1** | **Deployability** | Đề bài yêu cầu release lên cloud theo tuần; demo chạy trên cloud là phần được chấm nặng nhất | Mỗi tuần có 1 URL cloud chạy được, deploy bằng 1 lệnh: một process và một PostgreSQL | Một process dễ deploy. Một module lỗi làm sập cả app |
| **2** | **Modularity** | 4 người làm song song; cũng là nội dung chính của báo cáo | Bounded context, mỗi context một schema trên cùng PostgreSQL. Gọi nhau bằng interface trong process. Không đọc bảng schema khác | Bỏ ranh giới process để hạng 1 đứng. Ranh giới còn lại là schema và interface |
| **3** | **Reliability** (độ tin cậy của tin nhắn) | Chat mất tin hoặc nhân đôi tin là mất điểm ngay khi demo | Ghi DB trước rồi mới publish; at-least-once + `clientMsgId` chống trùng | Exactly-once quá đắt → chấp nhận at-least-once + idempotent |
| **4** | **Multi-device consistency** | GV yêu cầu web và mobile đồng bộ. Đây là đặc tính *bị chấm trực tiếp khi demo*: mở 2 thiết bị cạnh nhau là thấy ngay đúng hay sai | Cùng 1 account trên 2 thiết bị thấy cùng danh sách hội thoại, cùng thứ tự tin, cùng trạng thái đã đọc; hội tụ < 1s khi online, < 3s sau khi reconnect | Server phải giữ read state + `seq` cho từng hội thoại (thêm việc), thay vì để mỗi client tự quản trạng thái của mình (rẻ, nhưng lệch nhau khi demo) |
| **5** | **Performance** (độ trễ tương tác) | Đây là thứ làm cho nó giống app OTT chứ không giống form CRUD | p95 gửi → hiện < 500ms với **text**; media chậm hơn được | Push (WebSocket, thêm hạ tầng) so với polling (đơn giản, trễ) |

# 3. In-scope
| Hạng mục | Đề xuất |
|---|---|
| Đăng ký, đăng nhập, profile cơ bản | **Trong** |
| Nhóm: tạo, xem danh sách, thêm/xoá thành viên | **Trong** |
| Chat 1-1 và chat nhóm | **Trong** |
| 5 loại tin: text, emotion, image, document, video | **Trong** |
| Activity event + trang thống kê | **Trong** |
| Web client | **Trong** |
| **Mobile client** | **Trong — Must (mobile)** |
| **Đồng bộ multi-device giữa web và mobile** (cùng account, cùng lịch sử, cùng trạng thái đã đọc) | **Trong — Must (mobile)** |
| Push notification qua FCM khi app chạy nền | **Trong — Should (mobile)** |
| Deploy cloud, release theo tuần | **Trong** |
| Giới hạn dung lượng: text 4KB, image 5MB, document 10MB, video 20MB | **Trong** |

# 4. Cloud
Sử dụng AWS (EC2, storage,...)

# 5. Phân việc
| Vai trò | Tầng / thành phần sở hữu | Service steward | Vì sao cắt như vậy | Người nhận |
|---|---|---|---|---|
| **P1 — Backend Core & Contract** | Identity & Group, Analytics, cửa HTTP public, filter auth, schema `identity` và `analytics`. **Chủ sở hữu OpenAPI + envelope event** | Identity & Group, Analytics | Hai module này đều CRUD + nhận event, cùng một kiểu tư duy. Người này ra hợp đồng sớm nhất vì 3 người còn lại chờ nó | Ngọc Anh |
| **P2 — Backend Messaging & Realtime** | Messaging, media, WebSocket, `seq` + catch-up, fan-out, schema `messaging`, signed URL | Messaging, Media | Phần khó nhất của hệ thống và cũng là phần **quyết định việc đồng bộ có đúng hay không**. Không chia cho 2 người để không ai bị chia trí | Huy |
| **P3 — Client Core & Web** | Thư viện client dùng chung (SDK sinh từ OpenAPI, refresh token, WS client với reconnect + phát hiện thiếu `seq` + catch-up, cache hội thoại, outbox) + toàn bộ màn hình web gồm dashboard | — (steward của `client-core`) | Logic đồng bộ phải **viết một lần, một chủ**. Web là nơi dễ debug nên viết core ở đây rồi mobile dùng lại | Khanh |
| **P4 — Mobile & Delivery** | Target mobile (dùng lại core của P3), push FCM, secure storage, file picker, build APK; **và** Docker Compose, CI/CD, release cloud hàng tuần, monitoring, load test | — (steward của `infra/`) | Mobile và delivery ghép với nhau vì cùng là "đưa hàng tới tay người dùng", và vì cả hai đều phải có người chịu trách nhiệm riêng, nếu không sẽ không bao giờ xảy ra | Thành |
