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

        public Handler(Socket socket) {
            this.socket = socket;
        }

        // método executado quando a thread do cliente é iniciada
        public void run() {
            try {
                // inicializando os objetos de comunicação com o cliente
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                
                out.println("Bem vindo! Use /join <nome_sala> para entrar em uma sala.");


            } catch (IOException e) {
                System.out.println("Erro no cliente: " + e.getMessage());
            } finally {
                sairDaSala();
                try {
                    socket.close();
                } catch (IOException e) {

                }
            }
        }

        private synchronized void entrarEmsala(String nomeSala) {

        }

        private synchronized void sairDaSala() {

        }

        private synchronized void enviarMensagem(String mensagem) {

        }
    }
}