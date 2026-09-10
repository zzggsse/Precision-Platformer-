import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/**
 * 极简 Maven 抓取器（不依赖 Gradle/Maven）。
 *
 * 用法:
 *   java tools/MavenFetch.java pom    <group:artifact:version>             打印 POM 原文
 *   java tools/MavenFetch.java fetch  <outDir> <coord...>                  下载 jar 到 outDir
 *
 * coord 格式: group:artifact:version[:classifier]
 * 例如: com.badlogicgames.gdx:gdx:1.14.2
 *       com.badlogicgames.gdx:gdx-platform:1.14.2:natives-desktop
 *
 * 仓库按顺序尝试，全部走 JVM 自己的 JSSE（本机 schannel 被拦但 JSSE 正常）。
 */
public final class MavenFetch {

    private static final String[] REPOS = {
        "https://repo.maven.apache.org/maven2",
        "https://maven.aliyun.com/repository/public",
        "https://repo.huaweicloud.com/repository/maven",
    };

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: MavenFetch pom <coord> | MavenFetch fetch <outDir> <coord...>");
            System.exit(2);
        }
        switch (args[0]) {
            case "pom" -> System.out.println(getText(pathOf(new Coord(args[1]), "pom")));
            case "fetch" -> {
                Path out = Paths.get(args[1]);
                Files.createDirectories(out);
                int ok = 0, skipped = 0;
                for (int i = 2; i < args.length; i++) {
                    Coord c = new Coord(args[i]);
                    Path target = out.resolve(c.fileName("jar"));
                    if (Files.exists(target) && Files.size(target) > 0) {
                        System.out.println("skip   " + target.getFileName() + " (" + Files.size(target) + " bytes)");
                        skipped++;
                        continue;
                    }
                    byte[] data = getBytes(pathOf(c, "jar"));
                    Files.write(target, data);
                    System.out.println("ok     " + target.getFileName() + " (" + data.length + " bytes)");
                    ok++;
                }
                System.out.println("--- downloaded=" + ok + " skipped=" + skipped + " -> " + out.toAbsolutePath());
            }
            default -> throw new IllegalArgumentException("unknown mode: " + args[0]);
        }
    }

    // ---------------------------------------------------------------- coords

    record Coord(String group, String artifact, String version, String classifier) {
        Coord(String spec) {
            this(split(spec));
        }

        private static String[] split(String spec) {
            String[] p = spec.split(":");
            if (p.length < 3) throw new IllegalArgumentException("bad coord: " + spec);
            return p;
        }

        private Coord(String[] p) {
            this(p[0], p[1], p[2], p.length > 3 ? p[3] : null);
        }

        String dir() {
            return group.replace('.', '/') + "/" + artifact + "/" + version + "/";
        }

        String fileName(String ext) {
            return artifact + "-" + version + (classifier == null ? "" : "-" + classifier) + "." + ext;
        }
    }

    private static String pathOf(Coord c, String ext) {
        return c.dir() + c.fileName(ext);
    }

    // ---------------------------------------------------------------- network

    private static String getText(String path) throws Exception {
        return new String(getBytes(path), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static byte[] getBytes(String path) throws Exception {
        Exception last = null;
        for (String repo : REPOS) {
            String url = repo + "/" + path;
            try {
                HttpResponse<byte[]> r = HTTP.send(
                        HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                if (r.statusCode() == 200) return r.body();
                last = new IOException("HTTP " + r.statusCode() + " for " + url);
            } catch (Exception e) {
                last = e;
            }
        }
        throw new IOException("all repos failed for " + path, last);
    }
}
