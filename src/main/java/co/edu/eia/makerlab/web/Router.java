package co.edu.eia.makerlab.web;

import java.util.ArrayList;
import java.util.List;

/** Tabla de rutas mínima: método + patrón como /api/reservas/{id}/cancelar. */
public final class Router {

    private static final class Ruta {
        final String metodo;
        final String[] partes;
        final Api.Manejador manejador;

        Ruta(String metodo, String patron, Api.Manejador manejador) {
            this.metodo = metodo;
            this.partes = patron.substring(1).split("/");
            this.manejador = manejador;
        }
    }

    /** Resultado de buscar una ruta. */
    public static final class Coincidencia {
        Api.Manejador manejador; // null si la ruta existe pero con otro método
        boolean rutaExiste;
    }

    private final List<Ruta> rutas = new ArrayList<>();

    public void agregar(String metodo, String patron, Api.Manejador manejador) {
        rutas.add(new Ruta(metodo, patron, manejador));
    }

    /** Busca la ruta y deja los {parámetros} dentro de la petición. */
    public Coincidencia buscar(Peticion peticion) {
        Coincidencia resultado = new Coincidencia();
        String[] real = peticion.ruta().substring(1).split("/");
        for (Ruta ruta : rutas) {
            if (ruta.partes.length != real.length) {
                continue;
            }
            boolean coincide = true;
            for (int i = 0; i < real.length && coincide; i++) {
                String parte = ruta.partes[i];
                if (!(parte.startsWith("{") && parte.endsWith("}")) && !parte.equals(real[i])) {
                    coincide = false;
                }
            }
            if (!coincide) {
                continue;
            }
            resultado.rutaExiste = true;
            if (ruta.metodo.equals(peticion.metodo())) {
                for (int i = 0; i < real.length; i++) {
                    String parte = ruta.partes[i];
                    if (parte.startsWith("{")) {
                        peticion.ponerParametroRuta(parte.substring(1, parte.length() - 1), real[i]);
                    }
                }
                resultado.manejador = ruta.manejador;
                return resultado;
            }
        }
        return resultado;
    }
}
