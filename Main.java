import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap; // <--- MỚI: Dùng để lưu danh sách bị Ban an toàn

public class Main {

    // --- CẤU TRÚC DỮ LIỆU ---
    static class Post {
        int id;
        String content;
        int likes;
        int dislikes;

        public Post(int id, String content, int likes, int dislikes) {
            this.id = id;
            this.content = content;
            this.likes = likes;
            this.dislikes = dislikes;
        }

        public String toFileString() {
            String cleanContent = content.replace("\n", " ").replace("|", "-");
            return id + "|" + likes + "|" + dislikes + "|" + cleanContent;
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

    // --- CÁC BIẾN TOÀN CỤC ---
    private static final String DB_FILE = "minidb.txt";
    private static List<Post> communityPosts = new ArrayList<>();
    private static int postIdCounter = 1;
    private static Map<String, List<Wisdom>> schools = new HashMap<>();

    // --- PHẦN MỚI: CẤU HÌNH BAN (CẤM) ---
    // 1. Danh sách từ cấm (Copy từ JS của bạn vào đây để Server xử lý)
    private static final String[] BAD_WORDS = {
            "ngu", "chó", "chết", "bậy", "tục", "điên",
            "buồi", "cặc", "lồn", "giết", "buoi", "cac",
            "lon", "giet", "súc vật", "dm", "đm", "vkl"
    };

    // 2. Sổ đen ghi IP: Map<IP, Thời gian được thả>
    private static final Map<String, Long> bannedIps = new ConcurrentHashMap<>();

    // 3. Thời gian phạt: 5 phút (300,000 mili giây)
    private static final long BAN_DURATION = 5 * 60 * 1000;

    public static void main(String[] args) throws IOException {
        initData();
        loadPostsFromFile();

        int port = 8080;
        if (System.getenv("PORT") != null) {
            port = Integer.parseInt(System.getenv("PORT"));
        }

        System.out.println("Server đang khởi động tại cổng: " + port);

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);

        server.createContext("/", new HomeHandler());
        server.createContext("/room", new RoomHandler());
        server.createContext("/post", new PostHandler());
        server.createContext("/like", new LikeHandler());
        server.createContext("/dislike", new DislikeHandler());

        server.setExecutor(null);
        server.start();
        System.out.println("Server đã chạy thành công!");
    }

    // --- HÀM MỚI: LẤY IP THẬT TỪ RENDER ---
    private static String getClientIP(HttpExchange t) {
        // Render đặt IP thật của người dùng trong header này
        String ip = t.getRequestHeaders().getFirst("X-Forwarded-For");

        if (ip == null || ip.isEmpty()) {
            return t.getRemoteAddress().getAddress().getHostAddress(); // Chạy localhost
        }
        // Nếu có nhiều IP (do qua nhiều proxy), lấy cái đầu tiên
        return ip.split(",")[0].trim();
    }

