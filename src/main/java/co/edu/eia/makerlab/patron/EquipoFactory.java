package co.edu.eia.makerlab.patron;

import co.edu.eia.makerlab.modelo.CortadoraLaser;
import co.edu.eia.makerlab.modelo.Equipo;
import co.edu.eia.makerlab.modelo.EstacionElectronica;
import co.edu.eia.makerlab.modelo.Impresora3D;
import co.edu.eia.makerlab.modelo.Laboratorio;
import co.edu.eia.makerlab.modelo.TipoEquipo;

/**
 * Patrón Factory: el menú solo sabe qué tipo eligió el usuario y lee dos
 * datos como texto. La fábrica decide qué subclase crear y convierte los
 * datos. Así el menú no necesita conocer las tres subclases.
 *
 * dato1 / dato2 según el tipo:
 *   IMPRESORA_3D: tecnología / material
 *   CORTADORA_LASER: potencia en watts (entero) / área de trabajo
 *   ESTACION_ELECTRONICA: voltaje máximo (decimal) / herramientas
 */
public class EquipoFactory {

    public static Equipo crear(TipoEquipo tipo, String codigo, String nombre, String nivelRiesgo,
            Laboratorio laboratorio, String dato1, String dato2) {
        if (tipo == TipoEquipo.IMPRESORA_3D) {
            return new Impresora3D(codigo, nombre, nivelRiesgo, laboratorio, dato1, dato2);
        } else if (tipo == TipoEquipo.CORTADORA_LASER) {
            return new CortadoraLaser(codigo, nombre, nivelRiesgo, laboratorio, Integer.parseInt(dato1), dato2);
        } else {
            return new EstacionElectronica(codigo, nombre, nivelRiesgo, laboratorio, Double.parseDouble(dato1), dato2);
        }
    }
}
