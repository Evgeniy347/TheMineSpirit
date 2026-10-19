package com.theminespirit.guards.archfixture;

import java.net.http.HttpClient;

public final class HttpClientUser {
    private HttpClientUser() {}

    public static HttpClient client() {
        return HttpClient.newHttpClient();
    }
}
