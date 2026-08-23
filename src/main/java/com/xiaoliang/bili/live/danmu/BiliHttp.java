package com.xiaoliang.bili.live.danmu;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * B 站接口 HTTP 请求辅助类：共享 HttpClient + 统一超时 + 自动重试。
 * 网络波动（Connection reset / timed out）时最多重试 3 次，降低报错率。
 */
final class BiliHttp {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    private BiliHttp() {
    }

    /**
     * 发送请求并自动重试。IOException（含超时）时最多重试 MAX_ATTEMPTS 次，
     * 每次间隔 RETRY_DELAY。线程中断时不重试，直接向上抛出。
     */
    static HttpResponse<String> send(HttpRequest.Builder builder)
            throws IOException, InterruptedException {
        HttpRequest request = builder.timeout(REQUEST_TIMEOUT).build();
        IOException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                lastError = e;
                if (attempt < MAX_ATTEMPTS) {
                    Thread.sleep(RETRY_DELAY.toMillis());
                }
            }
        }
        throw lastError;
    }
}
