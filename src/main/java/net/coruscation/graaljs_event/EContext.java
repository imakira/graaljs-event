package net.coruscation.graaljs_event;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.js.nodes.function.EvalNode;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

abstract class SimpleJsFunc implements ProxyExecutable {
    final Context jsContext;
    final EContext eContext;
    final ScheduledExecutorService executor;

    public SimpleJsFunc(EContext eContext) {
        this.jsContext = eContext.getJsContext();
        this.executor = eContext.getExecutor();
        this.eContext = eContext;
    }
}

class JsSetTimeout extends SimpleJsFunc {

    public JsSetTimeout(EContext eContext) {
        super(eContext);
    }

    @Override
    public Object execute(Value... arguments) {
        if (arguments.length < 1 || !arguments[0].canExecute()) {
            throw new IllegalArgumentException();
        }
        var fn = arguments[0];
        var delay = arguments.length >= 2 ? arguments[1].asLong() : 0;
        final Object[] fnArgs = arguments.length > 2 ? Arrays.copyOfRange(arguments, 3, arguments.length)
                : new Object[0];
        var future = executor.schedule(() -> {
            fn.executeVoid(fnArgs);
        }, delay, TimeUnit.MILLISECONDS);
        return jsContext.asValue(future);
    }
}

class JsClearTimeout extends SimpleJsFunc {

    public JsClearTimeout(EContext eContext) {
        super(eContext);
    }

    @Override
    public Object execute(Value... arguments) {
        if (arguments.length != 1 && arguments[0].isHostObject()) {
            throw new IllegalArgumentException();
        }
        var task = arguments[0].asHostObject();
        if (task instanceof ScheduledFuture t) {
            t.cancel(false);
        } else {
            throw new IllegalArgumentException();
        }
        return null;
    }
}

class JsSetInterval extends SimpleJsFunc {

    public JsSetInterval(EContext eContext) {
        super(eContext);
    }

    @Override
    public Object execute(Value... arguments) {
        if (arguments.length < 1 || !arguments[0].canExecute()) {
            throw new IllegalArgumentException();
        }
        var fn = arguments[0];
        var delay = arguments.length >= 2 ? arguments[1].asLong() : 0;
        final Object[] fnArgs = arguments.length > 2 ? Arrays.copyOfRange(arguments, 3, arguments.length)
                : new Object[0];
        var future = executor.scheduleAtFixedRate(() -> {
            fn.executeVoid(fnArgs);
        }, 0, delay, TimeUnit.MILLISECONDS);
        return jsContext.asValue(future);
    }
}

class JsNewWorkerContext extends SimpleJsFunc {

    static class Worker {
        Value workerJsObj;
        EContext sourceEventContext;
        EContext workerEventContext;

        public Worker(EContext refereeEventContext, EContext workerEventContext, Value workerJsObj) {
            this.workerEventContext = workerEventContext;
            this.sourceEventContext = refereeEventContext;
            this.workerJsObj = workerJsObj;
        }

        public Value getWorkerJsObj() {
            return workerJsObj;
        }

    }

    public JsNewWorkerContext(EContext eContext) {
        super(eContext);
    }

