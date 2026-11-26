/* Copyright (c) 2026 Rezoss
 * SPDX-License-Identifier: GPL-3.0-or-later */
package io.mesalabs.unica.pif;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Data-only fetches for GitHub profiles and the live Google Autopif source. */
public final class PixelSource {
    private static final String BASE="https://raw.githubusercontent.com/KOWX712/PlayIntegrityFix/bot/";
    public static String get(String path) throws Exception {
        if(!path.equals("device_list.json")&&!path.matches("device_prop/[a-z0-9_]+\\.prop"))throw new IOException("Invalid profile path");
        return download(BASE+path,null,131072);
    }
    static String download(String address,String referer,int limit)throws IOException {
        URL url=new URL(address);
        for(int redirects=0;redirects<5;redirects++) {
            if(!url.getProtocol().equals("https")||!Arrays.asList("raw.githubusercontent.com","flash.android.com","content-flashstation-pa.googleapis.com","source.android.com").contains(url.getHost()))
                throw new IOException("Profile source redirected to an unsupported server");
            HttpsURLConnection connection=(HttpsURLConnection)url.openConnection();
            connection.setConnectTimeout(12000);connection.setReadTimeout(15000);connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept-Language","en");
            if(referer!=null)connection.setRequestProperty("Referer",referer);
            try {
                int code=connection.getResponseCode();
                if(code==301||code==302||code==303||code==307||code==308){String location=connection.getHeaderField("Location");if(location==null)throw new IOException("Profile source sent an empty redirect");url=new URL(url,location);continue;}
                if(code!=200)throw new IOException("Profile source returned HTTP "+code);
                try(InputStream input=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                    byte[] buffer=new byte[4096];int size;
                    while((size=input.read(buffer))!=-1){out.write(buffer,0,size);if(out.size()>limit)throw new IOException("Profile response too large");}
                    return new String(out.toByteArray(),StandardCharsets.UTF_8);
                }
            } finally {connection.disconnect();}
        }
        throw new IOException("Too many profile source redirects");
    }
}
