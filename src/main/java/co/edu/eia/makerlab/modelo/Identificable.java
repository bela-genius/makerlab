package co.edu.eia.makerlab.modelo;

/**
 * Todo lo que se guarda en un Repositorio tiene un id. Es el límite del
 * repositorio genérico: Repositorio<T extends Identificable>.
 */
public interface Identificable {

    String getId();
}