    @Override
    public Object execute(Value... arguments) {
        if (arguments.length < 2 || !arguments[0].hasMembers() || !arguments[1].isString() || arguments.length > 3) {
            throw new IllegalArgumentException();
        }

        var workerJsObj = arguments[0];

        var urlOrPath = arguments[1].asString();

        Value options = arguments.length == 3 ? arguments[2] : null;
        var isEsmModule = options != null && options.hasMember("type")
                && options.getMember("type").asString().equals("module");
        Path p = null;


        try {

            if (urlOrPath.startsWith("file:/")) {
                p = Paths.get(new URI(urlOrPath)).toAbsolutePath().normalize();
            } else {
                p = Path.of(urlOrPath).normalize();
            }


            if (!p.toFile().exists() || p.toFile().isDirectory()) {
                throw new RuntimeException("Js file does not exist: '" + p.toString() + "'");
            }

            EContext workerEContext = new EContext(this.eContext.getContextBuilder());

            Worker worker = new Worker(this.eContext, workerEContext, workerJsObj);

            ProxyExecutable sourcePostMessage = (Value... args) -> {
                var data = args[0];
                workerEContext.sendMessage("self", data.asString());
                return null;
            };
            workerJsObj.putMember("_postMessage", sourcePostMessage);

            // Setup callback for the module starting a new Worker
            this.eContext.getEventLoop().putMessageHandler(worker, (data) -> {
                workerJsObj.invokeMember("_onmessage", data);
            });

            var workerSourceBuilder = Source.newBuilder("js", p.toUri().toURL());
            if (isEsmModule) {
                workerSourceBuilder = workerSourceBuilder.mimeType("application/javascript+module");
            }
            var workerSource = workerSourceBuilder.build();

            workerEContext.evalAsync(() -> {
                Context workerJsContext = workerEContext.getJsContext();
                workerJsContext.eval("js", "globalThis.self=globalThis");
                workerJsContext.getBindings("js").getMember("_setup_worker").execute();
                workerEContext.getEventLoop().putMessageHandler("self", (Object data) -> {
                    workerJsContext.getBindings("js").getMember("_onmessage").execute(data);
                });

                Value bindings = workerJsContext.getBindings("js");
                ProxyExecutable workerPostMessage = (Value... args) -> {
                    worker.sourceEventContext.sendMessage(worker, args[0].asString());
                    return null;
                };
                bindings.putMember("_postMessage", workerPostMessage);
                workerJsContext.eval(workerSource);
            });
            return this.jsContext.asValue(worker);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}

class jsSendMessage extends SimpleJsFunc {

    public jsSendMessage(EContext eContext) {
        super(eContext);
    }

    @Override
    /*
     * 1. target Context
     * 2. callback ref
     * 3. data
     */
    public Object execute(Value... arguments) {
        if (arguments.length != 3 || !arguments[0].isHostObject() || !arguments[1].isHostObject()) {
            throw new IllegalArgumentException();
        }
        var targetContextObj = arguments[0].asHostObject();
        var handlerKey = arguments[1].asHostObject();
        var dataStr = arguments[2];
        if (targetContextObj instanceof EContext tarContext && dataStr.isString()) {
            tarContext.sendMessage(handlerKey, dataStr.asString());
            return null;
        } else {
            throw new IllegalArgumentException();
        }
    }

}

class JsCurrentDir extends SimpleJsFunc {

    public JsCurrentDir(EContext eContext) {
        super(eContext);
    }

    public static String currentFile() {
        var runtime = Truffle.getRuntime();
        try {
            var iterate = runtime.getClass().getDeclaredMethod("iterateFrames", FrameInstanceVisitor.class);
            iterate.setAccessible(true);
            final Node[] callee = { null };
            iterate.invoke(runtime, new FrameInstanceVisitor<Object>() {

                @Override
                public Object visitFrame(FrameInstance frameInstance) {
                    if (frameInstance.getCallNode() != null) {
                        callee[0] = frameInstance.getCallNode();
                        return true;
                    }
                    return null;
                }
            });
            var referee = EvalNode.findActiveScriptOrModule(callee[0]);
            return referee.getSource().getPath();
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException
                | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object execute(Value... arguments) {
        return JsCurrentDir.currentFile();
    }
}

public class EContext {

    final Context.Builder contextBuilder;
    final EventLoop eventLoop;

    class EventLoop {
        private final ScheduledExecutorService executor;
        private final Context jsContext;

        final ThreadLocal<Map<Object, Consumer<Object>>> handlers = ThreadLocal.withInitial(() -> new HashMap<>());


        public EventLoop(Context jsContext, ScheduledExecutorService executor) {
            this.jsContext = jsContext;
            this.executor = executor;
        }

        public ScheduledExecutorService getExecutor() {
            return executor;
        }

        public Context getJsContext() {
            return jsContext;
        }

        void putMessageHandler(Object key, Consumer<Object> handler) {
            this.handlers.get().put(key, handler);
        }

        public ThreadLocal<Map<Object, Consumer<Object>>> getHandlers() {
            return handlers;
        }

    }

    static void initializeJsContext(Context jsContext, EContext eventContext) {
        try {
            var webworkerUrl = eventContext.getClass().getClassLoader().getResource("webworker.js");
            jsContext.eval(Source.newBuilder("js", webworkerUrl).build());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        var bindings = jsContext.getBindings("js");
        bindings.putMember("setTimeout", new JsSetTimeout(eventContext));
        var jsClearTask = new JsClearTimeout(eventContext);
        bindings.putMember("clearTimeout", jsClearTask);
        bindings.putMember("clearInterval", jsClearTask);
        bindings.putMember("_newEContext", new JsNewWorkerContext(eventContext));
        bindings.putMember("_currentDir", new JsCurrentDir(eventContext));
    }

    public EContext(Context.Builder contextBuilder) {
        this.eventLoop = new EventLoop(contextBuilder.build(), Executors.newSingleThreadScheduledExecutor());
        this.contextBuilder = contextBuilder;
        this.eval(() -> {
            initializeJsContext(this.getJsContext(), this);
        });
    }

    public <T> T eval(Supplier<T> f) {
        try {
            return this.eventLoop.getExecutor().submit(() -> {
                return f.get();
            }).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public void eval(Runnable f) {
        try {
            this.eventLoop.getExecutor().submit(f).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public Value eval(String source) {
        return this.eval(() -> {
            return this.getJsContext().eval("js", source);
        });
    }

    public Value eval(Source source) {
        return this.eval(() -> {
            return this.getJsContext().eval(source);
        });
    }


    public void sendMessage(Object key, Object data) {
        this.evalAsync(() -> {
            var handlers = this.eventLoop.getHandlers().get();
            if (handlers.containsKey(key)) {
                handlers.get(key).accept(data);
            }
        });
    }

    void evalAsync(Runnable f) {
        this.eventLoop.getExecutor().submit(f);
    }

    <T> Future<T> evalAsync(Supplier<T> f) {
        return this.eventLoop.getExecutor().submit(() -> {
            return f.get();
        });
    }

    Context.Builder getContextBuilder() {
        return contextBuilder;
    }

    Context getJsContext() {
        return this.eventLoop.getJsContext();
    }

    ScheduledExecutorService getExecutor() {
        return this.eventLoop.getExecutor();
    }

    public EventLoop getEventLoop() {
        return eventLoop;
    }

}
