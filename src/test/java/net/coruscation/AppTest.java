package net.coruscation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;

import com.oracle.truffle.api.source.Source.SourceBuilder;

import java.io.IOException;

/**
 * Unit test for simple App.
 */
public class AppTest {

    @Test
    public void  confirmGraaljsWorks () throws IOException {
        var jsModule = ClassLoader.getSystemResource("module.js");
        var source =  Source.newBuilder("js", jsModule).build();
        Context context = Context
                .newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(className -> true)
                .build();
        var res = context.eval(source);
        var javaPi = res.getMember("test").execute();
        assertTrue((javaPi.asDouble() - java.lang.Math.PI) < 0.0001);
    }
    
    /**
     * Rigorous Test :-)
     */
    @Test
    public void shouldAnswerWithTrue() {
        assertTrue(true);
    }
}
