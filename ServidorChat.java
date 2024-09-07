import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ServidorChat {
    private static final int PORTA = 5000;
    private static final int MAX_THREADS = 100;
    // associa o nome da sala a um conjunto de PrintWriter, que envia mensagens para os clientes da sala
    private static final Map<String, Set<PrintWriter>> salasChat = new ConcurrentHashMap<>(); // ConcurrentHashMap garante que as operações sejam thread safe

    public static void main(String[] args) throws Exception {
        System.out.println("Servidor rodando na porta " + PORTA + "...");

        // cria o pool de threads para que o sistema atenda até 100 conexões simultâneas
        ExecutorService pool = Executors.newFixedThreadPool(MAX_THREADS);
        try (ServerSocket listener = new ServerSocket(PORTA)) { // cria um socket que espera conexões na porta 5000
            while (true) {
                pool.execute(new Handler(listener.accept())); // o servidor cria um Handler para lidar com a conexão do cliente em uma thread
            }
        }
    }

    // classe interna para gerenciar as conexões dos clientes
    private static class Handler implements Runnable {
        private Socket socket;      // socket do cliente, usado para se comunicar com ele
        private PrintWriter out;    // envia mensagens ao cliente
        private BufferedReader in;  // lê as mensagens do cliente
        private String sala = "";   // sala que o cliente está
        private String nomeCliente; //armazena o nome do cliente

        public Handler(Socket socket) {
            this.socket = socket;
        }

        // método executado quando a thread do cliente é iniciada
        public void run() {
            try {
                // inicializando os objetos de comunicação com o cliente
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                out.println("Bem vindo! Informe seu nome:");
                nomeCliente = in.readLine();
                out.println("Bem vindo " + nomeCliente + "! Use /join <nome_sala> para entrar em uma sala.");

                String mensagem;
                while ((mensagem = in.readLine()) != null) {
                    if (mensagem.startsWith("/join ")) {
                        entrarEmSala(mensagem.substring(6).trim());
                    } else if (mensagem.startsWith("/sair")) {
                        sairDaSala();
                    } else {
                        enviarMensagem(mensagem);
                    }
                }
            } catch (IOException e) {
                System.out.println("Erro no cliente: " + e.getMessage());
            } finally {
                sairDaSala();
                try {
                    socket.close();
                } catch (IOException e) {
                    System.out.println("Erro ao fechar o socket: " + e.getMessage());
                }
            }
        }

        private synchronized void entrarEmSala(String nomeSala) {
            // Chama sairDaSala se o cliente já estiver em uma sala
            if (!sala.isEmpty()) {
                sairDaSala();
            }

            sala = nomeSala;
            salasChat.putIfAbsent(nomeSala, ConcurrentHashMap.newKeySet());
            salasChat.get(nomeSala).add(out);

            // Envia mensagem de entrada para todos na sala, exceto o cliente que acabou de entrar
            enviarMensagemParaOutros("Usuário " + nomeCliente + " entrou na sala.");
            // Mensagem para o cliente que está entrando
            out.println("Você entrou na sala " + nomeSala);
        }

        private synchronized void sairDaSala() {
            if (!sala.isEmpty()) {
                // Envia mensagem de saída para todos na sala, exceto o cliente que acabou de sair
                enviarMensagemParaOutros("Usuário " + nomeCliente + " saiu da sala.");
                // Mensagem para o cliente que está saindo
                out.println("Você saiu da sala " + sala + ".");

                Set<PrintWriter> membrosSala = salasChat.get(sala);
                if (membrosSala != null) {
                    membrosSala.remove(out);
                    if (membrosSala.isEmpty()) {
                        salasChat.remove(sala); // Remove a sala se estiver vazia
                    }
                }
                sala = "";
            } else {
                out.println("Você não está em nenhuma sala para poder sair.");
            }
        }

        private synchronized void enviarMensagem(String mensagem) {
            if (!sala.isEmpty()) {
                Set<PrintWriter> membrosSala = salasChat.get(sala);
                if (membrosSala != null) {
                    for (PrintWriter writer : membrosSala) {
                        if (writer != out) { writer.println("[" + nomeCliente + "]: " + mensagem); }
                    }
                }
            } else {
                out.println("Você não está em uma sala. Use /join <nome_sala> para entrar em uma.");
            }
        }

        // Método para enviar mensagens para todos, exceto o cliente atual
        private synchronized void enviarMensagemParaOutros(String mensagem) {
            if (!sala.isEmpty()) {
                Set<PrintWriter> membrosSala = salasChat.get(sala);
                if (membrosSala != null) {
                    for (PrintWriter writer : membrosSala) {
                        // Envia a mensagem apenas para os outros clientes, não para o remetente
                        if (writer != out) { writer.println(mensagem); }
                    }
                }
            }
        }
    }
}
