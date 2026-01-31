import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SimpleWebServer {

    // Class lưu dữ liệu triết học
    static class Wisdom {
        String quote;
        String author;
        String prompt;

        public Wisdom(String quote, String author, String prompt) {
            this.quote = quote;
            this.author = author;
            this.prompt = prompt;
        }
    }

    // Danh sách dữ liệu
    private static final List<Wisdom> library = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        // 1. Nạp dữ liệu
        initData();

        // 2. Tạo máy chủ web tại cổng 8080
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // 3. Tạo đường dẫn (route). Khi vào trang chủ "/" sẽ chạy hàm MyHandler
        server.createContext("/", new MyHandler());

        server.setExecutor(null); // Tạo executor mặc định
        server.start();

        System.out.println("Máy chủ đang chạy!");
        System.out.println("Hãy mở trình duyệt và truy cập: http://localhost:" + port);
    }

    // Class xử lý yêu cầu từ trình duyệt
    static class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            // Lấy ngẫu nhiên một câu nói
            Random rand = new Random();
            Wisdom w = library.get(rand.nextInt(library.size()));

            // Tạo nội dung HTML (Nhúng CSS và HTML vào trong Java)
            String response = getHtmlContent(w);

            // Gửi phản hồi về trình duyệt (UTF-8 để hiển thị tiếng Việt)
            t.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            t.sendResponseHeaders(200, bytes.length);

            OutputStream os = t.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    private static void initData() {
        library.add(new Wisdom("Cuộc đời không phải được kiểm nghiệm thì không đáng sống.", "Socrates",
                "Hãy viết về một niềm tin mà bạn giữ, nhưng chưa bao giờ tự hỏi tại sao."));
        library.add(new Wisdom("Tôi tư duy, nên tôi tồn tại.", "René Descartes",
                "Nếu ký ức bị xóa sạch, điều gì chứng minh bạn là bạn?"));
        library.add(new Wisdom("Địa ngục chính là người khác.", "Jean-Paul Sartre",
                "Tưởng tượng thế giới chỉ có mình bạn. Tự do hay Kinh hoàng?"));
        library.add(new Wisdom("Không ai tắm hai lần trên một dòng sông.", "Heraclitus",
                "Bạn của ngày hôm qua và hôm nay khác nhau thế nào?"));
        library.add(new Wisdom("Hạnh phúc là muốn những gì mình làm.", "Leo Tolstoy",
                "Tìm ý nghĩa trong một việc nhàm chán bạn phải làm hôm nay."));
    }

    // Hàm trả về mã HTML cho trang web
    private static String getHtmlContent(Wisdom w) {
        return "<!DOCTYPE html>" +
                "<html lang='vi'>" +
                "<head>" +
                "<meta charset='UTF-8'>" +
                "<title>Nhà Tiên Tri Triết Học (Java Web)</title>" +
                "<style>" +
                "body { font-family: 'Segoe UI', sans-serif; background: #2c3e50; color: white; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; }"
                +
                ".card { background: rgba(255,255,255,0.1); padding: 40px; border-radius: 15px; max-width: 600px; text-align: center; backdrop-filter: blur(10px); box-shadow: 0 4px 30px rgba(0,0,0,0.5); border: 1px solid rgba(255,255,255,0.2); }"
                +
                "h1 { color: #f1c40f; margin-bottom: 30px; }" +
                ".quote { font-size: 1.5em; font-style: italic; margin-bottom: 20px; line-height: 1.4; }" +
                ".author { font-weight: bold; color: #bdc3c7; text-transform: uppercase; margin-bottom: 40px; display: block; }"
                +
                ".prompt-box { background: rgba(0,0,0,0.2); padding: 20px; border-left: 4px solid #e67e22; text-align: left; border-radius: 5px; }"
                +
                ".label { color: #e67e22; font-weight: bold; font-size: 0.9em; display: block; margin-bottom: 5px; }" +
                "button { margin-top: 30px; padding: 15px 30px; font-size: 1em; background: #e67e22; color: white; border: none; border-radius: 50px; cursor: pointer; transition: 0.3s; }"
                +
                "button:hover { background: #d35400; transform: scale(1.05); }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "  <div class='card'>" +
                "    <h1>Java Philosophy Web</h1>" +
                "    <div class='quote'>\"" + w.quote + "\"</div>" +
                "    <span class='author'>- " + w.author + "</span>" +
                "    <div class='prompt-box'>" +
                "       <span class='label'>CHỦ ĐỀ SUY NGẪM:</span>" +
                "       " + w.prompt +
                "    </div>" +
                "    <form action='/' method='get'>" +
                "       <button type='submit'>Tìm Kiếm Minh Triết Mới</button>" +
                "    </form>" +
                "  </div>" +
                "</body>" +
                "</html>";
    }
}