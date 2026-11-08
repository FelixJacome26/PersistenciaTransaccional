package chat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Hilo del servidor que atiende a UN cliente.
 * Protocolo (una línea de texto por mensaje):
 *   Cliente -> Servidor:  primera línea = nombre de usuario
 *                         /lista            pide la lista de conectados
 *                         /chat <usuario>   elige con quién conversar
 *                         chao              abandona el chat
 *                         <texto>           mensaje para el usuario elegido
 *   Servidor -> Cliente:  OK | ERROR <motivo> | USUARIOS <a,b,c> | INFO <texto> | <usuario>--> <texto>
 */
public class ManejadorCliente extends Thread {

    private final Socket socket;
    private final Servidor servidor;
    private BufferedReader entrada;
    private PrintWriter salida;
    private String nombre;
    private volatile ManejadorCliente pareja;   // usuario con quien conversa

    public ManejadorCliente(Socket socket, Servidor servidor) {
        this.socket = socket;
        this.servidor = servidor;
    }

    public String getNombre() {
        return nombre;
    }

    /** Envía una línea a este cliente. */
    public void enviar(String mensaje) {
        salida.println(mensaje);
    }

    @Override
    public void run() {
        try {
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            salida = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);

            // 1. Identificación del usuario
            String propuesto = entrada.readLine();
            if (propuesto == null || propuesto.isBlank() || propuesto.contains(",") || propuesto.contains(" ")) {
                enviar("ERROR nombre inválido");
                return;
            }
            nombre = propuesto.trim();
            if (!servidor.registrar(nombre, this)) {
                enviar("ERROR el nombre " + nombre + " ya está en uso");
                nombre = null;      // no retirar al otro usuario que sí tiene ese nombre
                return;
            }
            enviar("OK");
            enviar("USUARIOS " + servidor.listaUsuarios());

            // 2. Ciclo de conversación
            String linea;
            while ((linea = entrada.readLine()) != null) {
                if (linea.equalsIgnoreCase("chao")) {
                    if (pareja != null) reenviar(linea);   // el otro usuario ve el "chao"
                    enviar("INFO Hasta pronto, " + nombre);
                    break;
                } else if (linea.equalsIgnoreCase("/lista")) {
                    enviar("USUARIOS " + servidor.listaUsuarios());
                } else if (linea.toLowerCase().startsWith("/chat ")) {
                    elegirPareja(linea.substring(6).trim());
                } else if (!linea.isBlank()) {
                    reenviar(linea);
                }
            }
        } catch (IOException e) {
            servidor.log("Conexión perdida con " + nombre + ": " + e.getMessage());
        } finally {
            cerrar();
        }
    }

    private void elegirPareja(String destino) {
        ManejadorCliente otro = servidor.buscar(destino);
        if (otro == null) {
            enviar("ERROR el usuario " + destino + " no está conectado");
        } else if (otro == this) {
            enviar("ERROR no puede chatear consigo mismo");
        } else {
            pareja = otro;
            enviar("INFO Ahora conversa con " + otro.getNombre());
            servidor.log(nombre + " eligió conversar con " + otro.getNombre());
        }
    }

    /** Reenvía el mensaje al usuario elegido, con el formato "usuario--> texto". */
    private void reenviar(String texto) {
        ManejadorCliente destino = pareja;
        if (destino == null) {
            enviar("ERROR primero elija un usuario con /chat <usuario>");
            return;
        }
        if (servidor.buscar(destino.getNombre()) == null) {
            enviar("ERROR " + destino.getNombre() + " ya no está conectado");
            pareja = null;
            return;
        }
        if (destino.pareja == null) {
            destino.pareja = this;      // el receptor queda emparejado para poder responder
        }
        destino.enviar(nombre + "--> " + texto);
        servidor.log(nombre + " -> " + destino.getNombre() + ": " + texto);
    }

    private void cerrar() {
        servidor.retirar(nombre);
        ManejadorCliente p = pareja;
        if (p != null && nombre != null) {
            p.enviar("INFO " + nombre + " abandonó el chat");
            if (p.pareja == this) p.pareja = null;
        }
        try {
            socket.close();
        } catch (IOException ignorada) {
            // el socket ya estaba cerrado
        }
    }
}
