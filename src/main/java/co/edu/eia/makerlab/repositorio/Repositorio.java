package co.edu.eia.makerlab.repositorio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import co.edu.eia.makerlab.excepcion.MakerLabException;
import co.edu.eia.makerlab.modelo.Identificable;

/**
 * Repositorio genérico en memoria. Una sola clase reemplaza los cinco
 * repositorios de la Entrega 1.
 *
 * T extends Identificable: solo acepta objetos con getId(), que es lo que
 * necesita para guardarlos y para no repetir identificadores.
 *
 * Map porque casi siempre se busca por id, y el Map lo encuentra sin
 * recorrer todo. LinkedHashMap para que listar() respete el orden de registro.
 */
public class Repositorio<T extends Identificable> {

    private Map<String, T> elementos;

    public Repositorio() {
        this.elementos = new LinkedHashMap<>();
    }

    public void agregar(T elemento) throws MakerLabException {
        if (elementos.containsKey(elemento.getId())) {
            throw new MakerLabException("Ya existe un registro con id " + elemento.getId() + ".");
        }
        elementos.put(elemento.getId(), elemento);
    }

    /**
     * Wildcard "? extends T" (el lado Producer de PECS): solo se leen
     * elementos de la lista. Por eso un Repositorio<Usuario> acepta una
     * List<Estudiante>.
     */
    public void agregarTodos(List<? extends T> nuevos) throws MakerLabException {
        for (T elemento : nuevos) {
            agregar(elemento);
        }
    }

    /** Devuelve null si no existe. */
    public T buscar(String id) {
        return elementos.get(id);
    }

    public boolean existe(String id) {
        return elementos.containsKey(id);
    }

    public List<T> listar() {
        return new ArrayList<>(elementos.values());
    }

    public int tamano() {
        return elementos.size();
    }

    /**
     * Genera el siguiente id con ese prefijo: busca el número más alto entre
     * los ids existentes y le suma uno, con ceros a la izquierda. Por ejemplo,
     * si existen R-230 y R-238, siguienteId("R-", 3) devuelve "R-239"; si no
     * hay ninguno, devuelve "R-001". Los ids que no tienen un número válido
     * después del prefijo se ignoran.
     *
     * Como el número sale de los datos cargados, también funciona después de
     * leer la base de datos, sin guardar ningún contador.
     */
    public String siguienteId(String prefijo, int digitos) {
        int mayor = 0;
        for (String id : elementos.keySet()) {
            if (id.startsWith(prefijo)) {
                String numero = id.substring(prefijo.length());
                if (esNumero(numero) && Integer.parseInt(numero) > mayor) {
                    mayor = Integer.parseInt(numero);
                }
            }
        }
        // %03d escribe el número con 3 cifras, rellenando con ceros: 7 -> 007.
        return prefijo + String.format("%0" + digitos + "d", mayor + 1);
    }

    /** true si el texto no está vacío y solo tiene dígitos del 0 al 9. */
    private boolean esNumero(String texto) {
        if (texto.isEmpty() || texto.length() > 9) {
            return false; // más de 9 cifras no cabe en un int
        }
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
