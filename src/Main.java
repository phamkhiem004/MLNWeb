import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class Main {

    // --- DỮ LIỆU ---
    // Lưu trữ tin nhắn cộng đồng (Lưu trong RAM, tắt server sẽ mất)
    private static List<String> communityThoughts = new ArrayList<>();

    // Dữ liệu triết học phân loại theo phòng
    private static Map<String, List<Wisdom>> schools = new HashMap<>();

    static class Wisdom {
        String quote;
        String author;

        public Wisdom(String q, String a) {
            this.quote = q;
            this.author = a;
        }
    }

    public static void main(String[] args) throws IOException {
        initData(); // Nạp dữ liệu

        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);

        // --- CÁC ĐƯỜNG DẪN (ROUTING) ---
        server.createContext("/", new HomeHandler()); // Trang chủ
        server.createContext("/room", new RoomHandler()); // Vào từng phòng triết học
        server.createContext("/post", new PostHandler()); // Đăng bài viết

        server.setExecutor(null);
        server.start();
        System.out.println("Web Agora đã khởi động tại port " + port);
    }

    // --- XỬ LÝ TRANG CHỦ ---
    static class HomeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String html = getHeader("Sảnh Chính") +
                    "<div class='container'>" +
                    "  <h1>🏛️ THE DIGITAL AGORA</h1>" +
                    "  <p>Chào mừng lữ khách. Bạn muốn bước vào cánh cửa nào hôm nay?</p>" +
                    "  <div class='nav-grid'>" +
                    "    <a href='/room?type=stoic' class='card choice'>🛡️ Chủ Nghĩa Khắc Kỷ<br><small>Sự bình thản & Sức mạnh nội tại</small></a>"
                    +
                    "    <a href='/room?type=exist' class='card choice'>🌑 Chủ Nghĩa Hiện Sinh<br><small>Tự do & Tạo ra ý nghĩa</small></a>"
                    +
                    "    <a href='/room?type=eastern' class='card choice'>🎋 Triết Học Phương Đông<br><small>Hòa hợp & Vô vi</small></a>"
                    +
                    "  </div>" +
                    "  <br><hr><br>" +
                    "  <h2>📜 Bức Tường Cộng Đồng</h2>" +
                    "  <div class='wall'>" +
                    renderCommunityWall() +
                    "  </div>" +
                    "  <form action='/post' method='post' class='post-form'>" +
                    "    <input type='text' name='thought' placeholder='Để lại một suy tư của bạn...' required>" +
                    "    <button type='submit'>Khắc lên tường</button>" +
                    "  </form>" +
                    "</div>" +
                    getFooter();
            sendResponse(t, html);
        }
    }

    // --- XỬ LÝ TỪNG PHÒNG ---
    static class RoomHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String query = t.getRequestURI().getQuery(); // Lấy phần ?type=stoic
            String type = "stoic"; // Mặc định
            if (query != null && query.contains("type=")) {
                type = query.split("type=")[1];
            }

            List<Wisdom> roomData = schools.getOrDefault(type, schools.get("stoic"));
            Wisdom w = roomData.get(new Random().nextInt(roomData.size()));

            String title = type.equals("stoic") ? "Phòng Khắc Kỷ"
                    : (type.equals("exist") ? "Phòng Hiện Sinh" : "Phòng Phương Đông");
            String colorClass = type;

            String html = getHeader(title) +
                    "<div class='container " + colorClass + "-theme'>" +
                    "  <a href='/' class='back-btn'>⬅ Quay lại Sảnh</a>" +
                    "  <h1>" + title + "</h1>" +
                    "  <div class='quote-card'>" +
                    "    <p class='quote'>\"" + w.quote + "\"</p>" +
                    "    <p class='author'>— " + w.author + "</p>" +
                    "  </div>" +
                    "  <div class='actions'>" +
                    "     <button onclick='window.location.reload()'>✨ Suy ngẫm câu khác</button>" +
                    "  </div>" +
                    "</div>" +
                    getFooter();
            sendResponse(t, html);
        }
    }

    // --- XỬ LÝ ĐĂNG BÀI (POST) ---
    static class PostHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod())) {
                InputStreamReader isr = new InputStreamReader(t.getRequestBody(), StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                String formData = br.readLine(); // Dạng: thought=Noi+dung+viet
                if (formData != null && formData.startsWith("thought=")) {
                    String rawContent = formData.split("thought=")[1];
                    String decodedContent = URLDecoder.decode(rawContent, StandardCharsets.UTF_8.name());

                    // Thêm vào danh sách (Lưu tối đa 10 tin mới nhất)
                    if (communityThoughts.size() >= 10)
                        communityThoughts.remove(0);
                    communityThoughts.add(decodedContent);
                }
            }
            // Quay lại trang chủ sau khi đăng
            t.getResponseHeaders().set("Location", "/");
            t.sendResponseHeaders(302, -1);
        }
    }

    // --- CÁC HÀM HỖ TRỢ (HELPER) ---

    private static void sendResponse(HttpExchange t, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        t.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        t.sendResponseHeaders(200, bytes.length);
        OutputStream os = t.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private static String renderCommunityWall() {
        if (communityThoughts.isEmpty())
            return "<p style='opacity:0.6'>Chưa có suy tư nào. Hãy là người đầu tiên.</p>";
        StringBuilder sb = new StringBuilder();
        // Hiển thị ngược (mới nhất lên đầu)
        for (int i = communityThoughts.size() - 1; i >= 0; i--) {
            sb.append("<div class='wall-msg'>❝ ").append(communityThoughts.get(i)).append(" ❞</div>");
        }
        return sb.toString();
    }

    private static void initData() {
        List<Wisdom> stoic = new ArrayList<>();
        stoic.add(new Wisdom("Chúng ta khổ sở trong tưởng tượng nhiều hơn trong thực tế.", "Seneca"));
        stoic.add(new Wisdom("Không gì có thể làm hại bạn nếu bạn không cho phép nó.", "Marcus Aurelius"));
        stoic.add(new Wisdom("Hạnh phúc phụ thuộc vào bản thân ta.", "Aristotle"));
        schools.put("stoic", stoic);

        List<Wisdom> exist = new ArrayList<>();
        exist.add(new Wisdom("Con người bị kết án phải tự do.", "Jean-Paul Sartre"));
        exist.add(new Wisdom("Nếu thượng đế không tồn tại, mọi thứ đều được phép.", "Fyodor Dostoevsky"));
        exist.add(new Wisdom("Ta phải tưởng tượng Sisyphus đang hạnh phúc.", "Albert Camus"));
        schools.put("exist", exist);

        List<Wisdom> eastern = new ArrayList<>();
        eastern.add(new Wisdom("Biết người là trí, biết mình là sáng.", "Lão Tử"));
        eastern.add(new Wisdom("Đời là bể khổ.", "Đức Phật"));
        eastern.add(new Wisdom("Quá khứ đã qua, tương lai chưa tới, chỉ có hiện tại là thật.", "Thích Nhất Hạnh"));
        schools.put("eastern", eastern);
    }

    // --- HTML & CSS ---
    private static String getHeader(String title) {
        return "<!DOCTYPE html><html lang='vi'><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'>"
                +
                "<title>" + title + "</title>" +
                "<style>" +
                "@import url('https://fonts.googleapis.com/css2?family=Merriweather:ital,wght@0,300;0,700;1,300&family=Montserrat:wght@400;600&display=swap');"
                +
                ":root { --bg: #0f172a; --card: #1e293b; --text: #e2e8f0; --gold: #fbbf24; --accent: #38bdf8; }" +
                "body { background-color: var(--bg); color: var(--text); font-family: 'Montserrat', sans-serif; margin: 0; padding: 0; line-height: 1.6; }"
                +
                ".container { max-width: 800px; margin: 0 auto; padding: 20px; text-align: center; }" +
                "h1 { font-family: 'Merriweather', serif; color: var(--gold); margin-bottom: 30px; letter-spacing: 1px; }"
                +
                "a { text-decoration: none; color: inherit; }" +
                ".nav-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px; }" +
                ".card { background: var(--card); padding: 20px; border-radius: 12px; border: 1px solid #334155; transition: transform 0.2s; }"
                +
                ".choice:hover { transform: translateY(-5px); border-color: var(--gold); cursor: pointer; }" +
                ".choice small { display: block; margin-top: 5px; color: #94a3b8; font-size: 0.8rem; }" +
                ".quote-card { background: linear-gradient(145deg, #1e293b, #0f172a); padding: 40px; border-radius: 15px; margin: 30px 0; border: 1px solid var(--gold); }"
                +
                ".quote { font-family: 'Merriweather', serif; font-size: 1.5rem; font-style: italic; color: #fff; }" +
                ".author { margin-top: 20px; color: var(--gold); font-weight: bold; text-transform: uppercase; letter-spacing: 2px; }"
                +
                "button { background: var(--gold); color: #000; border: none; padding: 12px 25px; border-radius: 25px; font-weight: bold; cursor: pointer; font-size: 1rem; margin-top: 10px; }"
                +
                "button:hover { opacity: 0.9; }" +
                ".back-btn { display: inline-block; margin-bottom: 20px; color: var(--accent); font-size: 0.9rem; }" +
                ".wall { background: rgba(255,255,255,0.05); padding: 20px; border-radius: 10px; text-align: left; max-height: 300px; overflow-y: auto; margin-bottom: 20px; }"
                +
                ".wall-msg { border-bottom: 1px solid #334155; padding: 10px 0; font-family: 'Merriweather', serif; font-size: 0.95rem; }"
                +
                ".post-form { display: flex; gap: 10px; }" +
                ".post-form input { flex: 1; padding: 10px; border-radius: 20px; border: none; background: #334155; color: white; }"
                +
                "</style></head><body>";
    }

    private static String getFooter() {
        return "<br><br><p style='text-align:center; color:#475569; font-size:0.8rem'>Java Web Server - Created on Replit</p></body></html>";
    }
}