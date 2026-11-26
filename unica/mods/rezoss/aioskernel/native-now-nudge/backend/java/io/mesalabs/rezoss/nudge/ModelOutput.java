package io.mesalabs.rezoss.nudge;

/** Remove terminal decoder markers, retaining protocol-significant tabs and spaces. */
final class ModelOutput {
    static String completion(String raw) {
        if(raw==null)throw new IllegalArgumentException("missing_output");
        int end=raw.length();
        while(end>0&&(raw.charAt(end-1)=='\r'||raw.charAt(end-1)=='\n'||raw.charAt(end-1)==' '))end--;
        String value=raw.substring(0,end);
        for(String token:new String[]{"<|im_end|>","<|endoftext|>"})
            if(value.endsWith(token)){value=value.substring(0,value.length()-token.length());break;}
        if(value.contains("<|")||value.contains("<think>")||value.contains("</think>"))throw new IllegalArgumentException("invalid_output_control");
        return value;
    }
}
