package chat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servidor del chat bidireccional.
 * Escucha en un puerto TCP, acepta clientes y crea un hilo (ManejadorCliente)
 * por cada uno. Mantiene la lista de usuarios conectados y reenvía mensajes.
 */
public class Servidor {

    public static final int PUERTO_POR_DEFECTO = 59420;

    // Usuarios conectados: nombre -> hilo que lo atiende (segura para varios hilos)
    private final Map<String, ManejadorCliente> usuarios = new ConcurrentHashMap<>();
    private final int puerto;

    public Servidor(int puerto) {
        this.puerto = puerto;
    }

    public void iniciar() {
        try (ServerSocket serverSocket = new ServerSocket(puerto)) {
            log("Servidor iniciado y contestando OK en el puerto " + puerto);
            while (true) {
                Socket socket = serverSocket.accept();          // espera (bloquea) hasta que llegue un cliente
                new ManejadorCliente(socket, this).start();     // lo atiende en un hilo aparte
            }
        } catch (IOException e) {
            log("Error en el servidor: " + e.getMessage());
        }
    }

    /** Registra un usuario; devuelve false si el nombre ya está en uso. */
    public boolean registrar(String nombre, ManejadorCliente manejador) {
        boolean ok = usuarios.putIfAbsent(nombre.toLowerCase(), manejador) == null;
        if (ok) {
            log("Usuario \"" + nombre + "\" conectado");
            mostrarConectados();
        }
        return ok;
    }

    /** Retira un usuario de la lista y deja constancia en el log. */
    public void retirar(String nombre) {
        if (nombre != null && usuarios.remove(nombre.toLowerCase()) != null) {
            log("El usuario \"" + nombre + "\" abandonó");
            mostrarConectados();
        }
    }

    public ManejadorCliente buscar(String nombre) {
        return usuarios.get(nombre.toLowerCase());
    }

    /** Lista de usuarios conectados, separada por comas. */
    public String listaUsuarios() {
        StringBuilder sb = new StringBuilder();
        for (ManejadorCliente m : usuarios.values()) {
            if (sb.length() > 0) sb.append(",");
            sb.append(m.getNombre());
        }
        return sb.toString();
    }

    private void mostrarConectados() {
        log("Conectados (" + usuarios.size() + "): " + listaUsuarios());
    }

    public synchronized void log(String texto) {
        String hora = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        System.out.println("[" + hora + "] " + texto);
    }

    public static void main(String[] args) {
        int puerto = args.length > 0 ? Integer.parseInt(args[0]) : PUERTO_POR_DEFECTO;
        new Servidor(puerto).iniciar();
    }
}
