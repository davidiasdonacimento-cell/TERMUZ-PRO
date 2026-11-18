package com.davidias.termuzpro;

import android.content.Context;
import android.os.Build;
import org.apache.commons.compress.archivers.tar.*;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class LinuxInstaller {
    interface Listener {
        void log(String s);
        void done(boolean ok, String msg);
    }

    private static final String FILE = "ubuntu-base-24.04.5-base-arm64.tar.gz";
    private static final String BASE = "https://cdimages.ubuntu.com/ubuntu-base/releases/24.04/release/";
    private static final Object LOG_LOCK = new Object();

    private LinuxInstaller() {}

    static File root(Context c) {
        return new File(c.getNoBackupFilesDir(), "termuz-linux/rootfs");
    }

    static File logFile(Context c) {
        return new File(c.getNoBackupFilesDir(), "termuz-linux/installer.log");
    }

    private static void saveLog(Context c, String message) {
        synchronized (LOG_LOCK) {
            File f = logFile(c);
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    .format(new Date());
            try (FileWriter w = new FileWriter(f, true)) {
                w.write("[" + stamp + "] " + message + System.lineSeparator());
            } catch (IOException ignored) {
            }
        }
    }

    private static void log(Context c, Listener listener, String message) {
        saveLog(c, message);
        listener.log(message);
    }

    static boolean isInstalled(Context c) {
        File r = root(c);
        return new File(r, "usr/bin/apt-get").isFile()
                && new File(r, "usr/bin/python3").isFile()
                && new File(r, "usr/bin/git").isFile()
                && new File(c.getApplicationInfo().nativeLibraryDir, "libproot.so").isFile()
                && new File(c.getApplicationInfo().nativeLibraryDir, "libproot-loader.so").isFile();
    }

    static Process startShell(Context c) throws IOException {
        return guest(c, root(c), new String[]{"/bin/bash", "--login"});
    }

    static void install(Context c, Listener listener) {
        saveLog(c, "");
        saveLog(c, "===== NOVA TENTATIVA DE INSTALAÇÃO =====");
        try {
            boolean arm64 = false;
            for (String abi : Build.SUPPORTED_ABIS) {
                if ("arm64-v8a".equals(abi)) arm64 = true;
            }
            if (!arm64) throw new IOException("Este build requer Android ARM64.");

            File nativeDir = new File(c.getApplicationInfo().nativeLibraryDir);
            if (!new File(nativeDir, "libproot.so").isFile()
                    || !new File(nativeDir, "libproot-loader.so").isFile()) {
                throw new IOException("PRoot não está incluído no APK. Gere o APK pelo GitHub Actions.");
            }

            File workDir = root(c).getParentFile();
            if (!workDir.exists() && !workDir.mkdirs()) {
                throw new IOException("Sem espaço ou permissão para criar o ambiente Linux.");
            }

            if (!new File(root(c), "usr/bin/apt-get").isFile()) {
                File sums = new File(c.getCacheDir(), "SHA256SUMS");
                File archive = new File(c.getCacheDir(), FILE);

                log(c, listener, "Baixando hashes oficiais do Ubuntu...");
                download(BASE + "SHA256SUMS", sums);
                String expectedHash = findHash(sums);

                log(c, listener, "Baixando Ubuntu Base ARM64 (~29 MB)...");
                download(BASE + FILE, archive);
                if (!expectedHash.equalsIgnoreCase(sha(archive))) {
                    throw new IOException("O SHA-256 do Ubuntu não confere. O arquivo foi descartado.");
                }

                File stage = new File(workDir, "rootfs-new");
                del(stage);
                if (!stage.mkdirs() && !stage.isDirectory()) {
                    throw new IOException("Não foi possível criar a pasta temporária do Ubuntu.");
                }

                log(c, listener, "Extraindo Ubuntu ARM64...");
                extract(archive, stage);
                verifyRootfs(stage);
                prepare(stage);

                del(root(c));
                if (!stage.renameTo(root(c))) {
                    throw new IOException("Falha ao finalizar a extração do Ubuntu.");
                }
                archive.delete();
                sums.delete();
            }

            prepare(root(c));
            log(c, listener, "Atualizando APT e instalando Python 3, pip e Git...");
            Process process = guest(c, root(c), new String[]{
                    "/bin/bash", "-lc",
                    "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin; " +
                    "export DEBIAN_FRONTEND=noninteractive; " +
                    "/usr/bin/apt-get update && " +
                    "/usr/bin/apt-get install -y python3 python3-pip git ca-certificates curl wget nano procps"
            });

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log(c, listener, line);
                }
            }

            if (!process.waitFor(30, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IOException("Tempo limite de instalação excedido (30 minutos).");
            }
            if (process.exitValue() != 0) {
                throw new IOException("APT falhou (código " + process.exitValue()
                        + "). Toque em LOG para consultar a saída completa.");
            }

            String success = "Ubuntu, APT, Python 3 e Git instalados.";
            log(c, listener, "[OK] " + success);
            listener.done(true, success);
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            saveLog(c, "[ERRO] " + message);
            listener.done(false, message);
        }
    }

    private static Process guest(Context c, File r, String[] args) throws IOException {
        File nativeDir = new File(c.getApplicationInfo().nativeLibraryDir);
        File tempDir = new File(c.getCacheDir(), "proot-tmp");
        if (!tempDir.exists()) tempDir.mkdirs();

        List<String> command = new ArrayList<>();
        command.add(new File(nativeDir, "libproot.so").getAbsolutePath());
        Collections.addAll(command,
                "--kill-on-exit", "--link2symlink", "-0",
                "-r", r.getAbsolutePath(),
                "-b", "/dev", "-b", "/proc", "-b", "/sys",
                "-b", new File(r, "tmp").getAbsolutePath() + ":/dev/shm",
                "-w", "/root");
        Collections.addAll(command, args);

        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().put("PROOT_LOADER",
                new File(nativeDir, "libproot-loader.so").getAbsolutePath());
        builder.environment().put("PROOT_TMP_DIR", tempDir.getAbsolutePath());
        builder.environment().put("HOME", "/root");
        builder.environment().put("PATH",
                "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
        builder.environment().put("TERM", "xterm-256color");
        return builder.start();
    }

    private static void download(String address, File destination) throws Exception {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        File partial = new File(destination.getParentFile(), destination.getName() + ".part");
        IOException lastError = null;

        for (int attempt = 1; attempt <= 3; attempt++) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(address).openConnection();
                connection.setConnectTimeout(25000);
                connection.setReadTimeout(60000);
                connection.setInstanceFollowRedirects(true);
                connection.setUseCaches(false);
                connection.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Linux; Android) TERMUZ-PRO/0.2.1");
                connection.setRequestProperty("Accept", "*/*");

                int code = connection.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    String details = readError(connection.getErrorStream());
                    throw new IOException("Download recusado: HTTP " + code + " em "
                            + new URL(address).getHost()
                            + (details.isEmpty() ? "." : " — " + details));
                }

                try (InputStream in = new BufferedInputStream(connection.getInputStream());
                     FileOutputStream out = new FileOutputStream(partial)) {
                    byte[] buffer = new byte[65536];
                    int count;
                    while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                    out.getFD().sync();
                }

                if (!partial.isFile() || partial.length() == 0) {
                    throw new IOException("O servidor retornou um arquivo vazio.");
                }
                if (destination.exists() && !destination.delete()) {
                    throw new IOException("Não foi possível substituir o download antigo.");
                }
                if (!partial.renameTo(destination)) {
                    throw new IOException("Não foi possível salvar o download concluído.");
                }
                return;
            } catch (IOException e) {
                partial.delete();
                lastError = e;
                String message = e.getMessage() == null ? "" : e.getMessage();
                if (message.contains("HTTP 403") || message.contains("HTTP 404")
                        || message.contains("HTTP 401") || attempt == 3) {
                    throw e;
                }
                Thread.sleep(1200L * attempt);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        throw lastError == null ? new IOException("Falha no download.") : lastError;
    }

    private static String readError(InputStream stream) {
        if (stream == null) return "";
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[256];
            int count;
            while ((count = in.read(buffer)) != -1 && out.size() < 512) {
                out.write(buffer, 0, Math.min(count, 512 - out.size()));
            }
            return out.toString("UTF-8").replaceAll("\\s+", " ").trim();
        } catch (IOException ignored) {
            return "";
        }
    }

    private static String findHash(File file) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.endsWith(" " + FILE) || line.endsWith(" *" + FILE)) {
                    return line.substring(0, line.indexOf(' '));
                }
            }
        }
        throw new IOException("Hash oficial do Ubuntu não encontrado em SHA256SUMS.");
    }

    private static String sha(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = in.read(buffer)) > 0) digest.update(buffer, 0, count);
        }
        StringBuilder result = new StringBuilder();
        for (byte b : digest.digest()) result.append(String.format(Locale.US, "%02x", b & 255));
        return result.toString();
    }

    private static Path safePath(Path base, String name) throws IOException {
        String clean = name;
        while (clean.startsWith("/")) clean = clean.substring(1);
        Path path = base.resolve(clean).normalize();
        if (!path.startsWith(base)) throw new IOException("Caminho inválido no rootfs do Ubuntu.");
        return path;
    }

    private static void extract(File archive, File destination) throws Exception {
        Path base = destination.toPath().toAbsolutePath().normalize();
        Map<Path, Path> pendingHardLinks = new LinkedHashMap<>();

        try (TarArchiveInputStream tar = new TarArchiveInputStream(
                new GzipCompressorInputStream(new BufferedInputStream(
                        new FileInputStream(archive))))) {
            TarArchiveEntry entry;
            byte[] buffer = new byte[65536];

            while ((entry = tar.getNextTarEntry()) != null) {
                Path path = safePath(base, entry.getName());
                File output = path.toFile();

                if (entry.isDirectory()) {
                    if (!output.exists() && !output.mkdirs() && !output.isDirectory()) {
                        throw new IOException("Não foi possível criar " + entry.getName());
                    }
                } else if (entry.isSymbolicLink()) {
                    File parent = output.getParentFile();
                    if (parent != null) parent.mkdirs();
                    Files.deleteIfExists(path);
                    Files.createSymbolicLink(path, Paths.get(entry.getLinkName()));
                } else if (entry.isLink()) {
                    Path target = safePath(base, entry.getLinkName());
                    File parent = output.getParentFile();
                    if (parent != null) parent.mkdirs();
                    if (Files.exists(target)) Files.createLink(path, target);
                    else pendingHardLinks.put(path, target);
                } else if (entry.isFile()) {
                    File parent = output.getParentFile();
                    if (parent != null) parent.mkdirs();
                    Files.deleteIfExists(path);
                    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(output))) {
                        int count;
                        while ((count = tar.read(buffer)) != -1) out.write(buffer, 0, count);
                    }
                    output.setExecutable((entry.getMode() & 0100) != 0, true);
                }
            }
        }

        while (!pendingHardLinks.isEmpty()) {
            boolean progress = false;
            Iterator<Map.Entry<Path, Path>> iterator = pendingHardLinks.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Path, Path> link = iterator.next();
                if (Files.exists(link.getValue())) {
                    File parent = link.getKey().toFile().getParentFile();
                    if (parent != null) parent.mkdirs();
                    Files.createLink(link.getKey(), link.getValue());
                    iterator.remove();
                    progress = true;
                }
            }
            if (!progress) throw new IOException("O arquivo Ubuntu contém links internos incompletos.");
        }
    }

    private static void verifyRootfs(File root) throws IOException {
        String[] required = {"usr/bin/apt-get", "usr/bin/dpkg", "bin/bash"};
        for (String path : required) {
            if (!new File(root, path).isFile()) {
                throw new IOException("Ubuntu incompleto: o arquivo /" + path
                        + " não foi extraído corretamente.");
            }
        }
    }

    private static void prepare(File root) throws Exception {
        for (String path : new String[]{"dev", "proc", "sys", "tmp", "dev/shm", "root", "etc"}) {
            new File(root, path).mkdirs();
        }

        File dns = new File(root, "etc/resolv.conf");
        try (FileWriter writer = new FileWriter(dns)) {
            writer.write("nameserver 1.1.1.1\n");
            writer.write("nameserver 8.8.8.8\n");
        }

        File policy = new File(root, "usr/sbin/policy-rc.d");
        File parent = policy.getParentFile();
        if (parent != null) parent.mkdirs();
        try (FileWriter writer = new FileWriter(policy)) {
            writer.write("#!/bin/sh\nexit 101\n");
        }
        policy.setExecutable(true, false);
    }

    private static void del(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) del(child);
            }
        }
        file.delete();
    }
}
