package co.featureflags.server;

import okhttp3.*;
import okio.ByteString;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Test {

    private OkHttpClient client;
    private Request request;
    private WebSocket webSocket;

    public Test() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        client = builder
                .connectTimeout(5, TimeUnit.SECONDS)
                .pingInterval(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build();
        Request.Builder requestBuilder = new Request.Builder();
        requestBuilder.url("ws://121.40.165.18:8800");
        request = requestBuilder.build();
        webSocket = client.newWebSocket(request, listener);
    }

    public static void main(String[] args) {
        new Test();
        System.out.println("FINISH");
    }    private WebSocketListener listener = new WebSocketListener() {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            System.out.println("open");
            webSocket.send("Hello World");
            //webSocket.close(1000, "byebye");
        }

        @Override
        public void onMessage(WebSocket webSocket, final String text) {
            System.out.println("recv message");
            Executors.newSingleThreadExecutor().execute(() -> System.out.println(text));
        }

        @Override
        public void onMessage(@NotNull WebSocket webSocket, @NotNull ByteString bytes) {
            System.out.println(bytes.toString());
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            System.out.println(String.format("%d, %s", code, reason));
            if (code != 1000) {
                reconnect();
            }

        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            t.printStackTrace();
            reconnect();
        }
    };

    public void reconnect() {
        webSocket = client.newWebSocket(request, listener);
    }




}
