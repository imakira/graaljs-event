package net.coruscation.graaljs_event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.util.concurrent.TimeUnit;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;

public class Example {

    @Test
    void setTimeoutExample() throws InterruptedException {
        var out = new ByteArrayOutputStream();
        var ec = new EContext(Context.newBuilder("js").out(out));
        ec.eval("setTimeout(()=>{console.log('bonjour!')}, 1000)");
        assertEquals("", out.toString());
        TimeUnit.MILLISECONDS.sleep(1500);
        assertEquals("bonjour!\n", out.toString());
    }

    @Test
    void setTimeoutCancel() throws InterruptedException {
        var out = new ByteArrayOutputStream();
        var ec = new EContext(Context.newBuilder("js").out(out));
        ec.eval("timer = setTimeout(()=>{console.log('bonjour!')}, 1000)");
        ec.eval("clearTimeout(timer)");
        TimeUnit.MILLISECONDS.sleep(1500);
        assertEquals("", out.toString());
    }

    @Test
    void webworkerTest() throws InterruptedException, IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        var ec = new EContext(Context.newBuilder("js")
                .allowIO(true)
                .out(out));
        System.setOut(new PrintStream(out));
        ec.eval(Source.newBuilder("js", this.getClass()
                .getClassLoader()
                .getResource("main.js"))
                .mimeType("application/javascript+module")
                .build());
        TimeUnit.MILLISECONDS.sleep(50);
        assertEquals("hiya!\n", out.toString());
    }
}
