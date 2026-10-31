package co.edu.eia.makerlab.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import co.edu.eia.makerlab.excepcion.MakerLabException;

/**
 * Punto de entrada de la versión web de MakerLab EIA.
 *
 * Usa el servidor HTTP que ya trae el JDK (com.sun.net.httpserver), así que
 * no agrega ninguna librería nueva: la única dependencia sigue siendo el
 * driver de PostgreSQL. Sirve la API REST (/api/...) y el frontend móvil
 * (carpeta src/main/resources/public).
 *
 * Variables de entorno: PORT, DATABASE_URL (o DB_URL/DB_USER/DB_PASSWORD, o
 * el archivo .env), DEMO_TOKEN, SQL_DIR, ALLOW_MEMORY_FALLBACK.
 */
public class ServidorWeb implements HttpHandler {

    private static final int MAX_CUERPO = 32 * 1024;
    private static final String CSP = "default-src 'self'; img-src 'self' data:; style-src 'self'; "
            + "script-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'";

    private final Api api;
    private final Router router;
    private volatile boolean listo = false;
    private final Map<String, Recurso> recursos = new ConcurrentHashMap<>();

    /** Archivo estático ya leído, con su etiqueta para el caché del navegador. */
    private static final class Recurso {
        final byte[] bytes;
        final String tipo;
        final String etag;

        Recurso(byte[] bytes, String tipo, String etag) {
            this.bytes = bytes;
            this.tipo = tipo;
            this.etag = etag;
        }
    }

    ServidorWeb(Api api, Router router) {
        this.api = api;
        this.router = router;
    }

