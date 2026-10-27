package co.edu.eia.makerlab.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON mínimo, sin librerías: escribe Map, List, String, Number, Boolean y
 * null, y lee JSON a esos mismos tipos. Alcanza para esta API y evita
 * depender de Jackson o Gson.
 */
public final class Json {

    private Json() {
    }

    /** Error de formato al leer JSON. */
    public static class FormatoException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public FormatoException(String mensaje) {
            super(mensaje);
        }
    }

    // ---------------------------------------------------------------- escribir

    public static String escribir(Object valor) {
        StringBuilder sb = new StringBuilder(256);
        escribir(sb, valor);
        return sb.toString();
    }

    private static void escribir(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String) {
            texto(sb, (String) v);
        } else if (v instanceof Boolean) {
            sb.append(v.toString());
        } else if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                sb.append("null");
            } else if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                sb.append((long) d);
            } else {
                sb.append(d);
            }
        } else if (v instanceof Number) {
            sb.append(v.toString());
        } else if (v instanceof Map) {
            sb.append('{');
            boolean primero = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                if (!primero) {
                    sb.append(',');
                }
                primero = false;
                texto(sb, String.valueOf(e.getKey()));
                sb.append(':');
                escribir(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof Iterable) {
            sb.append('[');
            boolean primero = true;
            for (Object o : (Iterable<?>) v) {
                if (!primero) {
                    sb.append(',');
                }
                primero = false;
                escribir(sb, o);
            }
            sb.append(']');
        } else if (v instanceof Enum) {
            texto(sb, ((Enum<?>) v).name());
        } else {
            texto(sb, v.toString());
        }
    }

    private static void texto(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
            case '"':
                sb.append("\\\"");
                break;
            case '\\':
                sb.append("\\\\");
                break;
            case '\n':
                sb.append("\\n");
                break;
            case '\r':
                sb.append("\\r");
                break;
            case '\t':
                sb.append("\\t");
                break;
            default:
                if (c < 0x20 || c == 0x2028 || c == 0x2029) {
                    sb.append(String.format("\\u%04x", (int) c));
                } else {
                    sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    /** Atajo para armar objetos: mapa("a", 1, "b", "x"). Conserva el orden. */
    public static Map<String, Object> mapa(Object... paresClaveValor) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < paresClaveValor.length; i += 2) {
            m.put((String) paresClaveValor[i], paresClaveValor[i + 1]);
        }
        return m;
    }

    // ------------------------------------------------------------------- leer

    /** Lee un JSON cuya raíz es un objeto. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> leerObjeto(String json) {
        Object raiz = leer(json);
        if (!(raiz instanceof Map)) {
            throw new FormatoException("El cuerpo debe ser un objeto JSON.");
        }
        return (Map<String, Object>) raiz;
    }

    public static Object leer(String json) {
        Lector l = new Lector(json);
        l.espacios();
        Object v = l.valor(0);
        l.espacios();
        if (l.pos != json.length()) {
            throw new FormatoException("Texto sobrante después del JSON.");
        }
        return v;
    }

    private static final class Lector {
        private final String s;
        private int pos = 0;

        Lector(String s) {
            this.s = s;
        }

        void espacios() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        FormatoException error(String que) {
            return new FormatoException("JSON inválido (" + que + ") en la posición " + pos + ".");
        }

        Object valor(int profundidad) {
            if (profundidad > 20) {
                throw error("demasiado anidado");
            }
            if (pos >= s.length()) {
                throw error("fin inesperado");
            }
            char c = s.charAt(pos);
            if (c == '{') {
                return objeto(profundidad);
            } else if (c == '[') {
                return arreglo(profundidad);
            } else if (c == '"') {
                return cadena();
            } else if (s.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            } else if (s.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            } else if (s.startsWith("null", pos)) {
                pos += 4;
                return null;
            } else if (c == '-' || (c >= '0' && c <= '9')) {
                return numero();
            }
            throw error("carácter inesperado '" + c + "'");
        }

        Map<String, Object> objeto(int profundidad) {
            Map<String, Object> m = new LinkedHashMap<>();
            pos++; // {
            espacios();
            if (pos < s.length() && s.charAt(pos) == '}') {
                pos++;
                return m;
            }
            while (true) {
                espacios();
                if (pos >= s.length() || s.charAt(pos) != '"') {
                    throw error("se esperaba un nombre de campo");
                }
                String clave = cadena();
                espacios();
                if (pos >= s.length() || s.charAt(pos) != ':') {
                    throw error("se esperaba ':'");
                }
                pos++;
                espacios();
                m.put(clave, valor(profundidad + 1));
                espacios();
                if (pos >= s.length()) {
                    throw error("fin inesperado");
                }
                char c = s.charAt(pos++);
                if (c == '}') {
                    return m;
                }
                if (c != ',') {
                    throw error("se esperaba ',' o '}'");
                }
            }
        }

        List<Object> arreglo(int profundidad) {
            List<Object> lista = new ArrayList<>();
            pos++; // [
            espacios();
            if (pos < s.length() && s.charAt(pos) == ']') {
                pos++;
                return lista;
            }
            while (true) {
                espacios();
                lista.add(valor(profundidad + 1));
                espacios();
                if (pos >= s.length()) {
                    throw error("fin inesperado");
                }
                char c = s.charAt(pos++);
                if (c == ']') {
                    return lista;
                }
                if (c != ',') {
                    throw error("se esperaba ',' o ']'");
                }
            }
        }

        String cadena() {
            StringBuilder sb = new StringBuilder();
            pos++; // comilla de apertura
            while (pos < s.length()) {
                char c = s.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (pos >= s.length()) {
                        break;
                    }
                    char e = s.charAt(pos++);
                    switch (e) {
                    case '"':
                    case '\\':
                    case '/':
                        sb.append(e);
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        if (pos + 4 > s.length()) {
                            throw error("escape \\u incompleto");
                        }
                        try {
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw error("escape \\u inválido");
                        }
                        pos += 4;
                        break;
                    default:
                        throw error("escape desconocido");
                    }
                } else if (c < 0x20) {
                    throw error("carácter de control sin escapar");
                } else {
                    sb.append(c);
                }
            }
            throw error("cadena sin cerrar");
        }

        Number numero() {
            int inicio = pos;
            if (s.charAt(pos) == '-') {
                pos++;
            }
            while (pos < s.length() && "0123456789.eE+-".indexOf(s.charAt(pos)) >= 0) {
                pos++;
            }
            String t = s.substring(inicio, pos);
            try {
                if (t.contains(".") || t.contains("e") || t.contains("E")) {
                    return Double.valueOf(t);
                }
                return Long.valueOf(t);
            } catch (NumberFormatException e) {
                throw error("número inválido");
            }
        }
    }
}
