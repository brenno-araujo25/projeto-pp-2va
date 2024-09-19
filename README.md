# Projeto da Disciplina de Paradigmas de Programação
Chat com clientes distribuídos (modelo cliente-servidor) em linguagem Java, utilizando concorrência.

Grupo composto por:
- Brenno Araújo Caldeira Silva
- Camila de Almeida Silva
- Jeane Vitória Félix da Silva
- Lucas Matias da Silva
- Lucas Xavier de Almeida

## Instruções para Executar

1. **Compilar o servidor e o cliente:**
   Na pasta `src`, compile os arquivos Java do servidor e do cliente. Use os seguintes comandos:

   ```bash
   javac ServidorChat.java
   javac ClienteChat.java


2. **Após a compilação, execute o servidor de chat:**
   Use os seguintes comandos:

   ```bash
   java ServidorChat


3. **Em outro terminal, ainda na pasta src, execute o cliente de chat:**
   Use os seguintes comandos:

   ```bash
   java ClienteChat

Você pode executar múltiplos clientes para simular conversas entre diferentes usuários. Para isso, abra mais terminais e repita o comando java ClienteChat.

## Detalhes sobre o projeto

### O que é modelado como thread/co-rotina
- No projeto, cada cliente que se conecta ao servidor de chat é modelado como uma **thread** separada. Isso permite que o servidor atenda a múltiplos clientes simultaneamente, gerenciando a comunicação entre eles sem bloqueio.
- Cada cliente possui duas **threads** separadas: uma para enviar mensagens e outra para receber mensagens. Isso garante que o cliente possa realizar ambas as operações de forma independente e simultânea.

### Recursos compartilhados
Os principais recursos compartilhados no projeto são:
- **Servidor**: O servidor de chat gerencia todas as conexões dos clientes.
- **Clientes**: Diversos clientes podem se conectar ao servidor, e cada um é tratado como uma thread separada.
- **Mapas e listas**: Estruturas de dados, como mapas que associam clientes às salas de chat e à lista de usuários conectados, são compartilhadas entre as threads.

Esses recursos são acessados e modificados por diversas threads, o que cria uma necessidade de controle de concorrência.

### Controle de concorrência
O controle de concorrência é gerenciado através do uso de coleções seguras para threads, como o `ConcurrentHashMap` e o `ConcurrentHashMap.newKeySet()`. Essas estruturas garantem que múltiplas threads possam acessar e modificar os dados compartilhados de forma segura, sem necessidade de usar mecanismos mais complexos como semáforos ou mutexes.

### Aspectos parametrizados por constantes globais
Alguns aspectos da implementação podem ser parametrizados:
- **Número máximo de threads**: Existe uma constante global `MAX_THREADS` que define o número máximo de threads que podem ser executadas simultaneamente. Isso ajuda a controlar a carga do servidor e evita a sobrecarga.
- **Porta do servidor**: A porta na qual o servidor escuta as conexões pode ser parametrizada através da constante global `PORTA`.
