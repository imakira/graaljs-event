package net.coruscation.graaljs_event;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;

public class EContextTest {

    @Test
    public void getResourceTest() {
        assertNotNull(this.getClass().getClassLoader().getResource("webworker.js"));
    }

    @Test
    public void setTimeoutTest() throws InterruptedException {
        var ec = new EContext(Context.newBuilder("js"));
        ec.eval("let a = 10");
        var a = ec.eval("a");
        assertEquals(10, ec.eval(() -> a.asInt()));
        ec.eval("setTimeout(() => {a=a+1;}, 10)");
        TimeUnit.MILLISECONDS.sleep(100);
        var a1 = ec.eval("a");
        assertEquals(11, ec.eval(() -> {
            return a1.asInt();
        }));
    }

    @Test
    public void currentDirTest() throws IOException, URISyntaxException {
        var ec = new EContext(Context.newBuilder("js").allowIO(true));
        var testFileURL = this.getClass().getClassLoader().getResource("current_dir_test.js");
        var result = ec.eval(Source.newBuilder("js", testFileURL).build());
        assertEquals(Paths.get(testFileURL.toURI()).getParent().toString(), Path.of(result.asString()).getParent().toString());
    }

    @Test
    public void webworkerTest() throws IOException, InterruptedException {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        var ec = new EContext(Context.newBuilder("js")
                .allowIO(true)
                .out(stdout));
        System.setOut(new PrintStream(stdout));
        ec.eval(Source.newBuilder("js", this.getClass()
                .getClassLoader()
                .getResource("main.js"))
                .mimeType("application/javascript+module")
                .build());
        // ec.eval(Source.newBuilder("js", ).build());
        TimeUnit.MILLISECONDS.sleep(1000);
        assertEquals("hiya!\n", stdout.toString());
    }

    @Test
    public void webworkerTest2() throws InterruptedException, IOException {
        var ec = new EContext(Context.newBuilder("js")
                .allowIO(true));
        ec.eval(Source.newBuilder("js", this.getClass()
                .getClassLoader()
                .getResource("webworker_test2.js"))
                .mimeType("application/javascript+module")
                .build());
        TimeUnit.MILLISECONDS.sleep(1000);
        assertEquals(20001,ec.eval(() -> {
            return ec.getJsContext().getBindings("js").getMember("count").asInt();
        }));
        System.out.println("");
    }
}
