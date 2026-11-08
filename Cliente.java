package chat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Cliente de consola del chat.
 * Pide IP, puerto y nombre; se conecta al servidor y usa dos hilos:
 *  - el hilo principal lee el teclado y envía al servidor;
 *  - un hilo receptor escucha al servidor y muestra lo que llega.
 */
public class Cliente {

    public static void main(String[] args) {
        Scanner teclado = new Scanner(System.in, StandardCharsets.UTF_8);

        String ip = preguntar(teclado, "IP del servidor [127.0.0.1]: ", "127.0.0.1");
        int puerto = Integer.parseInt(preguntar(teclado, "Puerto de la conexión [59420]: ", "59420"));
        String nombre = preguntar(teclado, "Escriba su nombre: ", "");

        try (Socket socket = new Socket(ip, puerto);
             BufferedReader entrada = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter salida = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {

            // 1. Credenciales
            salida.println(nombre);
            String respuesta = entrada.readLine();
            if (respuesta == null || !respuesta.equals("OK")) {
                System.out.println("No fue posible ingresar: " + respuesta);
                return;
            }
            System.out.println("Conectado como " + nombre + ".");
            System.out.println("Comandos: /lista  |  /chat <usuario>  |  chao para salir");

            // 2. Hilo receptor: muestra todo lo que envía el servidor
            Thread receptor = new Thread(() -> {
                try {
                    String linea;
                    while ((linea = entrada.readLine()) != null) {
                        System.out.println(formatear(linea));
                    }
                } catch (IOException e) {
                    // el socket se cerró: fin de la conversación
                }
                System.out.println("Conexión cerrada por el servidor.");
            });
            receptor.setDaemon(true);
            receptor.start();

            // 3. Hilo principal: lo que escribe el usuario se envía al servidor
            while (teclado.hasNextLine()) {
                String texto = teclado.nextLine();
                salida.println(texto);
                if (texto.equalsIgnoreCase("chao")) {
                    break;
                }
            }
            receptor.join(2000);   // da tiempo a mostrar la despedida del servidor
        } catch (IOException e) {
            System.out.println("No se pudo conectar con " + ip + ":" + puerto + " -> " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Convierte las respuestas del protocolo en texto legible. */
    private static String formatear(String linea) {
        if (linea.startsWith("USUARIOS ")) {
            return "Usuarios conectados: " + linea.substring(9) + "\nElija uno con /chat <usuario>";
        } else if (linea.startsWith("INFO ")) {
            return "*** " + linea.substring(5);
        } else if (linea.startsWith("ERROR ")) {
            return "!!! " + linea.substring(6);
        }
        return linea;   // mensaje de chat: "usuario--> texto"
    }

    private static String preguntar(Scanner teclado, String mensaje, String porDefecto) {
        System.out.print(mensaje);
        String valor = teclado.hasNextLine() ? teclado.nextLine().trim() : "";
        return valor.isEmpty() ? porDefecto : valor;
    }
}
