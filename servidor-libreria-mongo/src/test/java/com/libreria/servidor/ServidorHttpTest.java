package com.libreria.servidor;

import com.libreria.servidor.servicio.ServicioLibro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Levanta el servidor en un puerto real y le habla por HTTP como los clientes: CORS para el
 * cliente web y el registro en consola de quién hizo cada operación (encabezado X-Cliente).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ServidorHttpTest {

    private static final String ORIGEN_CLIENTE_WEB = "http://localhost:5173";

    @LocalServerPort
    int puerto;

    @Autowired
    MongoTemplate mongo;

    @Autowired
    ServicioLibro servicioLibro;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void datos() {
        mongo.getCollection("libros").deleteMany(new org.bson.Document());
        mongo.getCollection("ejemplares").deleteMany(new org.bson.Document());
        servicioLibro.crear(PruebaBase.libro(PruebaBase.ISBN_CIEN, "Cien años de soledad"));
    }

    @Test
    void respondeGraphQlPorPostConCors() throws Exception {
        HttpResponse<String> respuesta = enviar("web", "{\"query\":\"{ libros { contenido { isbn titulo } } }\"}");

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"data\"").contains(PruebaBase.ISBN_CIEN);
        assertThat(respuesta.headers().firstValue("Access-Control-Allow-Origin")).isPresent();
    }

    @Test
    void preflightCorsDelNavegador() throws Exception {
        HttpRequest preflight = HttpRequest.newBuilder(url())
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", ORIGEN_CLIENTE_WEB)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,x-cliente")
                .build();

        HttpResponse<String> respuesta = http.send(preflight, HttpResponse.BodyHandlers.ofString());

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.headers().firstValue("Access-Control-Allow-Methods")).isPresent();
    }

    @Test
    void registraCadaOperacionConElClienteQueLaHizo(CapturedOutput consola) throws Exception {
        enviar("movil", "{\"query\":\"query ($isbn: ID!) { libroPorIsbn(isbn: $isbn) { titulo } }\","
                + "\"variables\":{\"isbn\":\"978-0307474728\"}}");
        enviar("web", "{\"query\":\"mutation { eliminarLibro(isbn: \\\"978-0000000000\\\") { isbn } }\"}");

        assertThat(consola.getOut())
                .contains("App móvil    | consulta libroPorIsbn (isbn=978-0307474728)")
                .contains("Cliente web  | mutación eliminarLibro  -> RECHAZADA: No existe un libro con el ISBN 978-0000000000.");
    }

    private HttpResponse<String> enviar(String cliente, String cuerpo) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder(url())
                .header("Content-Type", "application/json")
                .header("Origin", ORIGEN_CLIENTE_WEB)
                .header("X-Cliente", cliente)
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .build();
        return http.send(peticion, HttpResponse.BodyHandlers.ofString());
    }

    private URI url() {
        return URI.create("http://localhost:" + puerto + "/graphql");
    }
}
