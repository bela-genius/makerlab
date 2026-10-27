package co.edu.eia.makerlab.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import co.edu.eia.makerlab.excepcion.MakerLabException;
import co.edu.eia.makerlab.persistencia.ConfiguracionBD;

/**
 * Decide de dónde sale la conexión a PostgreSQL, sin tocar ConfiguracionBD
 * (que sigue leyendo el .env como en la entrega original). Orden de prioridad:
 *
 *   1. DATABASE_URL  (la entregan Neon, Supabase, Render, Railway...):
 *      postgresql://usuario:clave@host/base?sslmode=require
 *      También acepta una URL que ya empiece por jdbc:postgresql://
 *   2. DB_URL, DB_USER y DB_PASSWORD como variables de entorno.
 *   3. El archivo .env de la carpeta actual (respaldo local).
 *
 * Nunca se imprime la clave: los mensajes solo dicen de dónde salió la conexión.
 */
public final class ConfiguracionEntorno {

    private ConfiguracionEntorno() {
    }

    /** Conexión resuelta y una descripción segura (sin clave) de su origen. */
    public static final class Resultado {
        private final ConfiguracionBD configuracion;
        private final String origen;

        Resultado(ConfiguracionBD configuracion, String origen) {
            this.configuracion = configuracion;
            this.origen = origen;
        }

        public ConfiguracionBD getConfiguracion() {
            return configuracion;
        }

        public String getOrigen() {
            return origen;
        }
    }

    /** Variable de entorno; si no existe, la misma clave en el archivo .env (solo comodidad para el portátil). */
    public static String variable(String nombre, Path archivoEnv) {
        String v = System.getenv(nombre);
        if (v != null && !v.isBlank()) {
            return v.trim();
        }
        if (archivoEnv != null && Files.exists(archivoEnv)) {
            java.util.Properties datos = new java.util.Properties();
            try (java.io.Reader lector = Files.newBufferedReader(archivoEnv)) {
                datos.load(lector);
                String d = datos.getProperty(nombre);
                return d == null || d.isBlank() ? null : d.trim();
            } catch (java.io.IOException e) {
                return null;
            }
        }
        return null;
    }

    /** Devuelve null si no hay ninguna configuración (entonces se trabaja en memoria). */
    public static Resultado resolver(Map<String, String> entorno, Path archivoEnv) throws MakerLabException {
        String databaseUrl = valor(entorno, "DATABASE_URL");
        if (databaseUrl != null) {
            return desdeDatabaseUrl(databaseUrl, entorno);
        }
        String dbUrl = valor(entorno, "DB_URL");
        if (dbUrl != null) {
            String usuario = valor(entorno, "DB_USER");
            if (usuario == null) {
                throw new MakerLabException("Con DB_URL también se necesita DB_USER (y DB_PASSWORD).");
            }
            String clave = entorno.getOrDefault("DB_PASSWORD", "");
            return new Resultado(new ConfiguracionBD(normalizarJdbc(dbUrl), usuario, clave.trim()),
                    "variables DB_URL/DB_USER/DB_PASSWORD");
        }
        if (archivoEnv != null && Files.exists(archivoEnv)) {
            ConfiguracionBD desdeArchivo = ConfiguracionBD.desdeArchivo(archivoEnv);
            return new Resultado(new ConfiguracionBD(normalizarJdbc(desdeArchivo.getUrl()),
                    desdeArchivo.getUsuario(), desdeArchivo.getClave()), "archivo " + archivoEnv.getFileName());
        }
        return null;
    }

    private static String valor(Map<String, String> entorno, String nombre) {
        String v = entorno.get(nombre);
        return v == null || v.isBlank() ? null : v.trim();
    }

