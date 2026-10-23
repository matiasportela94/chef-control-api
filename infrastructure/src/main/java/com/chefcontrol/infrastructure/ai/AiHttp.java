package com.chefcontrol.infrastructure.ai;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Sin esto la llamada al modelo no tenía timeout de lectura: un proveedor colgado dejaba colgado el request. 120 s
 * porque interpretar una planilla de 300 filas tarda decenas de segundos (22 s medidos con gemini-2.5-flash).
 */
final class AiHttp {

    static final Duration READ_TIMEOUT = Duration.ofSeconds(120);

    private AiHttp() {}

    static ClientHttpRequestFactory requestFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }
}
