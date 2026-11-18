package com.davidias.termuzpro;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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
    private final ExecutorService io=Executors.newCachedThreadPool();
    private volatile boolean closed=false, installing=false;
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable bg(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(14));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private TextView label(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(Color.rgb(5,10,20));getWindow().setNavigationBarColor(Color.rgb(5,10,20));build();append("TERMUZ PRO 0.2.0",0xff19d7ff);append("Terminal Android independente • ARM64",0xff8ca7c5);startShell();}
    private void build(){
        root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(16),dp(12),dp(16),dp(12));root.setBackgroundColor(0xff050a14);setContentView(root);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);root.addView(head,new LinearLayout.LayoutParams(-1,dp(70)));
        TextView logo=label("⌘",34,0xff19d7ff,true);logo.setGravity(Gravity.CENTER);logo.setBackground(bg(0xff0b2747,0xff19d7ff));head.addView(logo,new LinearLayout.LayoutParams(dp(58),dp(58)));
        LinearLayout brand=new LinearLayout(this);brand.setOrientation(1);brand.setPadding(dp(12),0,0,0);head.addView(brand,new LinearLayout.LayoutParams(0,-2,1));brand.addView(label("TERMUZ PRO",21,0xffeaf5ff,true));brand.addView(label("ANDROID LINUX TERMINAL",10,0xff19d7ff,true));
        status=label("● ANDROID ATIVO",10,0xff40f3a2,true);head.addView(status);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(10),dp(7),dp(10),dp(7));bar.setBackground(bg(0xff0a1424,0xff184f80));root.addView(bar,new LinearLayout.LayoutParams(-1,dp(52)));
        bar.addView(label("UBUNTU / PROOT",10,0xff19d7ff,true),new LinearLayout.LayoutParams(0,-2,1));
        install=new Button(this);install.setText("INSTALAR LINUX");install.setTextSize(10);install.setTextColor(0xffeaf5ff);install.setBackground(bg(0xff087bff,0xff19d7ff));bar.addView(install);install.setOnClickListener(v->installLinux());
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackground(bg(0xff02070f,0xff105796));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,0,1);sp.topMargin=dp(12);root.addView(sc,sp);
        output=label("",13,0xff40f3a2,false);output.setTypeface(Typeface.MONOSPACE);output.setPadding(dp(12),dp(12),dp(12),dp(12));output.setTextIsSelectable(true);sc.addView(output);
        TextView shortcuts=label("ATALHOS: pwd  •  ls  •  python3 --version  •  git --version",10,0xff8ca7c5,true);LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(-1,-2);qp.topMargin=dp(10);root.addView(shortcuts,qp);
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(54));rp.topMargin=dp(8);root.addView(row,rp);
        input=new EditText(this);input.setSingleLine(true);input.setTextColor(0xffeaf5ff);input.setHintTextColor(0xff8ca7c5);input.setHint("Digite um comando...");input.setTextSize(13);input.setTypeface(Typeface.MONOSPACE);input.setImeOptions(EditorInfo.IME_ACTION_SEND);input.setBackground(bg(0xff0a1424,0xff184f80));row.addView(input,new LinearLayout.LayoutParams(0,-1,1));
        Button run=new Button(this);run.setText("EXECUTAR");run.setTextColor(-1);run.setBackground(bg(0xff087bff,0xff19d7ff));row.addView(run,new LinearLayout.LayoutParams(dp(110),-1));run.setOnClickListener(v->send());
        input.setOnEditorActionListener((v,a,e)->{if(a==EditorInfo.IME_ACTION_SEND||a==EditorInfo.IME_ACTION_DONE){send();return true;}return false;});
        TextView foot=label("TERMUZ PRO • DESENVOLVIDO POR DAVI DIAS",9,0xff8ca7c5,true);foot.setGravity(Gravity.CENTER);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(28));fp.topMargin=dp(4);root.addView(foot,fp);
    }
    private void append(String s,int color){runOnUiThread(()->{if(output!=null){output.append(s+"\n");if(output.length()>45000)output.setText(output.getText().subSequence(output.length()/3,output.length()));}});}
    private void startShell(){io.execute(()->{try{boolean ready=LinuxInstaller.isInstalled(this);shell=ready?LinuxInstaller.startShell(this):new ProcessBuilder("/system/bin/sh").redirectErrorStream(true).start();writer=new BufferedWriter(new OutputStreamWriter(shell.getOutputStream(),StandardCharsets.UTF_8));BufferedReader r=new BufferedReader(new InputStreamReader(shell.getInputStream(),StandardCharsets.UTF_8));runOnUiThread(()->status.setText(ready?"● UBUNTU ATIVO":"● ANDROID ATIVO"));String line;while(!closed&&(line=r.readLine())!=null)append(line,0xff40f3a2);}catch(Exception e){append("Falha no terminal: "+e.getMessage(),0xffff7777);}});}
    private void send(){String cmd=input.getText().toString().trim();if(cmd.isEmpty())return;input.setText("");append((LinuxInstaller.isInstalled(this)?"root@termuz:~# ":"termuz@android:$ ")+cmd,0xff19d7ff);if(cmd.equals("clear")){output.setText("");return;}if(cmd.equals("help")){append("Comandos: help, clear, pwd, ls, python3, git, apt-get",0xff8ca7c5);return;}io.execute(()->{try{if(writer==null||shell==null||!shell.isAlive())throw new IOException("Sessão encerrada; reabra o app.");synchronized(writer){writer.write(cmd);writer.newLine();writer.flush();}}catch(Exception e){append("Erro: "+e.getMessage(),0xffff7777);}});}
    private void installLinux(){if(installing||LinuxInstaller.isInstalled(this))return;installing=true;install.setEnabled(false);install.setText("INSTALANDO...");append("Preparando Ubuntu ARM64 e PRoot...",0xff19d7ff);io.execute(()->LinuxInstaller.install(this,new LinuxInstaller.Listener(){public void log(String s){append(s,0xff8ca7c5);}public void done(boolean ok,String msg){runOnUiThread(()->{installing=false;install.setEnabled(!ok);install.setText(ok?"LINUX PRONTO":"TENTAR NOVAMENTE");append((ok?"[OK] ":"[ERRO] ")+msg,ok?0xff40f3a2:0xffff7777);if(ok){try{if(writer!=null)writer.close();if(shell!=null)shell.destroy();}catch(Exception ignored){}startShell();}});}}));}
    @Override protected void onDestroy(){closed=true;try{if(writer!=null)writer.close();if(shell!=null)shell.destroy();}catch(Exception ignored){}io.shutdownNow();super.onDestroy();}
}
