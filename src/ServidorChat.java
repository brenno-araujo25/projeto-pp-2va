import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ServidorChat {
    private static final int PORTA = 5000;
    private static final int MAX_THREADS = 100;
    private static final String HISTORICO_DIR = "./HistoricoSalas"; // Caminho da pasta para armazenar logs
    private static final Map<String, Set<PrintWriter>> salasChat = new ConcurrentHashMap<>(); // ConcurrentHashMap garante que as operações sejam thread safe

    public static void main(String[] args) throws Exception {
        // Criar a pasta de histórico, se ela não existir
        File diretorioHistorico = new File(HISTORICO_DIR);
        if (!diretorioHistorico.exists()) {
            diretorioHistorico.mkdirs(); // Cria a pasta e seus diretórios pai se necessário
        }

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
        private String nomeCliente; // armazena o nome do cliente
        private File historicoDaSala;   // arquivo onde as mensagens da sala serão salvas

        public Handler(Socket socket) {
            this.socket = socket;
        }

        /**
         * Executa o fluxo de comunicação do cliente com o servidor.
         * Lê o nome do cliente, permite entrar em salas e envia/recebe mensagens.
         */
        public void run() {
            try {
                // inicializando os objetos de comunicação com o cliente
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                out.println("Bem vindo! Informe seu nome:");
                nomeCliente = in.readLine();
                out.println("Bem vindo, " + nomeCliente + "! Use /join <nome_sala> para entrar em uma sala. (Use /help para ver comandos)");

                String mensagem;
                while ((mensagem = in.readLine()) != null) {
                    if (mensagem.startsWith("/join ")) {
                        entrarEmSala(mensagem.substring(6).trim());
                    } else if (mensagem.startsWith("/sair")) {
                        sairDaSala();
                    } else if (mensagem.startsWith("/help")) {
                        mostrarComandos();
                    } else if (mensagem.equalsIgnoreCase("/desconectar")){
                        desconectarCliente();
                    }else if (mensagem.equalsIgnoreCase("/salas")){
                        listarSalas();
                    }else{
                        enviarMensagem(mensagem);
                    }
                }
            } catch (IOException e) {
                System.out.println("ERRO NO CLIENTE: " + e.getMessage());
            } finally {
                sairDaSala();
                try {
                    socket.close();
                } catch (IOException e) {
                    System.out.println("ERRO AO FECHAR O SOCKET: " + e.getMessage());
                }
            }
        }

        /**
         * Entra em uma sala de chat.
         * Se a sala não existir, cria uma nova.
         * @param nomeSala O nome da sala em que o cliente deseja entrar
         */
        private synchronized void entrarEmSala(String nomeSala) {
            if (!sala.isEmpty()) {
                sairDaSala();
            }

            sala = nomeSala;
            salasChat.putIfAbsent(nomeSala, ConcurrentHashMap.newKeySet());
            salasChat.get(nomeSala).add(out);

            // Define o arquivo para armazenar as mensagens da sala
            historicoDaSala = new File(HISTORICO_DIR, sala + ".txt");

            if (!historicoDaSala.exists()) {
                try {
                    historicoDaSala.createNewFile(); // Cria o arquivo
                } catch (IOException e){
                    e.printStackTrace();
                }
            }

            // Mensagem para o cliente que está entrando
            out.println("Você entrou na " + nomeSala);

            // Carregar e exibir o histórico de mensagens para o cliente que entrou na sala
            carregarHistorico();

            // Envia mensagem de entrada para todos na sala, exceto o cliente que acabou de entrar
            enviarMensagemParaOutros("Usuário " + nomeCliente + " entrou na sala.");
        }

        /**
         * Sai da sala de chat atual, se o cliente estiver em uma.
         * Remove o cliente da lista de membros da sala.
         */
        private synchronized void sairDaSala() {
            if (!sala.isEmpty()) {
                enviarMensagemParaOutros("Usuário " + nomeCliente + " saiu da sala.");
                out.println("Você saiu da " + sala + ".");

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

        /**
         * Envia uma mensagem para todos os membros da sala, exceto o remetente.
         * @param mensagem A mensagem a ser enviada
         */
        private synchronized void enviarMensagem(String mensagem) {
            if (!sala.isEmpty()) {
                Set<PrintWriter> membrosSala = salasChat.get(sala);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
                String dataHora = LocalDateTime.now().format(formatter);
                
                if (membrosSala != null) {
                    for (PrintWriter writer : membrosSala) {
                        if (writer != out) { writer.println("[" + dataHora + "] " + nomeCliente + ": " + mensagem); }
                    }
                }
                salvarMensagem("[" + dataHora + "] " + nomeCliente + ": " + mensagem);
            } else {
                out.println("Você não está em uma sala. Use /join <nome_sala> para entrar em uma.");
            }
        }

        /**
         * Envia uma mensagem para todos os membros da sala, exceto o remetente.
         * @param mensagem A mensagem a ser enviada para os outros usuários
         */
        private synchronized void enviarMensagemParaOutros(String mensagem) {
            if (!sala.isEmpty()) {
                Set<PrintWriter> membrosSala = salasChat.get(sala);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
                String dataHora = LocalDateTime.now().format(formatter);
                
                if (membrosSala != null) {
                    for (PrintWriter writer : membrosSala) {
                        if (writer != out) { writer.println(mensagem); }
                    }
                }
                salvarMensagem("[" + dataHora + "] " + mensagem);
            }
        }

        /**
         * Salva a mensagem no arquivo de log da sala.
         * @param mensagem A mensagem a ser salva no arquivo
         */
        private synchronized void salvarMensagem(String mensagem) {
            try (PrintWriter logWriter = new PrintWriter(new FileWriter(historicoDaSala, true))) {
                logWriter.println(mensagem);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * Carrega e exibe o histórico de mensagens da sala para o cliente que acabou de entrar.
         */
        private synchronized void carregarHistorico() {
            if (historicoDaSala.exists() && historicoDaSala.length() > 0) {
                try (BufferedReader br = new BufferedReader(new FileReader(historicoDaSala))) {
                    String linha;
                    boolean temConteudo = false;
                    while ((linha = br.readLine()) != null) {
                        if (!temConteudo) {
                            out.println("\n--- Histórico da sala ---");
                            temConteudo = true;
                        }
                        out.println(linha);
                    }
                    if (temConteudo) {
                        out.println("-------------------------");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private synchronized void desconectarCliente() throws IOException {
            final String ANSI_RESET = "\u001B[0m";
            final String ANSI_RED = "\u001B[31m";

            try {
                if(!sala.isEmpty()) { // Verifica se o usuário está dentro de uma sala
                    sairDaSala(); // Remove o cliente da sala
                }

                if (out != null) {
                    out.println("[Sistema] Desconectando do servidor..."); // Notifica o cliente da desconexão
                    out.flush();
                    try {
                        Thread.sleep(100); // Aguarda para garantir a entrega da mensagem
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    out.close(); // Fecha o PrintWriter
                }

                if (in != null) {
                    in.close();
                }

                if (socket != null && !socket.isClosed()) {
                    socket.close(); // Fecha o socket do cliente, se ainda estiver aberto
                }

            } catch (IOException e) {
                System.out.println(ANSI_RED + "Erro ao fechar a conexão: " + e.getMessage() + ANSI_RESET);
                e.printStackTrace();
            }
        }

        private synchronized void listarSalas() throws IOException {
            
            //Criar o caminho para pasta de historicoSalas
            File caminhoHistorico = new File(HISTORICO_DIR);
            //Verificar se o caminho existe 
            if(caminhoHistorico.exists() && caminhoHistorico.isDirectory()){
                File[] historicos = caminhoHistorico.listFiles((hist,name) -> name.endsWith(".txt")); 
                System.out.println("-------------------------");
                System.out.println("Listando Salas!");
                if (historicos !=null && historicos.length > 0){
                    System.out.println("-------------------------");
                    System.out.println("Salas existentes: ");
                    for(File historico: historicos){
                        //Tirar o .txt
                        String nome = historico.getName().replace(".txt","");
                        System.out.println("- " + nome);
                    }
                } else {
                    System.out.println("Nunhuma sala existente!");
                }
                System.out.println("-------------------------");
            }else{
                System.out.println("Caminho para HistoricoSalas invalido!");
            }

           
        }

        /** 
         * Exibe a lista de comandos disponíveis para o cliente.
         */
        private synchronized void mostrarComandos() {
            out.println("\n- /help (exibe o menu de comandos)");
            out.println("- /join <nome_sala> (permite entrar em uma sala)");
            out.println("- /sair (sai de uma sala)");
            out.println("- /desconectar (sai do servidor)");
            out.println("- /salas (lista as salas existentes no servidor)");
            out.println("- /usuários (exibe os usuários online dentro de uma sala)");
            out.println("- @nomeUsuário (envia a mensagem somente para um determinado usuário)\n");
        }
    }
}
