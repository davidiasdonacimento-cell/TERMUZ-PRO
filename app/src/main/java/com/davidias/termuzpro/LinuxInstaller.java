package com.davidias.termuzpro;

import android.content.Context;
import android.os.Build;
import org.apache.commons.compress.archivers.tar.*;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class LinuxInstaller {
    interface Listener { void log(String s); void done(boolean ok,String msg); }
    private static final String FILE="ubuntu-base-24.04.5-base-arm64.tar.gz";
    private static final String BASE="https://cdimages.ubuntu.com/ubuntu-base/releases/24.04/release/";
    private LinuxInstaller(){}
    static File root(Context c){return new File(c.getNoBackupFilesDir(),"termuz-linux/rootfs");}
    static boolean isInstalled(Context c){File r=root(c);return new File(r,"usr/bin/apt-get").isFile()&&new File(r,"usr/bin/python3").isFile()&&new File(r,"usr/bin/git").isFile()&&new File(c.getApplicationInfo().nativeLibraryDir,"libproot.so").isFile()&&new File(c.getApplicationInfo().nativeLibraryDir,"libproot-loader.so").isFile();}
    static Process startShell(Context c)throws IOException{return guest(c,root(c),new String[]{"/bin/bash","--login"});}
    static void install(Context c,Listener l){try{
        boolean arm=false;for(String a:Build.SUPPORTED_ABIS)if(a.equals("arm64-v8a"))arm=true;if(!arm)throw new IOException("Este build requer Android ARM64.");
        File nd=new File(c.getApplicationInfo().nativeLibraryDir);if(!new File(nd,"libproot.so").isFile()||!new File(nd,"libproot-loader.so").isFile())throw new IOException("PRoot não está incluído no APK. Execute o workflow GitHub Actions.");
        File rt=root(c).getParentFile();if(!rt.exists()&&!rt.mkdirs())throw new IOException("Sem espaço ou permissão para criar o ambiente Linux.");
        if(!new File(root(c),"usr/bin/apt-get").isFile()){
            File sums=new File(c.getCacheDir(),"SHA256SUMS"),arc=new File(c.getCacheDir(),FILE);l.log("Baixando hashes oficiais do Ubuntu...");download(BASE+"SHA256SUMS",sums);String hash=findHash(sums);l.log("Baixando Ubuntu Base ARM64 (~29 MB)...");download(BASE+FILE,arc);if(!hash.equalsIgnoreCase(sha(arc)))throw new IOException("Hash SHA-256 do Ubuntu não confere.");
            File stage=new File(rt,"rootfs-new");del(stage);stage.mkdirs();l.log("Extraindo Ubuntu...");extract(arc,stage);prepare(stage);del(root(c));if(!stage.renameTo(root(c)))throw new IOException("Falha ao finalizar extração.");arc.delete();sums.delete();
        }
        prepare(root(c));l.log("Atualizando APT e instalando Python 3, pip e Git...");Process p=guest(c,root(c),new String[]{"/bin/bash","-lc","export DEBIAN_FRONTEND=noninteractive; apt-get update && apt-get install -y python3 python3-pip git ca-certificates curl wget nano procps"});
        BufferedReader br=new BufferedReader(new InputStreamReader(p.getInputStream()));String line;while((line=br.readLine())!=null)l.log(line);if(!p.waitFor(30,TimeUnit.MINUTES)){p.destroyForcibly();throw new IOException("Tempo limite de instalação excedido.");}if(p.exitValue()!=0)throw new IOException("APT falhou (código "+p.exitValue()+"). Confira a internet e tente novamente.");
        l.done(true,"Ubuntu, APT, Python 3 e Git instalados.");return;
    }catch(Exception e){l.done(false,e.getMessage()==null?e.toString():e.getMessage());}}
    private static Process guest(Context c,File r,String[] args)throws IOException{File nd=new File(c.getApplicationInfo().nativeLibraryDir),tmp=new File(c.getCacheDir(),"proot-tmp");tmp.mkdirs();List<String>x=new ArrayList<>();x.add(new File(nd,"libproot.so").getAbsolutePath());Collections.addAll(x,"--kill-on-exit","--link2symlink","-0","-r",r.getAbsolutePath(),"-b","/dev","-b","/proc","-b","/sys","-b",new File(r,"tmp").getAbsolutePath()+":/dev/shm","-w","/root");Collections.addAll(x,args);ProcessBuilder b=new ProcessBuilder(x).redirectErrorStream(true);b.environment().put("PROOT_LOADER",new File(nd,"libproot-loader.so").getAbsolutePath());b.environment().put("PROOT_TMP_DIR",tmp.getAbsolutePath());b.environment().put("HOME","/root");b.environment().put("TERM","xterm-256color");return b.start();}
    private static void download(String u,File f)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(25000);c.setReadTimeout(60000);c.setRequestProperty("User-Agent","TERMUZ-PRO");try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(f)){byte[]b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}finally{c.disconnect();}}
    private static String findHash(File f)throws Exception{try(BufferedReader r=new BufferedReader(new FileReader(f))){String s;while((s=r.readLine())!=null)if(s.endsWith(" "+FILE)||s.endsWith(" *"+FILE))return s.substring(0,s.indexOf(' '));}throw new IOException("Hash oficial não encontrado.");}
    private static String sha(File f)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream i=new FileInputStream(f)){byte[]b=new byte[65536];int n;while((n=i.read(b))>0)d.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte b:d.digest())s.append(String.format("%02x",b&255));return s.toString();}
    private static void extract(File f,File dest)throws Exception{Path base=dest.toPath().toAbsolutePath().normalize();try(TarArchiveInputStream t=new TarArchiveInputStream(new GzipCompressorInputStream(new BufferedInputStream(new FileInputStream(f))))){TarArchiveEntry e;byte[]b=new byte[65536];while((e=t.getNextTarEntry())!=null){Path p=base.resolve(e.getName()).normalize();if(!p.startsWith(base))throw new IOException("Caminho inválido no rootfs.");File o=p.toFile();if(e.isDirectory())o.mkdirs();else if(e.isSymbolicLink()){o.getParentFile().mkdirs();Files.deleteIfExists(p);Files.createSymbolicLink(p,Paths.get(e.getLinkName()));}else if(e.isFile()){o.getParentFile().mkdirs();try(OutputStream z=new BufferedOutputStream(new FileOutputStream(o))){int n;while((n=t.read(b))!=-1)z.write(b,0,n);}o.setExecutable((e.getMode()&0100)!=0,true);}}}}
    private static void prepare(File r)throws Exception{for(String p:new String[]{"dev","proc","sys","tmp","dev/shm","root","etc"})new File(r,p).mkdirs();File dns=new File(r,"etc/resolv.conf");try(FileWriter w=new FileWriter(dns)){w.write("nameserver 1.1.1.1\nnameserver 8.8.8.8\n");}File policy=new File(r,"usr/sbin/policy-rc.d");try(FileWriter w=new FileWriter(policy)){w.write("#!/bin/sh\nexit 101\n");}policy.setExecutable(true,false);}
    private static void del(File f){if(f==null||!f.exists())return;if(f.isDirectory()){File[]a=f.listFiles();if(a!=null)for(File x:a)del(x);}f.delete();}
}
