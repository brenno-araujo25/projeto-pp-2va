import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ClienteChat {
    public static void main(String[] args) throws IOException {
        // Conectar ao servidor local na porta 5000
        String host = "localhost";
        if (args.length > 0) {
            host = args[0];
        }

        Socket socket = new Socket(host, 5000);
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        Scanner scanner = new Scanner(System.in);

        // Thread para enviar mensagens ao servidor
        new Thread(() -> {
            String userInput;
            while (true) {
                userInput = scanner.nextLine();
                out.println(userInput);  // Envia a mensagem digitada para o servidor
            }
        }).start();

        // Thread para receber mensagens do servidor
        new Thread(() -> {
            String serverMessage;
            try {
                while ((serverMessage = in.readLine()) != null) {
                    System.out.println(serverMessage);  // Exibe as mensagens recebidas do servidor
                }
            } catch (IOException e) {
                System.out.println("Erro ao ler mensagem do servidor.");
                e.printStackTrace();
            }
        }).start();
    }
}
