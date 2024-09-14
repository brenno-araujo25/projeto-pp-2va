import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Classe principal do servidor de chat.
 * Gerencia as conexões dos clientes e as salas de chat.
 */
public class ServidorChat {
    private static final int PORTA = 5000; // Porta onde o servidor escuta conexões
    private static final int MAX_THREADS = 100; // Número máximo de threads simultâneas
    private static final String HISTORICO_DIR = "./HistoricoSalas"; // Caminho para o diretório de histórico de salas
    private static final Map<String, Set<PrintWriter>> salasChat = new ConcurrentHashMap<>(); // Mapa de salas e seus membros
    private static final Map<PrintWriter, String> usuarios = new ConcurrentHashMap<>(); // Mapa de usuários e suas saídas

    public static void main(String[] args) throws Exception {
        // Cria o diretório para armazenar o histórico, se não existir
        File diretorioHistorico = new File(HISTORICO_DIR);
        if (!diretorioHistorico.exists()) {
            diretorioHistorico.mkdirs(); // Cria o diretório e seus pais se necessário
        }

        System.out.println("Servidor rodando na porta " + PORTA + "...");

        // Cria o pool de threads para gerenciar até 100 conexões simultâneas
        ExecutorService pool = Executors.newFixedThreadPool(MAX_THREADS);
        try (ServerSocket listener = new ServerSocket(PORTA)) { // Cria um socket para escutar conexões na porta 5000
            while (true) {
                pool.execute(new Handler(listener.accept())); // Cria um Handler para cada conexão recebida
            }
        }
    }

    /**
     * Classe interna para gerenciar a comunicação com os clientes.
     */
    private static class Handler implements Runnable {
        private Socket socket; // Socket do cliente
        private PrintWriter out; // Envia mensagens ao cliente
        private BufferedReader in; // Lê mensagens do cliente
        private String sala = ""; // Sala atual do cliente
        private String nomeCliente; // Nome do cliente
        private File historicoDaSala; // Arquivo para armazenar o histórico de mensagens da sala

        public Handler(Socket socket) {
            this.socket = socket;
        }

        /**
         * Gerencia a comunicação com o cliente, processando comandos e mensagens.
         */
        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Solicita o nome do cliente
                out.println("Bem vindo! Informe seu nome:");
                nomeCliente = in.readLine();
                System.out.println("Usuário " + nomeCliente + " conectado no servidor.");
                out.println("Bem vindo, " + nomeCliente + "! Use /join <nome_sala> para entrar em uma sala. (Use /help para ver comandos)");

                // Adiciona o usuário ao mapa de usuários
                usuarios.put(out, nomeCliente);

