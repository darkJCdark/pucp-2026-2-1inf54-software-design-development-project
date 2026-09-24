package pe.edu.pucp.paqrap.experiment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Small JSON writer (no parser); keeps the experimental CLI dependency-free. */
public final class Json {
    private Json(){}
    public static Map<String,Object> obj(Object... pairs){
        if(pairs.length%2!=0)throw new IllegalArgumentException();
        Map<String,Object> m=new LinkedHashMap<>();
        for(int i=0;i<pairs.length;i+=2)m.put(pairs[i].toString(),pairs[i+1]);
        return m;
    }
    public static String encode(Object o){
        if(o==null)return "null";
        if(o instanceof Boolean)return o.toString();
        if(o instanceof Number n){double d=n.doubleValue();return Double.isFinite(d)?o.toString():"null";}
        if(o instanceof Map<?,?> m){var a=new ArrayList<String>();m.forEach((k,v)->a.add(quote(k.toString())+":"+encode(v)));return "{"+String.join(",",a)+"}";}
        if(o instanceof Iterable<?> it){var a=new ArrayList<String>();it.forEach(v->a.add(encode(v)));return "["+String.join(",",a)+"]";}
        return quote(o.toString());
    }
    public static String quote(String s){
        StringBuilder b=new StringBuilder("\"");
        for(char c:s.toCharArray())switch(c){case '\\'->b.append("\\\\");case '"'->b.append("\\\"");case '\n'->b.append("\\n");case '\r'->b.append("\\r");case '\t'->b.append("\\t");default->{if(c<32)b.append(String.format("\\u%04x",(int)c));else b.append(c);}}
        return b.append('"').toString();
    }
    public static String sha256(String s){return sha256(s.getBytes(StandardCharsets.UTF_8));}
    public static String sha256(byte[] data){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}catch(Exception e){throw new IllegalStateException(e);}}
}
