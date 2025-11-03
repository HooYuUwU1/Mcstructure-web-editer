import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import org.jnbt.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;

public class NBTEditor {

    public static void main(String[] args) throws Exception {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Trang chủ
        server.createContext("/", exchange -> {
            String html =
                "<!DOCTYPE html><html><head><meta charset='utf-8'><title>NBT Web Editor</title>" +
                "<style>body{font-family:monospace;background:#111;color:#eee;padding:10px;}" +
                "a,button{color:#4cf;text-decoration:none;background:#222;border:1px solid #333;padding:5px 10px;border-radius:4px;cursor:pointer;}" +
                "span.editable{background:#222;padding:2px;border-radius:4px;cursor:text;}" +
                "</style></head><body>" +
                "<h1>NBT Web Editor</h1>" +
                "<a href='/read?file=input.mcstructure'>📂 Mở input.mcstructure</a>" +
                "</body></html>";
            sendResponse(exchange, html, "text/html", 200);
        });

        // Đọc file
        server.createContext("/read", exchange -> {
            Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
            String filename = params.get("file");
            if (filename == null) {
                sendResponse(exchange, "Thiếu tham số file", "text/plain", 400);
                return;
            }
            File file = new File(filename);
            if (!file.exists()) {
                sendResponse(exchange, "Không tìm thấy file: " + filename, "text/plain", 404);
                return;
            }

            try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
                NBTInputStreamLE nis = new NBTInputStreamLE(dis);
                Tag tag = nis.readTag();
                String nbtHtml = renderTag(tag);

                String html =
                    "<!DOCTYPE html><html><head><meta charset='utf-8'><title>" + filename + "</title>" +
                    "<style>body{font-family:monospace;background:#111;color:#eee;padding:10px;}" +
                    "ul{list-style-type:none;} li{margin-left:20px;}" +
                    "span.editable{background:#222;padding:2px;border-radius:4px;cursor:text;}" +
                    "button{margin:10px 0;background:#2a2;border:1px solid #4c4;padding:6px 12px;border-radius:4px;cursor:pointer;}" +
                    "</style></head><body>" +
                    "<h2>" + filename + "</h2>" +
                    "<form id='nbtForm'>" +
                    "<ul id='nbtData'>" + nbtHtml + "</ul>" +
                    "<button type='button' onclick='saveNBT()'>💾 Lưu thay đổi</button>" +
                    "</form>" +
                    "<a href='/'>⬅️ Quay lại</a>" +
                    "<script>" +
                    "document.addEventListener('click',function(e){" +
                    "if(e.target.classList.contains('editable')){" +
                    "var t=e.target;var v=prompt('Nhập giá trị mới:',t.innerText);" +
                    "if(v!==null)t.innerText=v;}" +
                    "});" +
                    "function saveNBT(){" +
                    "var text=document.getElementById('nbtData').innerText;" +
                    "fetch('/save',{method:'POST',headers:{'Content-Type':'text/plain'},body:text})" +
                    ".then(r=>r.text()).then(t=>alert(t)).catch(e=>alert('Lỗi: '+e));" +
                    "}" +
                    "</script></body></html>";

                sendResponse(exchange, html, "text/html", 200);
            } catch (Exception e) {
                sendResponse(exchange, "Lỗi đọc file: " + e.getMessage(), "text/plain", 500);
            }
        });

        // Ghi file text tạm
        server.createContext("/save", exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, "Chỉ hỗ trợ POST", "text/plain", 405);
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
            try (FileWriter fw = new FileWriter("edited_nbt.txt")) {
                fw.write(body);
            }
            sendResponse(exchange, "✅ Đã lưu tạm vào edited_nbt.txt", "text/plain", 200);
        });

        server.start();
        System.out.println("✅ Server đã chạy tại http://localhost:" + port);
    }

    // Hiển thị cây NBT
    private static String renderTag(Tag tag) {
        StringBuilder sb = new StringBuilder();
        if (tag instanceof CompoundTag) {
            sb.append("<li><b>").append(tag.getName()).append("</b>: {<ul>");
            for (Map.Entry<String, Tag> entry : ((CompoundTag) tag).getValue().entrySet()) {
                sb.append(renderTag(entry.getValue()));
            }
            sb.append("</ul>}</li>");
        } else if (tag instanceof ListTag) {
            sb.append("<li><b>").append(tag.getName()).append("</b>: [<ul>");
            for (Tag t : ((ListTag) tag).getValue()) {
                sb.append(renderTag(t));
            }
            sb.append("</ul>]</li>");
        } else if (tag instanceof ByteArrayTag) {
            sb.append("<li>").append(tag.getName()).append(": <span class='editable'>[byte[" + ((ByteArrayTag) tag).getValue().length + "]]</span></li>");
        } else if (tag instanceof IntArrayTag) {
            sb.append("<li>").append(tag.getName()).append(": <span class='editable'>[int[" + ((IntArrayTag) tag).getValue().length + "]]</span></li>");
        } else if (tag instanceof LongArrayTag) {
            sb.append("<li>").append(tag.getName()).append(": <span class='editable'>[long[" + ((LongArrayTag) tag).getValue().length + "]]</span></li>");
        } else {
            sb.append("<li>").append(tag.getName()).append(": <span class='editable'>").append(tag.getValue()).append("</span></li>");
        }
        return sb.toString();
    }

    private static Map<String, String> queryToMap(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null) return map;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=");
            if (kv.length == 2) map.put(kv[0], kv[1]);
        }
        return map;
    }

    private static void sendResponse(HttpExchange exchange, String response, String contentType, int statusCode) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType + "; charset=utf-8");
        byte[] bytes = response.getBytes("UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

}
