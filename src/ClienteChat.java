import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ClienteChat {

    // Códigos ANSI para cores
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";     // Erros ou mensagens importantes
    private static final String ANSI_GREEN = "\u001B[32m";   // Mensagens do sistema
    private static final String ANSI_YELLOW = "\u001B[33m";  // Comandos
    private static final String ANSI_BLUE = "\u001B[34m";    // Mensagens de outros usuários

    public static void main(String[] args) throws IOException {
        String host = "localhost";  // Conectar ao servidor local
        if (args.length > 0) {
            host = args[0];
        }

        // Conectar ao servidor na porta 5000
        Socket socket = new Socket(host, 5000);
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        // Thread para enviar mensagens para o servidor
        Thread enviarThread = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (true) {
                String mensagem = scanner.nextLine();
                out.println(mensagem);

                // Exibir o comando enviado no console do cliente
                System.out.println(ANSI_YELLOW + "[Você]: " + ANSI_RESET + mensagem);
                
                if (mensagem.equalsIgnoreCase("/desconectar")) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        System.out.println(ANSI_RED + "Erro ao fechar a conexão: " + e.getMessage() + ANSI_RESET);
                    }
                    break;
                }
            }
        });

        // Thread para receber mensagens do servidor
        Thread receberThread = new Thread(() -> {
            try {
                String mensagem;
                while ((mensagem = in.readLine()) != null) {
                    if (mensagem.contains("[Sistema]")) {
                        System.out.println(ANSI_GREEN + mensagem + ANSI_RESET);
                    } else if (mensagem.contains("[Erro]")) {
                        System.out.println(ANSI_RED + mensagem + ANSI_RESET);
                    } else {
                        System.out.println(ANSI_BLUE + mensagem + ANSI_RESET);
                    }
                }
            } catch (IOException e) {
                System.out.println(ANSI_RED + "Erro na conexão: " + e.getMessage() + ANSI_RESET);
            }
        });

        enviarThread.start();
        receberThread.start();
    }
}
