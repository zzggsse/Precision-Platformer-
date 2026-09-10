import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;

/**
 * 极简 HTTP 工具：列目录 / 下载文件。
 *
 * <p><b>为什么需要它</b>：这台机器上 curl 和 PowerShell 的 Invoke-WebRequest 走的是
 * Windows schannel，会被拦掉：
 * <pre>curl: (35) schannel: AcquireCredentialsHandle failed: SEC_E_NO_CREDENTIALS</pre>
 * 而 JVM 自带 JSSE，能正常出网。所以凡是要下载东西，都得走 Java。
 * 配合 {@link MavenFetch}（按 Maven 坐标抓依赖）一起用。
 *
 * <p>用法：
 * <pre>
 *   java tools/HttpGet.java list &lt;url&gt; [子串过滤]
 *   java tools/HttpGet.java get  &lt;url&gt; &lt;输出文件&gt;
 * </pre>
 *
 * <p>典型场景（见 docs/DEVELOPMENT.md 的「在本机推送 GitHub」一节）：
 * <pre>
 *   # 用 DoH 绕开被污染的 DNS，查 github.com 的真实 IP
 *   java tools/HttpGet.java list "https://dns.alidns.com/resolve?name=github.com&amp;type=A"
 *
 *   # 列出 Git for Windows 镜像里有哪些版本
 *   java tools/HttpGet.java list "https://registry.npmmirror.com/-/binary/git-for-windows/" "MinGit"
 *
 *   # 下载
 *   java tools/HttpGet.java get &lt;url&gt; out\MinGit.zip
 * </pre>
 */
public class HttpGet {

    static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: HttpGet list <url> [filter]");
            System.err.println("       HttpGet get  <url> <outfile>");
            System.exit(2);
        }
        switch (args[0]) {
            case "list" -> {
                String filter = args.length > 2 ? args[2] : null;
                HttpResponse<String> r = send(args[1], HttpResponse.BodyHandlers.ofString());
                System.out.println("HTTP " + r.statusCode() + ", " + r.body().length() + " chars");
                for (String line : r.body().split("\n")) {
                    if (filter == null || line.contains(filter)) System.out.println(line.trim());
                }
            }
            case "get" -> {
                Path out = Paths.get(args[2]).toAbsolutePath();
                if (out.getParent() != null) Files.createDirectories(out.getParent());
                HttpResponse<byte[]> r = send(args[1], HttpResponse.BodyHandlers.ofByteArray());
                if (r.statusCode() != 200) {
                    throw new IllegalStateException("HTTP " + r.statusCode() + " for " + args[1]);
                }
                Files.write(out, r.body());
                System.out.printf("downloaded %d bytes -> %s%n", r.body().length, out);
            }
            default -> throw new IllegalArgumentException("unknown mode: " + args[0]);
        }
    }

    static <T> HttpResponse<T> send(String url, HttpResponse.BodyHandler<T> h) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(300))
                .header("User-Agent", "Mozilla/5.0 (compatible; pixelperil-tools)")
                .GET().build(), h);
    }
}
