package paqrap.offlinecheck;
import java.util.Objects;
import java.util.function.Supplier;
/** Small, explicit offline assertion adapter; NOT the JUnit engine/API. */
public final class Assertions {
    private Assertions() {}
    public static void assertTrue(boolean p) { assertTrue(p,"Expected true"); }
    public static void assertTrue(boolean p,String s) { if(!p)throw new AssertionError(s); }
    public static void assertTrue(boolean p,Supplier<String> s) { if(!p)throw new AssertionError(s.get()); }
    public static void assertFalse(boolean p) { assertTrue(!p,"Expected false"); }
    public static void assertFalse(boolean p,String s) { assertTrue(!p,s); }
    public static void assertNotNull(Object o,String s) { assertTrue(o!=null,s); }
    public static void assertNotNull(Object o) { assertNotNull(o,"Expected not null"); }
    public static void assertEquals(Object a,Object b) { assertEquals(a,b,""); }
    public static void assertEquals(Object a,Object b,String s) {
        boolean equal=(a instanceof Number n && b instanceof Number m)?Double.compare(n.doubleValue(),m.doubleValue())==0:Objects.equals(a,b);
        if(!equal)throw new AssertionError(s+" expected="+a+" actual="+b);
    }
    @FunctionalInterface public interface Executable {void execute()throws Throwable;}
    public static <T extends Throwable> T assertThrows(Class<T> type,Executable e) {
        try {e.execute();} catch(Throwable t) {if(type.isInstance(t))return type.cast(t);throw new AssertionError("Wrong exception",t);}
        throw new AssertionError("Expected exception "+type);
    }
}