    /** Acepta postgres://, postgresql:// o jdbc:postgresql://. */
    static Resultado desdeDatabaseUrl(String valor, Map<String, String> entorno) throws MakerLabException {
        if (valor.startsWith("jdbc:")) {
            String usuario = valor(entorno, "DB_USER");
            String clave = entorno.getOrDefault("DB_PASSWORD", "");
            String usuarioUrl = parametro(valor, "user");
            if (usuarioUrl != null) {
                usuario = usuarioUrl;
                String claveUrl = parametro(valor, "password");
                clave = claveUrl != null ? claveUrl : clave;
            }
            if (usuario == null) {
                throw new MakerLabException("DATABASE_URL en formato jdbc: necesita user=... en la URL o DB_USER.");
            }
            return new Resultado(new ConfiguracionBD(normalizarJdbc(valor), usuario, clave.trim()), "DATABASE_URL");
        }
        URI uri;
        try {
            uri = new URI(valor);
        } catch (URISyntaxException e) {
            // No se repite el valor: contiene la clave.
            throw new MakerLabException(
                    "DATABASE_URL no tiene un formato válido. Debe ser postgresql://usuario:clave@host/base "
                            + "(si la clave tiene símbolos como @ # / ?, hay que codificarlos con %).");
        }
        String esquema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!esquema.equals("postgres") && !esquema.equals("postgresql")) {
            throw new MakerLabException("DATABASE_URL debe empezar por postgresql:// (o postgres://).");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new MakerLabException("DATABASE_URL no tiene el host de la base de datos.");
        }
        String info = uri.getUserInfo();
        if (info == null || info.isBlank()) {
            throw new MakerLabException("DATABASE_URL no tiene usuario (postgresql://usuario:clave@host/base).");
        }
        int dosPuntos = info.indexOf(':');
        String usuario = dosPuntos < 0 ? info : info.substring(0, dosPuntos);
        String clave = dosPuntos < 0 ? "" : info.substring(dosPuntos + 1);
        int puerto = uri.getPort() == -1 ? 5432 : uri.getPort();
        String base = uri.getPath() == null ? "" : uri.getPath();
        if (base.isEmpty() || base.equals("/")) {
            base = "/postgres";
        }
        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(uri.getHost()).append(':').append(puerto)
                .append(base);
        if (uri.getRawQuery() != null && !uri.getRawQuery().isBlank()) {
            jdbc.append('?').append(uri.getRawQuery());
        }
        return new Resultado(new ConfiguracionBD(normalizarJdbc(jdbc.toString()), usuario, clave),
                "DATABASE_URL (" + uri.getHost() + base + ")");
    }

    /**
     * Ajusta la URL JDBC para un servicio en la nube: quita parámetros que el
     * driver no entiende (channel_binding), exige SSL fuera de localhost y
     * pone tiempos máximos para que una base dormida o caída no cuelgue la app.
     */
    static String normalizarJdbc(String url) {
        String sinParametros = url;
        List<String> params = new ArrayList<>();
        int q = url.indexOf('?');
        if (q >= 0) {
            sinParametros = url.substring(0, q);
            for (String p : url.substring(q + 1).split("&")) {
                if (p.isBlank() || p.toLowerCase(Locale.ROOT).startsWith("channel_binding=")) {
                    continue;
                }
                params.add(p);
            }
        }
        String host = hostDe(sinParametros);
        boolean local = host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]");
        if (!tiene(params, "sslmode") && !local) {
            params.add("sslmode=require");
        }
        if (!tiene(params, "connectTimeout")) {
            params.add("connectTimeout=10");
        }
        if (!tiene(params, "socketTimeout")) {
            params.add("socketTimeout=60");
        }
        if (!tiene(params, "ApplicationName")) {
            params.add("ApplicationName=makerlab-web");
        }
        // El pooler de Neon/Supabase (PgBouncer) puede no soportar sentencias preparadas del lado del servidor.
        if (host.contains("-pooler") && !tiene(params, "prepareThreshold")) {
            params.add("prepareThreshold=0");
        }
        return sinParametros + "?" + String.join("&", params);
    }

    private static String hostDe(String urlSinParametros) {
        int inicio = urlSinParametros.indexOf("://");
        String resto = inicio >= 0 ? urlSinParametros.substring(inicio + 3) : urlSinParametros;
        int fin = resto.indexOf('/');
        String hostPuerto = fin >= 0 ? resto.substring(0, fin) : resto;
        int arroba = hostPuerto.lastIndexOf('@');
        if (arroba >= 0) {
            hostPuerto = hostPuerto.substring(arroba + 1);
        }
        if (hostPuerto.startsWith("[")) {
            int cierre = hostPuerto.indexOf(']');
            return cierre >= 0 ? hostPuerto.substring(0, cierre + 1).toLowerCase(Locale.ROOT)
                    : hostPuerto.toLowerCase(Locale.ROOT);
        }
        int dosPuntos = hostPuerto.indexOf(':');
        return (dosPuntos >= 0 ? hostPuerto.substring(0, dosPuntos) : hostPuerto).toLowerCase(Locale.ROOT);
    }

    private static boolean tiene(List<String> params, String nombre) {
        for (String p : params) {
            if (p.toLowerCase(Locale.ROOT).startsWith(nombre.toLowerCase(Locale.ROOT) + "=")) {
                return true;
            }
        }
        return false;
    }

    private static String parametro(String url, String nombre) {
        int q = url.indexOf('?');
        if (q < 0) {
            return null;
        }
        for (String p : url.substring(q + 1).split("&")) {
            if (p.toLowerCase(Locale.ROOT).startsWith(nombre.toLowerCase(Locale.ROOT) + "=")) {
                return p.substring(nombre.length() + 1);
            }
        }
        return null;
    }
}
