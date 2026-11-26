/* Copyright (c) 2026 Rezoss
 * SPDX-License-Identifier: GPL-3.0-or-later */
package io.mesalabs.unica.movecertificate;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.*;
import java.util.*;

/** Only explicit, validated CA imports become boot-time trust anchors. */
public final class Certificates {
    public static final int MAX_BYTES=65536, MAX_CERTS=64;
    public static byte[] read(InputStream in) throws IOException {
        if(in==null)throw new IOException("Cannot open certificate");
        ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] b=new byte[4096]; int n;
        while((n=in.read(b))!=-1){if(out.size()+n>MAX_BYTES)throw new IOException("Certificate exceeds 64 KiB");out.write(b,0,n);}
        return out.toByteArray();
    }
    public static X509Certificate parse(byte[] data) throws Exception {
        Collection<? extends java.security.cert.Certificate> all=CertificateFactory.getInstance("X.509").generateCertificates(new ByteArrayInputStream(data));
        if(all.size()!=1)throw new CertificateException("Select a single CA certificate");
        X509Certificate c=(X509Certificate)all.iterator().next();
        if(c.getBasicConstraints()<0)throw new CertificateException("This is not a CA certificate");
        boolean[] usage=c.getKeyUsage();
        if(usage!=null&&(usage.length<=5||!usage[5]))throw new CertificateException("CA certificate signing is not permitted");
        String name=c.getSubjectX500Principal().getName().toLowerCase(Locale.ROOT);
        if(name.contains("adguard")&&name.contains("intermediate"))throw new CertificateException("Keep the AdGuard Intermediate CA in the Android user store; import the Personal CA here");
        return c;
    }
    public static String hash(X509Certificate c) throws Exception {
        byte[] d=MessageDigest.getInstance("MD5").digest(c.getSubjectX500Principal().getEncoded());
        long h=0; for(int i=0;i<4;i++)h|=(d[i]&255L)<<(8*i);
        return String.format(Locale.ROOT,"%08x",h);
    }
    public static String fingerprint(X509Certificate c) throws Exception {
        StringBuilder out=new StringBuilder();
        for(byte b:MessageDigest.getInstance("SHA-256").digest(c.getEncoded())){if(out.length()>0)out.append(':');out.append(String.format(Locale.ROOT,"%02X",b&255));}
        return out.toString();
    }
    public static byte[] pem(X509Certificate c) throws Exception {
        return ("-----BEGIN CERTIFICATE-----\n"+Base64.getMimeEncoder(64,new byte[]{10}).encodeToString(c.getEncoded())+"\n-----END CERTIFICATE-----\n").getBytes(StandardCharsets.US_ASCII);
    }
    public static File[] files(File dir) {
        File[] files=dir.listFiles(f->f.isFile()&&f.getName().matches("[0-9a-f]{8}\\.[0-9]+"));
        if(files==null)return new File[0]; Arrays.sort(files); return files;
    }
    public static void atomic(File file,byte[] data) throws IOException {
        File temp=new File(file.getParentFile(),file.getName()+".tmp");
        try(FileOutputStream out=new FileOutputStream(temp)){out.write(data);out.getFD().sync();}
        // The boot worker has the system group but no DAC override capability.
        // The enclosing request directory limits access to root/system.
        if(!temp.setReadable(true,false))throw new IOException("Cannot set certificate permissions");
        if(!temp.renameTo(file))throw new IOException("Cannot save certificate configuration");
    }
    public static void install(File dir,X509Certificate c) throws Exception {
        c.checkValidity(); File[] current=files(dir);
        for(File f:current)try(InputStream in=new FileInputStream(f)){if(Arrays.equals(parse(read(in)).getEncoded(),c.getEncoded()))throw new IOException("Certificate is already imported");}
        if(current.length>=MAX_CERTS)throw new IOException("Maximum of 64 imported CAs reached");
        String hash=hash(c); int index=0; while(new File(dir,hash+"."+index).exists())index++;
        atomic(new File(dir,hash+"."+index),pem(c));
    }
}
