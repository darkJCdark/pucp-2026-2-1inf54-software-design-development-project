package paqrap.offlinecheck;
import java.lang.reflect.*;
import java.util.*;
public final class Runner {
    public static void main(String[] classes)throws Exception {
        int passed=0;
        for(String name:classes) {
            Class<?> c=Class.forName(name);var constructor=c.getDeclaredConstructor();constructor.setAccessible(true);
            List<Method> methods=Arrays.stream(c.getDeclaredMethods()).filter(m->m.isAnnotationPresent(Test.class)).sorted(Comparator.comparing(Method::getName)).toList();
            for(Method method:methods) {
                method.setAccessible(true);
                try {method.invoke(constructor.newInstance());passed++;System.out.println("PASS offline body: "+name+"."+method.getName());}
                catch(InvocationTargetException e) {e.getCause().printStackTrace();System.exit(1);}
            }
        }
        System.out.println("PASS: "+passed+" test method bodies via offline adapter. Maven/JUnit engine NOT executed.");
    }
}
