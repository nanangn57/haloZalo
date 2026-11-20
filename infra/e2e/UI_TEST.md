# UI E2E (Chrome web + Expo web)

Điều khiển thật UI trên browser Cursor:

| Client | URL | User |
|---|---|---|
| Web (Vite) | http://127.0.0.1:5173 | `anh@example.com` / `password1` |
| Expo web | http://127.0.0.1:8081 | `huy@example.com` / `password1` |

## Khởi động

```bash
# API monolith
cd infra && docker compose -f docker-compose.yml -f docker-compose.local.yml up -d

# Web
cp clients/web/.env.dev clients/web/ui/.env.local
cd clients/web/ui && npm run dev -- --host 127.0.0.1 --port 5173

# Expo web
cd clients/mobile/app && npx expo start --web --port 8081 --clear
```

## Checklist đã chạy (2026-11-20)

1. Chrome login → chat → WS connected  
2. Gửi TEXT `UI chrome to expo sync 1617` tới huy  
3. Expo login (huy) → thấy conversation **Ngoc Anh Agent**, unread  
4. Expo mở hội thoại → thấy đúng tin từ Chrome + media document  
5. Expo trả lời `expo reply to chrome 1618` → Chrome nhận realtime  
6. Chrome Dashboard `/dashboard` load stats  
7. Chrome gắn file IMAGE qua input (DataTransfer) → media URL `/media/...` hiện trong chat  

## Lưu ý

- Expo `Pressable` trên web đôi khi không nhận `element.click()` từ automation; dùng pointer events / deep-link `?conversationId=` / inject `sessionStorage.auth`.
- File picker native bị chặn trong automation; web dùng `input[type=file]` + `DataTransfer`.
- IMAGE trên Expo có thể hiện ô xám nếu URL/render chưa ổn; DOCUMENT hiện “Open file”.