                String mensagem;
                while ((mensagem = in.readLine()) != null) {
                    if (mensagem.startsWith("/join ")) {
                        entrarEmSala(mensagem.substring(6).trim());
                    } else if (mensagem.startsWith("/sair")) {
                        sairDaSala();
                    } else if (mensagem.startsWith("/help")) {
                        mostrarComandos();
                    } else if (mensagem.equalsIgnoreCase("/desconectar")) {
                        desconectarCliente();
                    } else if (mensagem.equalsIgnoreCase("/salas")) {
                        listarSalas();
                    } else if (mensagem.startsWith("@")) {
                        enviarMensagemPrivada(mensagem);
                    } else if (mensagem.startsWith("/pesquisar ")) {
                        pesquisarMensagem(mensagem.substring(11).trim());
                    } else {
                        enviarMensagem(mensagem);
                    }
                }
            } catch (IOException e) {
                System.out.println("Usuário " + nomeCliente + " desconectado do servidor.");
            } finally {
                sairDaSala();
                try {
                    if (!socket.isClosed()) {socket.close();}
                } catch (IOException e) {
                    System.out.println("ERRO AO FECHAR O SOCKET: " + e.getMessage());
                }
                usuarios.remove(out); // Remove o usuário do mapa ao desconectar
            }
        }

        /**
         * Permite ao cliente entrar em uma sala de chat.
         * Se a sala não existir, é criada uma nova.
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
                    historicoDaSala.createNewFile(); // Cria o arquivo se não existir
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
            System.out.println("Usuário " + nomeCliente + " entrou na " + nomeSala + ".");
        }

        /**
         * Permite ao cliente sair da sala atual.
         * Remove o cliente da lista de membros da sala e exclui a sala se estiver vazia.
         */
        private synchronized void sairDaSala() {
            if (!sala.isEmpty()) {
                enviarMensagemParaOutros("Usuário " + nomeCliente + " saiu da sala.");
                out.println("Você saiu da " + sala + ".");
                System.out.println("Usuário " + nomeCliente + " saiu da " + sala + ".");

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
                        if (writer != out) {
                            writer.println("[" + dataHora + "] " + nomeCliente + ": " + mensagem);
                        }
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
         * Carrega e exibe o histórico de mensagens da sala para o usuário que acabou de entrar.
         */
        private synchronized void carregarHistorico() {
            if (historicoDaSala.exists() && historicoDaSala.length() > 0) {
                try (BufferedReader br = new BufferedReader(new FileReader(historicoDaSala))) {
                    String linha;
                    boolean temConteudo = false;
                    while ((linha = br.readLine()) != null) {
                        if (!temConteudo) {
                            out.println("\n---------- Histórico da sala ----------");
                            temConteudo = true;
                        }
                        out.println(linha);
                    }
                    if (temConteudo) {
                        out.println("---------------------------------------");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        /**
         * Envia uma mensagem privada para um usuário específico.
         * @param mensagem A mensagem privada a ser enviada
         */
        private synchronized void enviarMensagemPrivada(String mensagem) {
            int index = mensagem.indexOf(" ");
            if (index != -1) {
                String destinatario = mensagem.substring(1, index).trim();
                String mensagemPrivada = mensagem.substring(index + 1).trim();

                // obtém os usuários na sala atual do cliente
                Set<PrintWriter> clientesNaSala = salasChat.get(sala); 
                
                boolean encontrado = false;
                for (Map.Entry<PrintWriter, String> entry : usuarios.entrySet()) {
                    if (entry.getValue().equals(destinatario) && clientesNaSala.contains(entry.getKey())) { // checa se usuário é o destinatário e se está na mesma sala
                        PrintWriter destinatarioOut = entry.getKey();
                        destinatarioOut.println("Mensagem privada de " + nomeCliente + ": " + mensagemPrivada);
                        encontrado = true;
                        break;
                    }
                }
                if (!encontrado) {
                    out.println("Usuário " + destinatario + " não encontrado.");
                }
            } else {
                out.println("Formato incorreto para mensagem privada. Use @<nome_usuario> <mensagem>.");
            }
        }

        /**
         * Pesquisa mensagens no histórico da sala.
         * @param termo O termo a ser pesquisado
         */
        private synchronized void pesquisarMensagem(String termo) {
            try (BufferedReader br = new BufferedReader(new FileReader(historicoDaSala))) {
                String linha;
                boolean encontrou = false;
                while ((linha = br.readLine()) != null) {
                    if (linha.contains(termo)) {
                        out.println(linha);
                        encontrou = true;
                    }
                }
                if (!encontrou) {
                    out.println("Nenhuma mensagem encontrada contendo o termo: " + termo);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * Exibe a lista de salas de chat disponíveis.
         */
        private void listarSalas() {
            if (salasChat.isEmpty()) {
                out.println("Nenhuma sala de chat disponível.");
            } else {
                out.println("Salas disponíveis:");
                for (String nomeSala : salasChat.keySet()) {
                    out.println("- " + nomeSala);
                }
            }
        }

        /**
         * Exibe uma lista de comandos disponíveis para o cliente.
         */
        private void mostrarComandos() {
            out.println("Comandos disponíveis:");
            out.println("/join <nome_sala> - Entrar em uma sala de chat");
            out.println("/sair - Sair da sala atual");
            out.println("/desconectar - Desconectar do servidor");
            out.println("/salas - Listar salas disponíveis");
            out.println("/pesquisar <termo> - Pesquisar mensagens no histórico da sala");
            out.println("@<nome_usuario> <mensagem> - Enviar mensagem privada para um usuário");
        }

        /**
         * Desconecta o cliente do servidor e limpa recursos.
         */
        private void desconectarCliente() {
            out.println("Desconectando...");
            sairDaSala();
            try {
                if (!socket.isClosed()) {socket.close();}
            } catch (IOException e) {
                System.out.println("ERRO AO FECHAR O SOCKET: " + e.getMessage());
            }
            System.out.println("Usuário " + nomeCliente + " desconectado.");
        }
    }
}
