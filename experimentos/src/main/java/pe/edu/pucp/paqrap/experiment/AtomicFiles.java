package pe.edu.pucp.paqrap.experiment;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.Properties;
final class AtomicFiles {
    private AtomicFiles(){}
    static void write(Path path,String content)throws IOException{
        Path temp=path.resolveSibling(path.getFileName()+".tmp");
        Files.writeString(temp,content,StandardCharsets.UTF_8);
        try {Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException unsupported){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
    }
    static void properties(Path path,Properties values,String comment)throws IOException{
        StringWriter writer=new StringWriter();values.store(writer,comment);write(path,writer.toString());
    }
}
