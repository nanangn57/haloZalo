# haloZalo

Ứng dụng chat 1-1 và chat nhóm, có web và mobile. Cùng một tài khoản trên hai thiết bị thấy cùng hội thoại, cùng thứ tự tin và cùng trạng thái đã đọc.

## Giới thiệu

Người dùng đăng ký, tạo nhóm, nhắn tin và xem thống kê hoạt động. Tin gồm chữ, emotion, ảnh, tài liệu và video. Tin mới tới qua WebSocket, không cần tải lại trang.

Bốn người làm trong một repo. Mỗi service giữ store của mình: muốn dữ liệu của service khác thì gọi HTTP hoặc nhận event. Client chỉ gọi API gateway.

## Tính năng

- Đăng ký, đăng nhập, hồ sơ cơ bản
- Nhóm: tạo, xem danh sách, thêm và xoá thành viên
- Chat 1-1 và chat nhóm
- Năm loại tin: text, emotion, image, document, video
- Đồng bộ web với mobile, kể cả sau khi mất kết nối
- Ghi hoạt động và trang thống kê
- Phát hành lên AWS mỗi tuần bằng một lệnh từ repo

Phạm vi đầy đủ nằm ở [doc/requirement.md](doc/requirement.md).

## Kiến trúc

| Thành phần | Vai trò | Lưu trữ |
|---|---|---|
| gateway | Cửa public duy nhất | — |
| identity | Tài khoản, token, nhóm, thành viên | MySQL |
| analytics | Ghi event, đọc bảng tổng hợp | MySQL |
| messaging | Hội thoại, tin, `seq`, file, fan-out | Mongo, Redis |
| client | Web và mobile dùng chung `clients/core` | Cache trên thiết bị |

Identity là nơi duy nhất ký token. Gateway tự kiểm chữ ký JWT. Tin được ghi xong rồi mới phát ra WebSocket.

Quyết định đã chốt: [doc/contract/decisions.md](doc/contract/decisions.md).
Hợp đồng HTTP: [doc/contract/openapi.yaml](doc/contract/openapi.yaml).
Sự kiện: [doc/contract/events.md](doc/contract/events.md).
Lịch 8 tuần: [doc/timeline.md](doc/timeline.md).

## Cấu trúc thư mục

Mỗi người sửa thư mục của mình.

| Thư mục | Việc | Người |
|---|---|---|
| `doc/contract/` | OpenAPI, envelope event, quyết định kiến trúc | P1 Ngọc Anh |
| `services/gateway/` | Cửa public duy nhất | P1 Ngọc Anh |
| `services/identity/` | Tài khoản, token, nhóm, thành viên | P1 Ngọc Anh |
| `services/analytics/` | Ghi event, đọc bảng tổng hợp | P1 Ngọc Anh |
| `services/messaging/` | Hội thoại, tin, `seq`, file, fan-out. Media nằm ở đây | P2 Huy |
| `clients/core/` | SDK, WebSocket, reconnect, catch-up, outbox, cache | P3 Khanh |
| `clients/web/` | Màn hình web, gồm dashboard | P3 Khanh |
| `clients/mobile/` | App mobile dùng lại `clients/core`, FCM, build APK | P4 Thành |
| `infra/` | Compose, một lệnh deploy, CI, URL cloud mỗi tuần | P4 Thành |

## Bắt đầu

Cần Git. Khi `infra/` có Compose thì cần Docker.

Chưa có lệnh chạy cả hệ thống. Cách chạy từng service sẽ nằm trong README của thư mục đó. Lệnh deploy một phát nằm ở `infra/`.

Đọc hợp đồng trước khi viết handler hoặc màn hình:

1. [doc/contract/openapi.yaml](doc/contract/openapi.yaml)
2. [doc/contract/events.md](doc/contract/events.md)
3. [doc/contract/decisions.md](doc/contract/decisions.md)

## Đóng góp

Sửa trong thư mục mình giữ. Một pull request không trộn thư mục của hai người.

Đổi API hoặc event thì P1 sửa `doc/contract/` trước. Người còn lại sinh type và SDK từ file đó, không chép schema bằng tay.

Mẫu mô tả task: [doc/taskTemplate.md](doc/taskTemplate.md).

## Nhóm

| Người | Phần |
|---|---|
| P1 Ngọc Anh | Hợp đồng, gateway, identity, analytics |
| P2 Huy | Messaging và media |
| P3 Khanh | client-core và web |
| P4 Thành | Mobile và phát hành |
