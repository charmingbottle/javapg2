import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class App {
    private static final String DB_URL = "jdbc:sqlite:todos.db"; // ★ ファイル保存版から変更: SQLite接続先を追加

    public static void main(String[] args) throws Exception {
        Class.forName("org.sqlite.JDBC"); // ★ SQLite JDBCドライバ（SQLite接続用部品）を読み込む
        initializeDatabase(); // ★ ファイル保存版から変更: 起動時にtodos表を用意

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if (path.equals("/add") && method.equals("POST")) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String title = body.startsWith("todo=")
                        ? URLDecoder.decode(body.substring(5), StandardCharsets.UTF_8)
                        : "";
                if (!title.isEmpty()) {
                    addTodo(title); // ★ ファイル保存版から変更: INSERTで追加
                }
                redirect(exchange);
                return;
            } else if (path.equals("/done") && method.equals("GET")) {
                Integer id = getId(exchange.getRequestURI().getQuery());
                if (id != null) {
                    completeTodo(id); // ★ ファイル保存版から変更: UPDATEで完了
                }
                redirect(exchange);
                return;
            } else if (path.equals("/delete") && method.equals("GET")) {
                Integer id = getId(exchange.getRequestURI().getQuery());
                if (id != null) {
                    deleteTodo(id); // ★ ファイル保存版から変更: DELETEで削除
                }
                redirect(exchange);
                return;
            } else if (path.equals("/")) {
                String html = "<!doctype html><html><head><meta charset='UTF-8'>"
                        + "<title>私のTodoリスト</title>"
                        + "<style>body { max-width: 640px; margin: 24px auto; padding: 0 16px; font-size: 16px; } "
                        + "input { font-size: 16px; } button { font-size: 16px; }</style>"
                        + "</head><body><h1>私のTodoリスト</h1>"
                        + "<form method='post' action='/add'><input name='todo'>"
                        + "<button>追加</button></form>"; // ★ 見出しと最小限のstyle（見た目の指定）を追加
                html += listTodos(); // ★ ファイル保存版から変更: SELECTで一覧を取得
                html += "</body></html>";
                send(exchange, 200, html, "text/html; charset=UTF-8");
                return;
            }

            send(exchange, 404, "ページが見つかりません", "text/plain; charset=UTF-8");
        });

        server.start();
        System.out.println("サーバー起動: http://localhost:8080 (停止するとき: Ctrl+C)");
    }

    private static void initializeDatabase() throws SQLException {
        try (Connection connection = DriverManager.getConnection(DB_URL); // ★ ファイル保存版から変更: SQLiteへ接続
                PreparedStatement statement = connection.prepareStatement(
                        "CREATE TABLE IF NOT EXISTS todos (id INTEGER PRIMARY KEY, title TEXT, done INTEGER)")) { // ★
                                                                                                                  // ファイル保存版から変更:
                                                                                                                  // todos表を作成
            statement.executeUpdate(); // ★ ファイル保存版から変更: PreparedStatementで実行
        }
    }

    private static void addTodo(String title) {
        try (Connection connection = DriverManager.getConnection(DB_URL); // ★ ファイル保存版から変更
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO todos (title, done) VALUES (?, ?)")) { // ★ ファイル保存版から変更
            statement.setString(1, title); // ★ ファイル保存版から変更
            statement.setInt(2, 0); // ★ ファイル保存版から変更
            statement.executeUpdate(); // ★ ファイル保存版から変更
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void completeTodo(int id) {
        try (Connection connection = DriverManager.getConnection(DB_URL); // ★ ファイル保存版から変更
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE todos SET done = ? WHERE id = ?")) { // ★ ファイル保存版から変更
            statement.setInt(1, 1); // ★ ファイル保存版から変更
            statement.setInt(2, id); // ★ ファイル保存版から変更
            statement.executeUpdate(); // ★ ファイル保存版から変更
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void deleteTodo(int id) {
        try (Connection connection = DriverManager.getConnection(DB_URL); // ★ ファイル保存版から変更
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM todos WHERE id = ?")) { // ★ ファイル保存版から変更
            statement.setInt(1, id); // ★ ファイル保存版から変更
            statement.executeUpdate(); // ★ ファイル保存版から変更
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static String listTodos() {
        StringBuilder html = new StringBuilder();
        int count = 0; // ★ 0件かどうかを確認するための数
        try (Connection connection = DriverManager.getConnection(DB_URL); // ★ ファイル保存版から変更
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT id, title, done FROM todos ORDER BY id"); // ★ ファイル保存版から変更
                ResultSet resultSet = statement.executeQuery()) { // ★ ファイル保存版から変更
            while (resultSet.next()) { // ★ ファイル保存版から変更
                if (count == 0) { // ★ 一覧があるときだけul（箇条書き）を開始
                    html.append("<ul>");
                }
                count++; // ★ Todoの件数を数える
                int id = resultSet.getInt("id"); // ★ ファイル保存版から変更
                String title = escapeHtml(resultSet.getString("title")); // ★ ファイル保存版から変更
                boolean done = resultSet.getInt("done") != 0; // ★ ファイル保存版から変更
                String mark = done ? " ✓" : ""; // ★ ファイル保存版から変更
                html.append("<li>").append(title).append(mark)
                        .append(" <a href='/done?id=").append(id).append("'>完了</a>")
                        .append(" <a href='/delete?id=").append(id).append("'>削除</a></li>"); // ★ ファイル保存版から変更
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        if (count == 0) { // ★ 一覧が0件のときの表示を追加
            return "<p>やることは、いまゼロです</p>"; // ★ 一覧が0件のときの表示を追加
        }
        return html.append("</ul>").toString(); // ★ 一覧があるときだけulを閉じる
    }

    private static Integer getId(String query) {
        if (query != null && query.startsWith("id=") && query.length() > 3) {
            try {
                return Integer.parseInt(query.substring(3));
            } catch (NumberFormatException ignored) {
                // 不正なIDは無視する
            }
        }
        return null;
    }

    private static void redirect(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Location", "/");
        exchange.sendResponseHeaders(303, -1);
        exchange.close();
    }

    private static void send(HttpExchange exchange, int status, String text, String contentType)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.getResponseBody().close();
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