    // --- 1. XỬ LÝ DATABASE ---
    private static void savePostsToFile() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(DB_FILE))) {
            for (Post p : communityPosts) {
                writer.write(p.toFileString());
                writer.newLine();
            }
        } catch (IOException e) {
            System.out.println("Lỗi lưu file");
        }
    }

    private static void loadPostsFromFile() {
        File file = new File(DB_FILE);
        if (!file.exists())
            return;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            communityPosts.clear();
            int maxId = 0;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|", 4);
                if (parts.length >= 3) {
                    int id = Integer.parseInt(parts[0]);
                    int likes = Integer.parseInt(parts[1]);
                    int dislikes = (parts.length == 4) ? Integer.parseInt(parts[2]) : 0;
                    String content = (parts.length == 4) ? parts[3] : parts[2];

                    communityPosts.add(new Post(id, content, likes, dislikes));
                    if (id > maxId)
                        maxId = id;
                }
            }
            postIdCounter = maxId + 1;
        } catch (Exception e) {
            System.out.println("Lỗi đọc file: " + e.getMessage());
        }
    }

    // --- 2. XỬ LÝ HANDLERS ---

    // Xử lý Like
    static class LikeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            handleReaction(t, true);
        }
    }

    // Xử lý Dislike
    static class DislikeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            handleReaction(t, false);
        }
    }

    private static void handleReaction(HttpExchange t, boolean isLike) throws IOException {
        if ("POST".equals(t.getRequestMethod())) {
            String body = getRequestBody(t);
            if (body.startsWith("id=")) {
                try {
                    int id = Integer.parseInt(body.split("id=")[1]);
                    for (Post p : communityPosts) {
                        if (p.id == id) {
                            if (isLike)
                                p.likes++;
                            else
                                p.dislikes++;
                            savePostsToFile();
                            break;
                        }
                    }
                } catch (Exception e) {
                }
            }
        }
        redirectHome(t);
    }

    // --- HANDLER CHÍNH: ĐĂNG BÀI (ĐÃ THÊM LOGIC BAN IP) ---
    static class PostHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod())) {
                String userIP = getClientIP(t);
                long currentTime = System.currentTimeMillis();

                // --- BƯỚC 1: KIỂM TRA SỔ ĐEN ---
                if (bannedIps.containsKey(userIP)) {
                    long releaseTime = bannedIps.get(userIP);
                    if (currentTime < releaseTime) {
                        // Vẫn chưa hết hạn phạt -> CHẶN
                        long secondsLeft = (releaseTime - currentTime) / 1000;
                        String errorMsg = "🚫 BAN IP: Bạn bị cấm chat do ngôn từ không phù hợp! Quay lại sau "
                                + secondsLeft + " giây.";
                        t.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
                        t.sendResponseHeaders(403, errorMsg.getBytes(StandardCharsets.UTF_8).length);
                        OutputStream os = t.getResponseBody();
                        os.write(errorMsg.getBytes(StandardCharsets.UTF_8));
                        os.close();
                        return; // Dừng ngay, không cho đi tiếp
                    } else {
                        // Hết hạn -> Xóa tên khỏi sổ đen
                        bannedIps.remove(userIP);
                    }
                }

                // --- BƯỚC 2: XỬ LÝ NỘI DUNG ---
                String body = getRequestBody(t);
                if (body.startsWith("thought=")) {
                    String raw = body.split("thought=")[1];
                    String content = URLDecoder.decode(raw, StandardCharsets.UTF_8.name());

                    // --- BƯỚC 3: KIỂM TRA TỪ CẤM ---
                    boolean isBad = false;
                    for (String badWord : BAD_WORDS) {
                        if (content.toLowerCase().contains(badWord)) {
                            isBad = true;
                            break;
                        }
                    }

                    if (isBad) {
                        // PHÁT HIỆN TỪ CẤM -> BAN NGAY LẬP TỨC
                        bannedIps.put(userIP, currentTime + BAN_DURATION);

                        String banMsg = "😡 PHÁT HIỆN TỪ CẤM! IP của bạn đã bị khóa 5 phút.";
                        t.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
                        t.sendResponseHeaders(403, banMsg.getBytes(StandardCharsets.UTF_8).length);
                        OutputStream os = t.getResponseBody();
                        os.write(banMsg.getBytes(StandardCharsets.UTF_8));
                        os.close();
                        return; // Dừng, không lưu bài
                    }

                    // Nếu sạch sẽ -> Lưu bài
                    // Encode lại HTML để chống XSS
                    content = content.replace("<", "&lt;").replace(">", "&gt;");

                    if (communityPosts.size() >= 50)
                        communityPosts.remove(0);
                    communityPosts.add(new Post(postIdCounter++, content, 0, 0));
                    savePostsToFile();
                }
            }
            redirectHome(t);
        }
    }

    static class HomeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String html = getHeader("Sảnh Chính") +
                    "<div class='container'>" +
                    "  <h1>🏛️ CÁNH CỬA TRIẾT HỌC</h1>" +
                    "  <p>Chào mừng lữ khách. Bạn muốn bước vào cánh cửa nào hôm nay?</p>" +
                    "  <div class='nav-grid'>" +
                    "    <a href='/room?type=stoic' class='card choice'>🛡️ Khắc Kỷ</a>" +
                    "    <a href='/room?type=exist' class='card choice'>🌑 Hiện Sinh</a>" +
                    "    <a href='/room?type=eastern' class='card choice'>🎋 Phương Đông</a>" +
                    "  </div>" +
                    "  <br><hr><br>" +
                    "  <h2>📜 Bức Tường Cộng Đồng</h2>" +
                    "  <div class='post-input-area'>" +
                    "     <form action='/post' method='post' class='post-form'>" +
                    "       <input type='text' name='thought' placeholder='Bạn đang suy ngẫm điều gì?' required autocomplete='off'>"
                    +
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

    // --- UI HELPERS ---
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
                    .append("    <form action='/dislike' method='post' style='display:inline'>")
                    .append("      <input type='hidden' name='id' value='").append(p.id).append("'>")
                    .append("      <button type='submit' class='btn-dislike'>💔 ").append(p.dislikes)
                    .append("</button>")
                    .append("    </form>")
                    .append("    <button type='button' onclick='hidePost(").append(p.id)
                    .append(")' class='btn-hide'>🙈 Ẩn</button>")
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
            String title = "Phòng Triết Học";
            String html = getHeader(title) +
                    "<div class='container'><a href='/' class='back-btn'>⬅ Quay lại</a><h1>" + title + "</h1>" +
                    "<div class='quote-card'><p class='quote'>\"" + w.quote + "\"</p><p class='author'>— " + w.author
                    + "</p></div>" +
                    "<button onclick='window.location.reload()' class='btn-reload'>✨ Câu khác</button></div>"
                    + getFooter();
            sendResponse(t, html);
        }
    }

    private static String getRequestBody(HttpExchange t) throws IOException {
        InputStreamReader isr = new InputStreamReader(t.getRequestBody(), StandardCharsets.UTF_8);
        BufferedReader br = new BufferedReader(isr);
        return br.readLine();
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
        // 1. Trường phái Khắc Kỷ (Stoicism) - Rèn luyện tâm trí vững vàng
        schools.put("stoic", Arrays.asList(
                new Wisdom("Chúng ta khổ sở trong tưởng tượng nhiều hơn thực tế.", "Seneca"),
                new Wisdom("Không gì làm hại bạn nếu bạn không cho phép.", "Marcus Aurelius"),
                new Wisdom("Hãy tập trung vào những gì bạn có thể kiểm soát.", "Epictetus"),
                new Wisdom("Người nghèo không phải là người có ít, mà là người khao khát nhiều hơn.", "Seneca"),
                new Wisdom("Đừng mong mọi chuyện xảy ra theo ý mình, hãy mong nó xảy ra như nó vốn có.", "Epictetus"),
                new Wisdom("Cách trả thù tốt nhất là đừng trở nên giống kẻ thù của mình.", "Marcus Aurelius"),
                new Wisdom("Không phải sự việc làm ta rối trí, mà là cách ta nhìn nhận nó.", "Epictetus"),
                new Wisdom("Hạnh phúc của đời bạn phụ thuộc vào chất lượng những suy nghĩ của bạn.", "Marcus Aurelius"),
                new Wisdom("Hãy sống mỗi ngày như thể đó là ngày cuối cùng của đời bạn.", "Seneca")));

        // 2. Trường phái Hiện Sinh (Existentialism) - Tự do và Ý nghĩa
        schools.put("exist", Arrays.asList(
                new Wisdom("Con người bị kết án phải tự do.", "Jean-Paul Sartre"),
                new Wisdom("Phải tưởng tượng Sisyphus hạnh phúc.", "Albert Camus"),
                new Wisdom("Người có lý do để sống có thể chịu đựng bất kỳ nghịch cảnh nào.", "Friedrich Nietzsche"),
                new Wisdom("Địa ngục chính là người khác.", "Jean-Paul Sartre"),
                new Wisdom("Giữa mùa đông khắc nghiệt, tôi nhận ra trong mình có một mùa hè bất diệt.", "Albert Camus"),
                new Wisdom("Cuộc đời chỉ có thể được hiểu khi nhìn lại, nhưng phải được sống khi nhìn về phía trước.",
                        "Søren Kierkegaard"),
                new Wisdom("Mọi thứ đều có thể bị tước đoạt... trừ một thứ: quyền lựa chọn thái độ.", "Viktor Frankl"),
                new Wisdom("Lo âu là sự chóng mặt của tự do.", "Søren Kierkegaard")));

        // 3. Triết học Phương Đông (Eastern) - An nhiên và Tỉnh thức
        schools.put("eastern", Arrays.asList(
                new Wisdom("Biết người là trí, biết mình là sáng.", "Lão Tử"), // Đạo Đức Kinh
                new Wisdom("Đời là bể khổ, quay đầu là bờ.", "Phật Giáo"), // Tứ diệu đế
                new Wisdom("Hành trình vạn dặm bắt đầu từ một bước chân.", "Lão Tử"),
                new Wisdom("Sống chậm lại để cảm nhận sâu hơn.", "Khuyết danh"), // Câu này mang tính hiện đại
                                                                                 // (Lifestyle), không thuộc kinh điển
                                                                                 // Thiền tông
                new Wisdom("Hãy để tâm hồn bạn như mặt hồ yên tĩnh, phản chiếu mọi sự vật mà không bị dao động.",
                        "Tục ngữ Thiền"), // Hoặc "Zen Proverb"
                new Wisdom("Dĩ bất biến, ứng vạn biến.", "Hồ Chí Minh"), // Sửa lại cho đúng lịch sử (hoặc để "Triết lý
                                                                         // Đạo gia")
                new Wisdom("Không quan trọng việc bạn đi chậm thế nào, miễn là đừng bao giờ dừng lại.", "Khổng Tử"),
                new Wisdom("Quá khứ đã qua, tương lai chưa tới, chỉ có khoảnh khắc hiện tại là thực.",
                        "Thích Ca Mâu Ni")));
    }

    private static String getHeader(String title) {
        return "<!DOCTYPE html><html lang='vi'><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'><title>"
                + title + "</title>" +
                "<style>" +
                "@import url('https://fonts.googleapis.com/css2?family=Merriweather:ital,wght@0,300;0,700;1,300&family=Montserrat:wght@400;600&display=swap');"
                +
                ":root { --bg: #0f172a; --card: #1e293b; --text: #e2e8f0; --gold: #fbbf24; }" +
                "body { background-color: var(--bg); color: var(--text); font-family: 'Montserrat', sans-serif; margin: 0; padding: 20px; line-height: 1.6; }"
                +
                ".container { max-width: 600px; margin: 0 auto; text-align: center; }" +
                "h1 { font-family: 'Merriweather', serif; color: var(--gold); }" +
                ".nav-grid { display: flex; gap: 10px; justify-content: center; flex-wrap: wrap; }" +
                ".choice { background: var(--card); padding: 10px 20px; border-radius: 8px; border: 1px solid #334155; text-decoration: none; color: white; transition: 0.2s; }"
                +
                ".choice:hover { border-color: var(--gold); transform: translateY(-2px); }" +
                ".wall { text-align: left; margin-top: 20px; }" +
                ".wall-msg { background: rgba(255,255,255,0.05); padding: 15px; margin-bottom: 15px; border-radius: 10px; border-left: 3px solid var(--gold); transition: all 0.5s ease; }"
                +
                ".msg-content { font-family: 'Merriweather', serif; margin-bottom: 10px; font-size: 1.1em; word-wrap: break-word; }"
                +
                ".msg-actions { display: flex; gap: 10px; align-items: center; }" +
                ".btn-like { background: none; border: 1px solid #ef4444; color: #ef4444; padding: 5px 12px; border-radius: 15px; cursor: pointer; transition: 0.2s; }"
                +
                ".btn-like:hover { background: #ef4444; color: white; }" +
                ".btn-dislike { background: none; border: 1px solid #64748b; color: #94a3b8; padding: 5px 12px; border-radius: 15px; cursor: pointer; transition: 0.2s; }"
                +
                ".btn-dislike:hover { border-color: #cbd5e1; color: #fff; }" +
                ".btn-hide { background: none; border: none; color: #475569; padding: 5px 12px; cursor: pointer; font-size: 0.9em; }"
                +
                ".btn-hide:hover { color: #94a3b8; }" +
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
                "</style>" +
                "<script>" +
                // Vẫn giữ script ẩn bài cũ
                "document.addEventListener('DOMContentLoaded', function() {" +
                "  var hiddenList = JSON.parse(localStorage.getItem('hidden_posts') || '[]');" +
                "  hiddenList.forEach(function(id) {" +
                "      var el = document.getElementById('post-' + id);" +
                "      if(el) el.style.display = 'none';" +
                "  });" +
                "});" +
                "function hidePost(id) {" +
                "  var element = document.getElementById('post-' + id);" +
                "  if(element) {" +
                "      element.style.opacity = '0';" +
                "      setTimeout(function(){ element.style.display = 'none'; }, 500);" +
                "      var hiddenList = JSON.parse(localStorage.getItem('hidden_posts') || '[]');" +
                "      if (!hiddenList.includes(id)) {" +
                "          hiddenList.push(id);" +
                "          localStorage.setItem('hidden_posts', JSON.stringify(hiddenList));" +
                "      }" +
                "  }" +
                "}" +
                "</script></head><body>";
    }

    private static String getFooter() {
        return "<br><br><p style='text-align:center; color:#475569; font-size:0.8rem'>He he he</p></body></html>";
    }
}