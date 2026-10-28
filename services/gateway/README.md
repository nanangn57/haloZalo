# gateway

Không còn là process riêng. Cửa public là app: HTTP và WebSocket trên một cổng. Filter trong app gọi `AuthService` của identity khi request có Bearer hoặc WebSocket có `access_token`. App không giữ bảng session và không gọi HTTP nội bộ.

Người giữ: P1 Ngọc Anh.
