package co.edu.eia.makerlab.web;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Una petición HTTP ya leída: método, ruta, parámetros de la ruta ({id}),
 * parámetros de la URL (?a=b), cabeceras y cuerpo JSON. Trae los lectores de
 * campos con sus mensajes de error en español, para que la gente sepa qué
 * escribió mal.
 */
public final class Peticion {

    private final String metodo;
    private final String ruta;
    private final Map<String, String> parametrosRuta = new LinkedHashMap<>();
    private final Map<String, String> consulta;
    private final Map<String, String> cabeceras;
    private final String textoCuerpo;
    private Map<String, Object> cuerpo;

    Peticion(String metodo, String ruta, Map<String, String> consulta, Map<String, String> cabeceras,
            String textoCuerpo) {
        this.metodo = metodo;
        this.ruta = ruta;
        this.consulta = consulta;
        this.cabeceras = cabeceras;
        this.textoCuerpo = textoCuerpo;
    }

    public String metodo() {
        return metodo;
    }

    public String ruta() {
        return ruta;
    }

    void ponerParametroRuta(String nombre, String valor) {
        parametrosRuta.put(nombre, valor);
    }

    public String ruta(String nombre) {
        return parametrosRuta.get(nombre);
    }

    public String consulta(String nombre) {
        return consulta.get(nombre);
    }

    /** Las cabeceras se guardan en minúsculas. */
    public String cabecera(String nombre) {
        return cabeceras.get(nombre.toLowerCase());
    }

    /** Cuerpo JSON como objeto. Sin cuerpo devuelve un objeto vacío. */
    public Map<String, Object> cuerpo() {
        if (cuerpo == null) {
            if (textoCuerpo == null || textoCuerpo.isBlank()) {
                cuerpo = new LinkedHashMap<>();
            } else {
                try {
                    cuerpo = Json.leerObjeto(textoCuerpo);
                } catch (Json.FormatoException e) {
                    throw ApiError.datos("El cuerpo de la petición no es un JSON válido.");
                }
            }
        }
        return cuerpo;
    }

    // ---------------------------------------------------------- campos del cuerpo

    /** Texto obligatorio, sin espacios sobrantes y con un largo máximo (el de la columna en la base). */
    public String texto(String campo, String etiqueta, int maximo) {
        String valor = textoOpcional(campo, etiqueta, maximo);
        if (valor == null || valor.isEmpty()) {
            throw ApiError.datos("Falta " + etiqueta + ".");
        }
        return valor;
    }

    /** Texto que puede venir vacío: devuelve "" si no viene. */
    public String textoOpcional(String campo, String etiqueta, int maximo) {
        Object crudo = cuerpo().get(campo);
        if (crudo == null) {
            return "";
        }
        if (!(crudo instanceof String) && !(crudo instanceof Number)) {
            throw ApiError.datos(etiqueta.substring(0, 1).toUpperCase() + etiqueta.substring(1)
                    + " debe ser un texto.");
        }
        String valor = crudo.toString().trim();
        if (valor.length() > maximo) {
            throw ApiError.datos(etiqueta.substring(0, 1).toUpperCase() + etiqueta.substring(1)
                    + " no puede tener más de " + maximo + " caracteres.");
        }
        return valor;
    }

    public LocalDate fecha(String campo, String etiqueta) {
        return parseFecha(texto(campo, etiqueta, 10), etiqueta);
    }

    public LocalTime hora(String campo, String etiqueta) {
        return parseHora(texto(campo, etiqueta, 8), etiqueta);
    }

    public boolean booleano(String campo) {
        Object crudo = cuerpo().get(campo);
        return crudo instanceof Boolean ? (Boolean) crudo : "true".equalsIgnoreCase(String.valueOf(crudo));
    }

    // ------------------------------------------------------------ conversiones

    static LocalDate parseFecha(String texto, String etiqueta) {
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException e) {
            throw ApiError.datos("Revise " + etiqueta + ": use el formato AAAA-MM-DD.");
        }
    }

    /** Acepta HH:mm y HH:mm:ss. */
    static LocalTime parseHora(String texto, String etiqueta) {
        try {
            return LocalTime.parse(texto);
        } catch (DateTimeParseException e) {
            throw ApiError.datos("Revise " + etiqueta + ": use el formato HH:mm.");
        }
    }
}
