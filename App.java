import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

public class App {
    private static final String DB_URL = "jdbc:sqlite:todos.db"; // ★ ファイル保存版から変更: SQLite接続先を追加

    public static void main(String[] args) throws Exception {
        Class.forName("org.sqlite.JDBC"); // ★ SQLite JDBCドライバ（SQLite接続用部品）を読み込む
        initializeDatabase(); // ★ ファイル保存版から変更: 起動時にtodos表を用意

        HttpServer server = null;
        int port = 8080;
        for (int candidate = 8080; candidate <= 8090; candidate++) { // ★ 使用中なら別のポートを試す
            try {
                server = HttpServer.create(new InetSocketAddress(candidate), 0); // ★ 空いているポートで起動
                port = candidate;
                break;
            } catch (BindException ignored) {
                // ポートが使用中なら次のポートを試す
            }
        }
        if (server == null) {
            throw new IllegalStateException("8080～8090番ポートがすべて使用中です。");
        }
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if (path.equals("/add") && method.equals("POST")) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> form = parseParams(body);
                String title = form.getOrDefault("todo", "");
                String due = form.getOrDefault("due", "");
                String category = form.getOrDefault("category", ""); // ★ カテゴリ／タグを受け取る
                if (!title.isEmpty()) {
                    addTodo(title, due, category); // ★ INSERTでカテゴリも保存
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
            } else if (path.equals("/edit") && method.equals("POST")) { // ★ Todoタイトル編集を追加
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> form = parseParams(body);
                Integer id = parseInteger(form.get("id"));
                String title = form.getOrDefault("title", "");
                if (id != null && !title.isEmpty()) {
                    editTodo(id, title); // ★ UPDATEでタイトルを編集
                }
                redirect(exchange);
                return;
            } else if (path.equals("/")) {
                String html = "<!doctype html><html><head><meta charset='UTF-8'>"
                        + "<title>私のTodoリスト</title>"
                        + "<style>"
                        + "* { box-sizing: border-box; }"
                        + "body { max-width: 680px; margin: 0 auto; padding: 48px 20px; "
                        + "font-family: system-ui, sans-serif; font-size: 16px; color: #24324a; "
                        + "background: #f4f7fb; }"
                        + "main { background: white; padding: 32px; border-radius: 18px; "
                        + "box-shadow: 0 8px 24px rgba(36, 50, 74, .10); }"
                        + "h1 { margin: 0 0 24px; color: #1d3557; font-size: 32px; }"
                        + "form { display: flex; gap: 8px; margin-bottom: 24px; }"
                        + "input { flex: 1; min-width: 0; padding: 11px 13px; border: 1px solid #cbd5e1; "
                        + "border-radius: 9px; font-size: 16px; }"
                        + "input:focus { outline: 2px solid #93c5fd; border-color: #3b82f6; }"
                        + "button { padding: 10px 18px; border: 0; border-radius: 9px; "
                        + "background: #3b82f6; color: white; font-size: 16px; cursor: pointer; }"
                        + "button:hover { background: #2563eb; }"
                        + "ul { padding: 0; margin: 0; list-style: none; }"
                        + "li { margin: 10px 0; padding: 14px 16px; background: #f8fafc; "
                        + "border: 1px solid #e2e8f0; border-radius: 10px; }"
                        + "li a { margin-left: 8px; color: #2563eb; font-size: 14px; }"
                        + ".tag { display: inline-block; margin-left: 8px; padding: 2px 8px; "
                        + "border-radius: 999px; background: #fff1e6; color: #c2410c; font-size: 13px; }"
                        + ".tools { margin-bottom: 20px; color: #64748b; line-height: 2; }"
                        + ".tools form { margin-bottom: 8px; }"
                        + "li form { margin: 0; } li form input, li form button { font-size: 13px; }"
                        + "p { padding: 16px; color: #64748b; background: #f8fafc; border-radius: 10px; }"
                        + "</style>"
                        + "</head><body><main><h1>私のTodoリスト</h1>"
                        + "<form method='post' action='/add'><input name='todo' placeholder='やること'>"
                        + "<input name='category' placeholder='カテゴリ／タグ'>"
                        + "<input type='date' name='due'><button>追加</button></form>"
                        + filterAndSortLinks(exchange.getRequestURI().getQuery()); // ★ 絞り込み・並べ替えを追加
                html += listTodos(exchange.getRequestURI().getQuery()); // ★ 検索・絞り込み・並べ替え付き一覧
                html += "</main></body></html>";
                send(exchange, 200, html, "text/html; charset=UTF-8");
                return;
            }

            send(exchange, 404, "ページが見つかりません", "text/plain; charset=UTF-8");
        });

        server.start();
        System.out.println("サーバー起動: http://localhost:" + port + " (停止するとき: Ctrl+C)"); // ★ 実際のポートを表示
    }

    private static void initializeDatabase() throws SQLException {
        try (Connection connection = DriverManager.getConnection(DB_URL); // ★ ファイル保存版から変更: SQLiteへ接続
                PreparedStatement statement = connection.prepareStatement(
                        "CREATE TABLE IF NOT EXISTS todos (id INTEGER PRIMARY KEY, title TEXT, done INTEGER, due TEXT, category TEXT)")) { // ★ カテゴリ列を追加
            statement.executeUpdate(); // ★ ファイル保存版から変更: PreparedStatementで実行
        }
        try (Connection connection = DriverManager.getConnection(DB_URL); // ★ 既存DBにも締め切り列を追加
                PreparedStatement statement = connection.prepareStatement(
                        "ALTER TABLE todos ADD COLUMN due TEXT")) {
            statement.executeUpdate();
        } catch (SQLException ignored) {
            // 既にdue列がある場合は何もしない
        }
        try (Connection connection = DriverManager.getConnection(DB_URL); // ★ 既存DBにもカテゴリ列を追加
                PreparedStatement statement = connection.prepareStatement(
                        "ALTER TABLE todos ADD COLUMN category TEXT")) {
            statement.executeUpdate();
        } catch (SQLException ignored) {
            // 既にcategory列がある場合は何もしない
        }
    }

    private static void addTodo(String title, String due, String category) {
        try (Connection connection = DriverManager.getConnection(DB_URL); // ★ ファイル保存版から変更
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO todos (title, done, due, category) VALUES (?, ?, ?, ?)")) { // ★ INSERTにカテゴリを追加
            statement.setString(1, title); // ★ ファイル保存版から変更
            statement.setInt(2, 0); // ★ ファイル保存版から変更
            statement.setString(3, due); // ★ 締め切りを保存
            statement.setString(4, category); // ★ カテゴリ／タグを保存
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

    private static String listTodos(String query) {
        Map<String, String> params = parseParams(query);
        String filter = params.getOrDefault("filter", "all");
        String sort = params.getOrDefault("sort", "new");
        String keyword = params.getOrDefault("q", "");
        String category = params.getOrDefault("category", "");
        StringBuilder sql = new StringBuilder("SELECT id, title, done, due, category FROM todos WHERE 1=1");
        if (filter.equals("todo")) sql.append(" AND done = 0");
        if (filter.equals("done")) sql.append(" AND done = 1");
        if (!keyword.isEmpty()) sql.append(" AND title LIKE ?");
        if (!category.isEmpty()) sql.append(" AND category = ?");
        sql.append(sort.equals("name") ? " ORDER BY title COLLATE NOCASE, id" : " ORDER BY id DESC");

        StringBuilder html = new StringBuilder();
        int count = 0;
        try (Connection connection = DriverManager.getConnection(DB_URL); // ★ SELECT条件を追加
                PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int parameterIndex = 1;
            if (!keyword.isEmpty()) statement.setString(parameterIndex++, "%" + keyword + "%");
            if (!category.isEmpty()) statement.setString(parameterIndex, category);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    if (count++ == 0) html.append("<ul>");
                    int id = resultSet.getInt("id");
                    String title = escapeHtml(resultSet.getString("title"));
                    boolean done = resultSet.getInt("done") != 0;
                    String due = resultSet.getString("due");
                    String todoCategory = resultSet.getString("category");
                    String mark = done ? " ✓" : "";
                    String dueText = due == null || due.isEmpty() ? "" : "（締切: " + escapeHtml(due) + "）";
                    String categoryText = todoCategory == null || todoCategory.isEmpty()
                            ? "" : " <span class='tag'>#" + escapeHtml(todoCategory) + "</span>";
                    html.append("<li>").append(title).append(mark).append(dueText).append(categoryText)
                            .append(" <a href='/done?id=").append(id).append("'>完了</a>")
                            .append(" <a href='/delete?id=").append(id).append("'>削除</a>")
                            .append(" <form method='post' action='/edit' style='display:inline'>")
                            .append("<input type='hidden' name='id' value='").append(id).append("'>")
                            .append("<input name='title' value='").append(title).append("' size='12'>")
                            .append("<button>編集</button></form></li>");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return count == 0 ? "<p>やることは、いまゼロです</p>" : html.append("</ul>").toString();
    }

    private static String filterAndSortLinks(String query) {
        Map<String, String> params = parseParams(query);
        String q = params.getOrDefault("q", "");
        String filter = params.getOrDefault("filter", "all");
        String sort = params.getOrDefault("sort", "new");
        String category = params.getOrDefault("category", "");
        String encodedQ = java.net.URLEncoder.encode(q, StandardCharsets.UTF_8);
        String encodedCategory = java.net.URLEncoder.encode(category, StandardCharsets.UTF_8);
        String base = "&q=" + encodedQ + "&category=" + encodedCategory;
        return "<div class='tools'><form method='get'><input name='q' value='" + escapeHtml(q)
                + "' placeholder='キーワード検索'><input name='category' value='" + escapeHtml(category)
                + "' placeholder='カテゴリで絞り込み'><button>検索</button></form>"
                + "表示: <a href='/?filter=all&sort=" + sort + base + "'>全て</a> "
                + "<a href='/?filter=todo&sort=" + sort + base + "'>未完了</a> "
                + "<a href='/?filter=done&sort=" + sort + base + "'>完了済み</a>　"
                + "並び順: <a href='/?filter=" + filter + "&sort=new" + base + "'>新しい順</a> "
                + "<a href='/?filter=" + filter + "&sort=name" + base + "'>名前順</a></div>";
    }

    private static void editTodo(int id, String title) {
        try (Connection connection = DriverManager.getConnection(DB_URL);
                PreparedStatement statement = connection.prepareStatement("UPDATE todos SET title = ? WHERE id = ?")) {
            statement.setString(1, title);
            statement.setInt(2, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static Integer getId(String query) {
        return parseInteger(parseParams(query).get("id"));
    }

    private static Integer parseInteger(String value) {
        try {
            return value == null ? null : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Map<String, String> parseParams(String text) {
        Map<String, String> params = new LinkedHashMap<>();
        if (text == null || text.isEmpty()) return params;
        for (String pair : text.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            params.put(key, value);
        }
        return params;
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
