import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Main {

    // --- CẤU TRÚC DỮ LIỆU ---
    static class Post {
        int id;
        String content;
        int likes;

        public Post(int id, String content, int likes) {
            this.id = id;
            this.content = content;
            this.likes = likes;
        }

        // Chuyển đối tượng thành chuỗi để lưu vào file (dạng: id|likes|content)
        public String toFileString() {
            // Thay thế ký tự xuống dòng để tránh lỗi file
            String cleanContent = content.replace("\n", " ").replace("|", "-");
            return id + "|" + likes + "|" + cleanContent;
        }
    }

    static class Wisdom {
        String quote;
        String author;

        public Wisdom(String q, String a) {
            this.quote = q;
            this.author = a;
        }
    }

    // --- KHO CHỨA & DATABASE FILE ---
    private static final String DB_FILE = "minidb.txt"; // Tên file lưu dữ liệu
    private static List<Post> communityPosts = new ArrayList<>();
    private static int postIdCounter = 1;
    private static Map<String, List<Wisdom>> schools = new HashMap<>();

    public static void main(String[] args) throws IOException {
        initData(); // Nạp danh ngôn
        loadPostsFromFile(); // <--- MỚI: Khôi phục dữ liệu cũ khi khởi động

        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);

        server.createContext("/", new HomeHandler());
        server.createContext("/room", new RoomHandler());
        server.createContext("/post", new PostHandler());
        server.createContext("/like", new LikeHandler());

        server.setExecutor(null);
        server.start();
        System.out.println("Agora 3.0 (Có lưu trữ) đã chạy tại port " + port);
    }

    // --- 1. XỬ LÝ DATABASE (FILE TEXT) ---
    // Lưu toàn bộ danh sách xuống file
    private static void savePostsToFile() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(DB_FILE))) {
            for (Post p : communityPosts) {
                writer.write(p.toFileString());
                writer.newLine();
            }
        } catch (IOException e) {
            System.out.println("Lỗi lưu file: " + e.getMessage());
        }
    }

    // Đọc dữ liệu từ file lên RAM
    private static void loadPostsFromFile() {
        File file = new File(DB_FILE);
        if (!file.exists())
            return; // Nếu chưa có file thì thôi

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            communityPosts.clear();
            int maxId = 0;
            while ((line = reader.readLine()) != null) {
                // Tách chuỗi: id|likes|content
                String[] parts = line.split("\\|", 3);
                if (parts.length == 3) {
                    int id = Integer.parseInt(parts[0]);
                    int likes = Integer.parseInt(parts[1]);
                    String content = parts[2];
                    communityPosts.add(new Post(id, content, likes));

                    if (id > maxId)
                        maxId = id;
                }
            }
            postIdCounter = maxId + 1; // Cập nhật bộ đếm ID tiếp theo
        } catch (IOException e) {
            System.out.println("Lỗi đọc file: " + e.getMessage());
        }
    }

    // --- 2. XỬ LÝ TRANG CHỦ ---
    static class HomeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String html = getHeader("Sảnh Chính") +
                    "<div class='container'>" +
                    "  <h1>🏛️ THE DIGITAL AGORA</h1>" +
                    "  <p>Nơi lưu giữ những suy tư (Đã có tính năng lưu trữ vĩnh viễn).</p>" +
                    "  <div class='nav-grid'>" +
                    "    <a href='/room?type=stoic' class='card choice'>🛡️ Khắc Kỷ</a>" +
                    "    <a href='/room?type=exist' class='card choice'>🌑 Hiện Sinh</a>" +
                    "    <a href='/room?type=eastern' class='card choice'>🎋 Phương Đông</a>" +
                    "  </div>" +
                    "  <br><hr><br>" +
                    "  <h2>📜 Bức Tường Cộng Đồng</h2>" +
                    "  <div class='post-input-area'>" +
                    "     <form action='/post' method='post' class='post-form'>" +
                    "       <input type='text' name='thought' placeholder='Bạn đang suy ngẫm điều gì?' required>" +
                    "       <button type='submit'>Khắc lên tường</button>" +
                    "     </form>" +
                    "  </div>" +
                    "  <div class='wall'>" +
                    renderCommunityWall() +
                    "  </div>" +
                    "</div>" +
                    getFooter();
            sendResponse(t, html);
        }
    }

    // --- 3. XỬ LÝ ĐĂNG BÀI (CÓ LƯU) ---
    static class PostHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod())) {
                String body = getRequestBody(t);
                if (body.startsWith("thought=")) {
                    String rawContent = body.split("thought=")[1];
                    String decodedContent = URLDecoder.decode(rawContent, StandardCharsets.UTF_8.name());

                    // Thêm mới
                    if (communityPosts.size() >= 50)
                        communityPosts.remove(0); // Giới hạn 50 bài
                    communityPosts.add(new Post(postIdCounter++, decodedContent, 0));

                    savePostsToFile(); // <--- QUAN TRỌNG: Lưu ngay xuống file
                }
            }
            redirectHome(t);
        }
    }

    // --- 4. XỬ LÝ LIKE (CÓ LƯU) ---
    static class LikeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod())) {
                String body = getRequestBody(t);
                if (body.startsWith("id=")) {
                    try {
                        int idToLike = Integer.parseInt(body.split("id=")[1]);
                        for (Post p : communityPosts) {
                            if (p.id == idToLike) {
                                p.likes++;
                                savePostsToFile(); // <--- QUAN TRỌNG: Lưu like xuống file
                                break;
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            redirectHome(t);
        }
    }

    // --- CÁC HÀM CŨ (KHÔNG ĐỔI) ---
    private static String renderCommunityWall() {
        if (communityPosts.isEmpty())
            return "<p style='opacity:0.6; text-align:center'>Chưa có suy tư nào.</p>";
        StringBuilder sb = new StringBuilder();
        for (int i = communityPosts.size() - 1; i >= 0; i--) {
            Post p = communityPosts.get(i);
            sb.append("<div class='wall-msg' id='post-").append(p.id).append("'>")
                    .append("  <div class='msg-content'>❝ ").append(p.content).append(" ❞</div>")
                    .append("  <div class='msg-actions'>")
                    .append("    <form action='/like' method='post' style='display:inline'>")
                    .append("      <input type='hidden' name='id' value='").append(p.id).append("'>")
                    .append("      <button type='submit' class='btn-like'>❤️ ").append(p.likes).append("</button>")
                    .append("    </form>")
                    .append("    <button onclick='hidePost(").append(p.id).append(")' class='btn-hide'>🙈 Ẩn</button>")
                    .append("  </div>")
                    .append("</div>");
        }
        return sb.toString();
    }

    static class RoomHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String query = t.getRequestURI().getQuery();
            String type = (query != null && query.contains("type=")) ? query.split("type=")[1] : "stoic";
            List<Wisdom> roomData = schools.getOrDefault(type, schools.get("stoic"));
            Wisdom w = roomData.get(new Random().nextInt(roomData.size()));
            String title = type.equals("stoic") ? "Phòng Khắc Kỷ"
                    : (type.equals("exist") ? "Phòng Hiện Sinh" : "Phòng Phương Đông");
            String html = getHeader(title) +
                    "<div class='container'>" +
                    "  <a href='/' class='back-btn'>⬅ Quay lại</a>" +
                    "  <h1>" + title + "</h1>" +
                    "  <div class='quote-card'><p class='quote'>\"" + w.quote + "\"</p><p class='author'>— " + w.author
                    + "</p></div>" +
                    "  <button onclick='window.location.reload()' class='btn-reload'>✨ Câu khác</button>" +
                    "</div>" + getFooter();
            sendResponse(t, html);
        }
    }

    private static String getRequestBody(HttpExchange t) throws IOException {
        InputStreamReader isr = new InputStreamReader(t.getRequestBody(), StandardCharsets.UTF_8);
        return new BufferedReader(isr).readLine();
    }

    private static void redirectHome(HttpExchange t) throws IOException {
        t.getResponseHeaders().set("Location", "/");
        t.sendResponseHeaders(302, -1);
    }

    private static void sendResponse(HttpExchange t, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        t.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        t.sendResponseHeaders(200, bytes.length);
        OutputStream os = t.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private static void initData() {
        schools.put("stoic", Arrays.asList(new Wisdom("Chúng ta khổ sở trong tưởng tượng nhiều hơn thực tế.", "Seneca"),
                new Wisdom("Không gì làm hại bạn nếu bạn không cho phép.", "Marcus Aurelius")));
        schools.put("exist", Arrays.asList(new Wisdom("Con người bị kết án phải tự do.", "Sartre"),
                new Wisdom("Ta phải tưởng tượng Sisyphus hạnh phúc.", "Camus")));
        schools.put("eastern", Arrays.asList(new Wisdom("Biết người là trí, biết mình là sáng.", "Lão Tử"),
                new Wisdom("Đời là bể khổ, quay đầu là bờ.", "Phật Giáo")));
    }

    private static String getHeader(String title) {
        return "<!DOCTYPE html><html lang='vi'><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'><title>"
                + title + "</title>" +
                "<style>@import url('https://fonts.googleapis.com/css2?family=Merriweather:ital,wght@0,300;0,700;1,300&family=Montserrat:wght@400;600&display=swap');"
                +
                ":root { --bg: #0f172a; --card: #1e293b; --text: #e2e8f0; --gold: #fbbf24; --red: #ef4444; }" +
                "body { background-color: var(--bg); color: var(--text); font-family: 'Montserrat', sans-serif; margin: 0; padding: 20px; line-height: 1.6; }"
                +
                ".container { max-width: 600px; margin: 0 auto; text-align: center; }" +
                "h1 { font-family: 'Merriweather', serif; color: var(--gold); }" +
                ".nav-grid { display: flex; gap: 10px; justify-content: center; flex-wrap: wrap; }" +
                ".choice { background: var(--card); padding: 10px 20px; border-radius: 8px; border: 1px solid #334155; text-decoration: none; color: white; transition: 0.2s; }"
                +
                ".choice:hover { border-color: var(--gold); transform: translateY(-2px); }" +
                ".wall { text-align: left; margin-top: 20px; }" +
                ".wall-msg { background: rgba(255,255,255,0.05); padding: 15px; margin-bottom: 15px; border-radius: 10px; border-left: 3px solid var(--gold); }"
                +
                ".msg-content { font-family: 'Merriweather', serif; margin-bottom: 10px; font-size: 1.1em; }" +
                ".msg-actions { display: flex; gap: 10px; align-items: center; }" +
                ".btn-like { background: none; border: 1px solid #ef4444; color: #ef4444; padding: 5px 12px; border-radius: 15px; cursor: pointer; transition: 0.2s; }"
                +
                ".btn-like:hover { background: #ef4444; color: white; }" +
                ".btn-hide { background: none; border: 1px solid #94a3b8; color: #94a3b8; padding: 5px 12px; border-radius: 15px; cursor: pointer; }"
                +
                ".btn-hide:hover { background: #94a3b8; color: #0f172a; }" +
                ".post-form { display: flex; gap: 10px; margin-bottom: 20px; }" +
                ".post-form input { flex: 1; padding: 12px; border-radius: 20px; border: none; background: #334155; color: white; outline: none; }"
                +
                ".post-form button { background: var(--gold); border: none; padding: 0 20px; border-radius: 20px; font-weight: bold; cursor: pointer; }"
                +
                ".quote-card { background: var(--card); padding: 30px; border-radius: 15px; border: 1px solid var(--gold); margin: 20px 0; }"
                +
                ".quote { font-style: italic; font-size: 1.2em; }.author { color: var(--gold); font-weight: bold; margin-top: 10px; }"
                +
                ".btn-reload { padding: 10px 20px; background: var(--gold); border: none; border-radius: 20px; font-weight: bold; cursor: pointer; }"
                +
                ".back-btn { display: inline-block; margin-bottom: 15px; color: #38bdf8; text-decoration: none; }" +
                "<script>function hidePost(id){var e=document.getElementById('post-'+id);if(e){e.style.opacity='0';setTimeout(function(){e.style.display='none';},500);}}</script>"
                +
                "</style></head><body>";
    }

    private static String getFooter() {
        return "<br><br><p style='text-align:center; color:#475569; font-size:0.8rem'>Java Agora v3.0 (Persistent)</p></body></html>";
    }
}