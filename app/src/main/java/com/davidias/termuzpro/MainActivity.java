package com.davidias.termuzpro;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private LinearLayout root;
    private TextView output, status;
    private EditText input;
    private Button install;
    private Process shell;
    private BufferedWriter writer;
    private final ExecutorService io = Executors.newCachedThreadPool();
    private volatile boolean closed = false;
    private volatile boolean installing = false;

    private int dp(float n) {
        return (int) (n * getResources().getDisplayMetrics().density + .5f);
    }

    private GradientDrawable bg(int color, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(14));
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }

    private TextView label(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(5, 10, 20));
        getWindow().setNavigationBarColor(Color.rgb(5, 10, 20));
        build();
        append("TERMUZ PRO 0.2.1", 0xff19d7ff);
        append("Terminal Linux ARM64 • PRoot", 0xff8ca7c5);
        if (LinuxInstaller.isInstalled(this)) {
            install.setText("LINUX PRONTO");
            install.setEnabled(false);
        } else {
            append("Linux ainda não instalado. Toque em INSTALAR LINUX para preparar o Ubuntu.", 0xffffca65);
        }
        startShell();
    }

    private void build() {
        root = new LinearLayout(this);
        root.setOrientation(1);
        root.setPadding(dp(14), dp(8), dp(14), dp(10));
        root.setBackgroundColor(0xff050a14);
        setContentView(root);

        ImageView artwork = new ImageView(this);
        artwork.setImageResource(R.drawable.termuz_brand);
        artwork.setScaleType(ImageView.ScaleType.FIT_CENTER);
        artwork.setAdjustViewBounds(true);
        artwork.setContentDescription("Identidade visual TERMUZ PRO");
        LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(-1, dp(184));
        artParams.bottomMargin = dp(6);
        root.addView(artwork, artParams);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(5), dp(8), dp(5));
        bar.setBackground(bg(0xff0a1424, 0xff184f80));
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(50)));

        bar.addView(label("UBUNTU / PROOT", 10, 0xff19d7ff, true),
                new LinearLayout.LayoutParams(0, -2, 1));

        status = label("● ANDROID", 9, 0xff40f3a2, true);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-2, -2);
        statusParams.rightMargin = dp(6);
        bar.addView(status, statusParams);

        Button logs = new Button(this);
        logs.setText("LOG");
        logs.setTextSize(9);
        logs.setMinWidth(0);
        logs.setMinimumWidth(0);
        logs.setPadding(dp(5), 0, dp(5), 0);
        logs.setTextColor(0xffeaf5ff);
        logs.setBackground(bg(0xff12304c, 0xff19d7ff));
        bar.addView(logs, new LinearLayout.LayoutParams(dp(48), dp(40)));
        logs.setOnClickListener(v -> showInstallerLog());

        install = new Button(this);
        install.setText("INSTALAR LINUX");
        install.setTextSize(9);
        install.setMinWidth(0);
        install.setMinimumWidth(0);
        install.setPadding(dp(7), 0, dp(7), 0);
        install.setTextColor(0xffeaf5ff);
        install.setBackground(bg(0xff087bff, 0xff19d7ff));
        LinearLayout.LayoutParams installParams = new LinearLayout.LayoutParams(-2, dp(40));
        installParams.leftMargin = dp(6);
        bar.addView(install, installParams);
        install.setOnClickListener(v -> installLinux());

        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        sc.setBackground(bg(0xff02070f, 0xff105796));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, 0, 1);
        sp.topMargin = dp(10);
        root.addView(sc, sp);

        output = label("", 13, 0xff40f3a2, false);
        output.setTypeface(Typeface.MONOSPACE);
        output.setPadding(dp(12), dp(12), dp(12), dp(12));
        output.setTextIsSelectable(true);
        sc.addView(output);

        TextView shortcuts = label(
                "ATALHOS: pwd  •  ls  •  python3 --version  •  git --version",
                10, 0xff8ca7c5, true);
        LinearLayout.LayoutParams shortcutParams = new LinearLayout.LayoutParams(-1, -2);
        shortcutParams.topMargin = dp(8);
        root.addView(shortcuts, shortcutParams);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(52));
        rowParams.topMargin = dp(7);
        root.addView(row, rowParams);

        input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(0xffeaf5ff);
        input.setHintTextColor(0xff8ca7c5);
        input.setHint("Digite um comando...");
        input.setTextSize(13);
        input.setTypeface(Typeface.MONOSPACE);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setBackground(bg(0xff0a1424, 0xff184f80));
        row.addView(input, new LinearLayout.LayoutParams(0, -1, 1));

        Button run = new Button(this);
        run.setText("EXECUTAR");
        run.setTextSize(10);
        run.setTextColor(-1);
        run.setBackground(bg(0xff087bff, 0xff19d7ff));
        row.addView(run, new LinearLayout.LayoutParams(dp(100), -1));
        run.setOnClickListener(v -> send());

        input.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEND || action == EditorInfo.IME_ACTION_DONE) {
                send();
                return true;
            }
            return false;
        });

        TextView foot = label("TERMUZ PRO • 0.2.1 • ARM64", 9, 0xff8ca7c5, true);
        foot.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footParams = new LinearLayout.LayoutParams(-1, dp(24));
        footParams.topMargin = dp(3);
        root.addView(foot, footParams);
    }

    private void append(String s, int color) {
        runOnUiThread(() -> {
            if (output == null) return;
            SpannableString line = new SpannableString(s + "\n");
            line.setSpan(new ForegroundColorSpan(color), 0, line.length(),
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
            output.append(line);
            if (output.length() > 45000) {
                output.setText(output.getText().subSequence(output.length() / 3, output.length()));
            }
        });
    }

    private void showInstallerLog() {
        File file = LinuxInstaller.logFile(this);
        if (!file.isFile()) {
            append("Ainda não existe log de instalação.", 0xffffca65);
            return;
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long start = Math.max(0, raf.length() - 18000);
            raf.seek(start);
            byte[] data = new byte[(int) (raf.length() - start)];
            raf.readFully(data);
            append("===== LOG DE INSTALAÇÃO =====", 0xff19d7ff);
            append(new String(data, StandardCharsets.UTF_8), 0xffc8ddf2);
            append("===== FIM DO LOG =====", 0xff19d7ff);
        } catch (IOException e) {
            append("Não foi possível ler o log: " + e.getMessage(), 0xffff7777);
        }
    }

    private void startShell() {
        io.execute(() -> {
            try {
                boolean ready = LinuxInstaller.isInstalled(this);
                shell = ready
                        ? LinuxInstaller.startShell(this)
                        : new ProcessBuilder("/system/bin/sh").redirectErrorStream(true).start();
                writer = new BufferedWriter(new OutputStreamWriter(
                        shell.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        shell.getInputStream(), StandardCharsets.UTF_8));
                runOnUiThread(() -> status.setText(ready ? "● UBUNTU" : "● ANDROID"));
                String line;
                while (!closed && (line = reader.readLine()) != null) {
                    append(line, 0xff40f3a2);
                }
            } catch (Exception e) {
                append("Falha no terminal: " + e.getMessage(), 0xffff7777);
            }
        });
    }

    private void send() {
        String cmd = input.getText().toString().trim();
        if (cmd.isEmpty()) return;
        input.setText("");
        boolean linux = LinuxInstaller.isInstalled(this);
        append((linux ? "root@termuz:~# " : "termuz@android:$ ") + cmd, 0xff19d7ff);
        if (cmd.equals("clear")) {
            output.setText("");
            return;
        }
        if (cmd.equals("help")) {
            append("Comandos: help, clear, pwd, ls, python3, git, apt-get", 0xff8ca7c5);
            if (!linux) append("Instale o Ubuntu para usar Python, Git e APT.", 0xffffca65);
            return;
        }
        if (!linux && (cmd.startsWith("python") || cmd.startsWith("git") || cmd.startsWith("apt"))) {
            append("Esse comando precisa do Ubuntu. Toque em INSTALAR LINUX primeiro.", 0xffffca65);
        }
        io.execute(() -> {
            try {
                if (writer == null || shell == null || !shell.isAlive()) {
                    throw new IOException("Sessão encerrada; reabra o app.");
                }
                synchronized (writer) {
                    writer.write(cmd);
                    writer.newLine();
                    writer.flush();
                }
            } catch (Exception e) {
                append("Erro: " + e.getMessage(), 0xffff7777);
            }
        });
    }

    private void installLinux() {
        if (installing || LinuxInstaller.isInstalled(this)) return;
        installing = true;
        install.setEnabled(false);
        install.setText("INSTALANDO...");
        append("Preparando Ubuntu ARM64 e PRoot...", 0xff19d7ff);
        io.execute(() -> LinuxInstaller.install(this, new LinuxInstaller.Listener() {
            @Override
            public void log(String s) {
                append(s, 0xff8ca7c5);
            }

            @Override
            public void done(boolean ok, String msg) {
                runOnUiThread(() -> {
                    installing = false;
                    install.setEnabled(!ok);
                    install.setText(ok ? "LINUX PRONTO" : "TENTAR NOVAMENTE");
                    append((ok ? "[OK] " : "[ERRO] ") + msg,
                            ok ? 0xff40f3a2 : 0xffff7777);
                    if (ok) {
                        try {
                            if (writer != null) writer.close();
                            if (shell != null) shell.destroy();
                        } catch (Exception ignored) {
                        }
                        startShell();
                    }
                });
            }
        }));
    }

    @Override
    protected void onDestroy() {
        closed = true;
        try {
            if (writer != null) writer.close();
            if (shell != null) shell.destroy();
        } catch (Exception ignored) {
        }
        io.shutdownNow();
        super.onDestroy();
    }
}