    public static void main(String[] args) throws Exception {
        Path env = Path.of(".env");
        String puertoTexto = ConfiguracionEntorno.variable("PORT", env);
        int puerto = Integer.parseInt(puertoTexto == null ? "8080" : puertoTexto);
        String tokenDemo = ConfiguracionEntorno.variable("DEMO_TOKEN", env);
        AlmacenMakerLab almacen = new AlmacenMakerLab();
        Api api = new Api(almacen, tokenDemo);
        Router router = new Router();
        api.registrar(router);
        ServidorWeb servidor = new ServidorWeb(api, router);

        // El puerto se abre de inmediato (Render lo exige); los datos se cargan en segundo plano.
        HttpServer http = HttpServer.create(new InetSocketAddress(puerto), 128);
        http.createContext("/", servidor);
        http.setExecutor(Executors.newFixedThreadPool(32));
        http.start();
        System.out.println("[arranque] MakerLab EIA web escuchando en el puerto " + puerto);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> http.stop(1)));

        try {
            System.out.println("[arranque] " + almacen.iniciar());
            servidor.listo = true;
        } catch (MakerLabException e) {
            // Mejor caerse con un error claro que atender gente sin base de datos.
            System.err.println("[arranque] ERROR: " + e.getMessage());
            http.stop(0);
            System.exit(1);
        }
        if (tokenDemo == null) {
            System.out.println("[arranque] Aviso: DEMO_TOKEN no está definido; el modo demostración está desactivado.");
        }
    }

    // ------------------------------------------------------------------ HTTP

    @Override
    public void handle(HttpExchange ex) throws IOException {
        long inicio = System.nanoTime();
        int estado = 500;
        String ruta = "?";
        try {
            ruta = ex.getRequestURI().getPath();
            if (ruta == null || ruta.isEmpty()) {
                ruta = "/";
            }
            if (ruta.startsWith("/api/") || ruta.equals("/api")) {
                estado = manejarApi(ex, ruta);
            } else {
                estado = manejarEstatico(ex, ruta);
            }
        } catch (Throwable t) {
            System.err.println("[http] Error en " + ruta + ": " + t);
            try {
                estado = escribirJson(ex, 500, Api.error("ErrorInterno", "Error interno del servidor."));
            } catch (Throwable ignorado) {
                // la conexión ya se cerró
            }
        } finally {
            ex.close();
            if (ruta.startsWith("/api/") && !ruta.equals("/api/salud")) {
                System.out.println("[api] " + ex.getRequestMethod() + " " + ruta + " " + estado + " "
                        + (System.nanoTime() - inicio) / 1_000_000 + " ms");
            }
        }
    }

    private int manejarApi(HttpExchange ex, String ruta) throws IOException {
        String metodo = ex.getRequestMethod().toUpperCase();
        if (metodo.equals("HEAD")) {
            metodo = "GET";
        }
        byte[] cuerpo = new byte[0];
        if (metodo.equals("POST") || metodo.equals("PUT")) {
            try (InputStream in = ex.getRequestBody()) {
                cuerpo = in.readNBytes(MAX_CUERPO + 1);
            }
            if (cuerpo.length > MAX_CUERPO) {
                return escribirJson(ex, 413, Api.error("CuerpoGrande", "La petición es demasiado grande."));
            }
        }
        Map<String, String> cabeceras = new HashMap<>();
        for (Map.Entry<String, List<String>> e : ex.getRequestHeaders().entrySet()) {
            if (e.getKey() != null && !e.getValue().isEmpty()) {
                cabeceras.put(e.getKey().toLowerCase(), e.getValue().get(0));
            }
        }
        Peticion peticion = new Peticion(metodo, ruta, consulta(ex.getRequestURI().getRawQuery()), cabeceras,
                new String(cuerpo, StandardCharsets.UTF_8));

        Router.Coincidencia c = router.buscar(peticion);
        if (c.manejador == null) {
            return c.rutaExiste ? escribirJson(ex, 405, Api.error("MetodoNoPermitido", "Método no permitido."))
                    : escribirJson(ex, 404, Api.error("NoExiste", "Ruta no encontrada."));
        }
        if (!listo && !ruta.equals("/api/salud")) {
            return escribirJson(ex, 503, Api.error("Iniciando",
                    "El servidor se está conectando a la base de datos. Intente en unos segundos."));
        }
        Api.Respuesta r = api.ejecutar(c.manejador, peticion);
        return escribirJson(ex, r.estado, r.cuerpo);
    }

    private static Map<String, String> consulta(String cruda) {
        Map<String, String> m = new LinkedHashMap<>();
        if (cruda == null || cruda.isEmpty()) {
            return m;
        }
        for (String par : cruda.split("&")) {
            int i = par.indexOf('=');
            String k = i < 0 ? par : par.substring(0, i);
            String v = i < 0 ? "" : par.substring(i + 1);
            m.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return m;
    }

    private int escribirJson(HttpExchange ex, int estado, Object cuerpo) throws IOException {
        byte[] bytes = Json.escribir(cuerpo).getBytes(StandardCharsets.UTF_8);
        Headers h = ex.getResponseHeaders();
        cabecerasComunes(ex, h);
        h.set("Content-Type", "application/json; charset=utf-8");
        h.set("Cache-Control", "no-store");
        enviar(ex, estado, bytes);
        return estado;
    }

    private void enviar(HttpExchange ex, int estado, byte[] bytes) throws IOException {
        boolean sinCuerpo = ex.getRequestMethod().equalsIgnoreCase("HEAD") || estado == 304 || estado == 204;
        ex.sendResponseHeaders(estado, sinCuerpo ? -1 : bytes.length);
        if (!sinCuerpo) {
            try (OutputStream out = ex.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    private static void cabecerasComunes(HttpExchange ex, Headers h) {
        h.set("X-Content-Type-Options", "nosniff");
        h.set("Referrer-Policy", "no-referrer");
        h.set("Content-Security-Policy", CSP);
        h.set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        if ("https".equalsIgnoreCase(ex.getRequestHeaders().getFirst("X-Forwarded-Proto"))) {
            h.set("Strict-Transport-Security", "max-age=15552000");
        }
    }

    // ------------------------------------------------------------- estáticos

    private int manejarEstatico(HttpExchange ex, String ruta) throws IOException {
        String metodo = ex.getRequestMethod().toUpperCase();
        if (!metodo.equals("GET") && !metodo.equals("HEAD")) {
            return escribirJson(ex, 405, Api.error("MetodoNoPermitido", "Método no permitido."));
        }
        if (ruta.equals("/")) {
            ruta = "/index.html";
        }
        Recurso recurso = null;
        // Solo nombres simples dentro de /public: nada de "..", ni "\", ni rutas escondidas.
        if (ruta.matches("/[A-Za-z0-9_\\-]+(\\.[A-Za-z0-9]+)?")) {
            recurso = recursos.get(ruta);
            if (recurso == null) {
                recurso = cargar(ruta);
                if (recurso != null) {
                    recursos.put(ruta, recurso);
                }
            }
        }
        if (recurso == null) {
            return escribirJson(ex, 404, Api.error("NoExiste", "No encontrado."));
        }
        Headers h = ex.getResponseHeaders();
        cabecerasComunes(ex, h);
        h.set("Content-Type", recurso.tipo);
        h.set("ETag", recurso.etag);
        h.set("Cache-Control", "no-cache"); // el navegador revisa el ETag en cada visita
        if (recurso.etag.equals(ex.getRequestHeaders().getFirst("If-None-Match"))) {
            enviar(ex, 304, new byte[0]);
            return 304;
        }
        enviar(ex, 200, recurso.bytes);
        return 200;
    }

    private Recurso cargar(String ruta) throws IOException {
        try (InputStream in = ServidorWeb.class.getResourceAsStream("/public" + ruta)) {
            if (in == null) {
                return null;
            }
            byte[] bytes = in.readAllBytes();
            return new Recurso(bytes, tipoDe(ruta), "\"" + hash(bytes) + "\"");
        }
    }

    private static String tipoDe(String ruta) {
        String r = ruta.toLowerCase();
        if (r.endsWith(".html")) {
            return "text/html; charset=utf-8";
        } else if (r.endsWith(".css")) {
            return "text/css; charset=utf-8";
        } else if (r.endsWith(".js")) {
            return "text/javascript; charset=utf-8";
        } else if (r.endsWith(".svg")) {
            return "image/svg+xml";
        } else if (r.endsWith(".ttf")) {
            return "font/ttf";
        } else if (r.endsWith(".png")) {
            return "image/png";
        } else if (r.endsWith(".ico")) {
            return "image/x-icon";
        } else if (r.endsWith(".json") || r.endsWith(".webmanifest")) {
            return "application/json; charset=utf-8";
        }
        return "application/octet-stream";
    }

    private static String hash(byte[] bytes) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", d[i]));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return Integer.toHexString(java.util.Arrays.hashCode(bytes));
        }
    }
}
