import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

public class SimuladorDroneGrid extends JPanel {

    // ==========================================
    // 1. CONFIGURAÇÃO DA GRADE E DIMENSÕES
    // ==========================================
    private final int LINHAS = 16;
    private final int COLUNAS = 16;
    private final int TAMANHO_CELULA = 50;

    private final int LARGURA_MUNDO = COLUNAS * TAMANHO_CELULA;
    private final int ALTURA_MUNDO = LINHAS * TAMANHO_CELULA;

    private final int MINI_LARGURA = 200;
    private final int MINI_ALTURA = 200;
    private final int MINI_X = 20;
    private final int MINI_Y = 20;

    // ==========================================
    // 2. ESTRUTURA DO GRAFO (NÓS)
    // ==========================================
    class No {
        int linha, coluna;
        boolean ehObstaculo;
        List<No> vizinhos = new ArrayList<>();

        No(int linha, int coluna, boolean ehObstaculo) {
            this.linha = linha;
            this.coluna = coluna;
            this.ehObstaculo = ehObstaculo;
        }
    }

    private No[][] gradeNos = new No[LINHAS][COLUNAS];
    private No noAtualDrone;
    
    // ==========================================
    // 3. ESTADO DA ANIMAÇÃO E ROTAS
    // ==========================================
    private double droneX, droneY; // Posição em pixels para animação suave
    private List<No> currentPath = null;
    private int pathIndex = 0;
    private javax.swing.Timer timer;
    private String statusMessage = "Modo Livre: Use as setas ou clique em uma ação.";
    private Random random = new Random();

    public SimuladorDroneGrid() {
        setFocusable(true);
        
        // Inicializa mapa e timer de animação
        gerarMapaAleatorio();
        timer = new javax.swing.Timer(16, e -> animateDrone());

        // Controle Manual (Interrompe rotas automáticas)
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (currentPath != null) {
                    timer.stop();
                    currentPath = null;
                    statusMessage = "Piloto Automático desligado. Controle manual.";
                }

                No proximoNo = null;
                if (e.getKeyCode() == KeyEvent.VK_UP && noAtualDrone.linha > 0) {
                    proximoNo = gradeNos[noAtualDrone.linha - 1][noAtualDrone.coluna];
                }
                if (e.getKeyCode() == KeyEvent.VK_DOWN && noAtualDrone.linha < LINHAS - 1) {
                    proximoNo = gradeNos[noAtualDrone.linha + 1][noAtualDrone.coluna];
                }
                if (e.getKeyCode() == KeyEvent.VK_LEFT && noAtualDrone.coluna > 0) {
                    proximoNo = gradeNos[noAtualDrone.linha][noAtualDrone.coluna - 1];
                }
                if (e.getKeyCode() == KeyEvent.VK_RIGHT && noAtualDrone.coluna < COLUNAS - 1) {
                    proximoNo = gradeNos[noAtualDrone.linha][noAtualDrone.coluna + 1];
                }

                // Move apenas se for uma aresta válida (não for obstáculo)
                if (proximoNo != null && noAtualDrone.vizinhos.contains(proximoNo)) {
                    noAtualDrone = proximoNo;
                    droneX = noAtualDrone.coluna * TAMANHO_CELULA;
                    droneY = noAtualDrone.linha * TAMANHO_CELULA;
                }
                repaint();
            }
        });
    }

    // ==========================================
    // 4. LÓGICA DO MAPA E CLIMA
    // ==========================================
    public void gerarMapaAleatorio() {
        if (timer != null) timer.stop();
        currentPath = null;

        // Cria os nós com 25% de chance de ser obstáculo
        for (int l = 0; l < LINHAS; l++) {
            for (int c = 0; c < COLUNAS; c++) {
                boolean obstaculo = random.nextDouble() > 0.75;
                // Garante que a base (0,0) não seja obstáculo
                if (l == 0 && c == 0) obstaculo = false;
                gradeNos[l][c] = new No(l, c, obstaculo);
            }
        }
        
        atualizarArestas();
        
        noAtualDrone = gradeNos[0][0];
        droneX = noAtualDrone.coluna * TAMANHO_CELULA;
        droneY = noAtualDrone.linha * TAMANHO_CELULA;
        statusMessage = "Novo mapa gerado. Drone na base (0,0).";
        repaint();
    }

    public void aleatorizarClima() {
        if (timer != null) timer.stop();
        currentPath = null;

        for (int l = 0; l < LINHAS; l++) {
            for (int c = 0; c < COLUNAS; c++) {
                // Não bloqueia a posição atual do drone
                if (gradeNos[l][c] != noAtualDrone) {
                    gradeNos[l][c].ehObstaculo = random.nextDouble() > 0.70; // 30% de chance
                }
            }
        }
        atualizarArestas();
        
        // Alinha os pixels exatamente na grade caso estivesse no meio de uma animação
        droneX = noAtualDrone.coluna * TAMANHO_CELULA;
        droneY = noAtualDrone.linha * TAMANHO_CELULA;
        statusMessage = "O clima mudou! Novos obstáculos surgiram.";
        repaint();
    }

    private void atualizarArestas() {
        for (int l = 0; l < LINHAS; l++) {
            for (int c = 0; c < COLUNAS; c++) {
                No atual = gradeNos[l][c];
                atual.vizinhos.clear();
                if (atual.ehObstaculo) continue;

                if (l > 0 && !gradeNos[l - 1][c].ehObstaculo) atual.vizinhos.add(gradeNos[l - 1][c]);
                if (l < LINHAS - 1 && !gradeNos[l + 1][c].ehObstaculo) atual.vizinhos.add(gradeNos[l + 1][c]);
                if (c > 0 && !gradeNos[l][c - 1].ehObstaculo) atual.vizinhos.add(gradeNos[l][c - 1]);
                if (c < COLUNAS - 1 && !gradeNos[l][c + 1].ehObstaculo) atual.vizinhos.add(gradeNos[l][c + 1]);
            }
        }
    }

    // ==========================================
    // 5. NAVEGAÇÃO AUTÔNOMA (BFS)
    // ==========================================
    public void fazerEntregaAleatoria() {
        List<No> destinosValidos = new ArrayList<>();
        for (int l = 0; l < LINHAS; l++) {
            for (int c = 0; c < COLUNAS; c++) {
                if (!gradeNos[l][c].ehObstaculo && gradeNos[l][c] != noAtualDrone) {
                    destinosValidos.add(gradeNos[l][c]);
                }
            }
        }

        if (destinosValidos.isEmpty()) return;
        No destino = destinosValidos.get(random.nextInt(destinosValidos.size()));
        encontrarRota(noAtualDrone, destino);
        requestFocusInWindow(); // Mantém o foco para o teclado funcionar depois
    }

    private void encontrarRota(No inicio, No fim) {
        Queue<List<No>> fila = new LinkedList<>();
        Set<No> visitados = new HashSet<>();

        List<No> caminhoInicial = new ArrayList<>();
        caminhoInicial.add(inicio);
        fila.add(caminhoInicial);
        visitados.add(inicio);

        while (!fila.isEmpty()) {
            List<No> caminho = fila.poll();
            No atual = caminho.get(caminho.size() - 1);

            if (atual == fim) {
                currentPath = caminho;
                pathIndex = 0;
                statusMessage = "Piloto Automático: Rota encontrada para [" + fim.linha + "," + fim.coluna + "]";
                timer.start();
                repaint();
                return;
            }

            for (No vizinho : atual.vizinhos) {
                if (!visitados.contains(vizinho)) {
                    visitados.add(vizinho);
                    List<No> novoCaminho = new ArrayList<>(caminho);
                    novoCaminho.add(vizinho);
                    fila.add(novoCaminho);
                }
            }
        }
        statusMessage = "Erro: O destino [" + fim.linha + "," + fim.coluna + "] está isolado!";
        repaint();
    }

    private void animateDrone() {
        if (currentPath == null || pathIndex >= currentPath.size() - 1) {
            timer.stop();
            currentPath = null;
            statusMessage = "Entrega concluída! Modo livre ativado.";
            repaint();
            return;
        }

        No targetNode = currentPath.get(pathIndex + 1);
        double targetX = targetNode.coluna * TAMANHO_CELULA;
        double targetY = targetNode.linha * TAMANHO_CELULA;

        double dx = targetX - droneX;
        double dy = targetY - droneY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        double speed = 4.0; // Velocidade do drone

        if (distance <= speed) {
            droneX = targetX;
            droneY = targetY;
            noAtualDrone = targetNode; // Atualiza o nó oficial
            pathIndex++;
        } else {
            droneX += (dx / distance) * speed;
            droneY += (dy / distance) * speed;
        }
        repaint();
    }

    // ==========================================
    // 6. RENDERIZAÇÃO
    // ==========================================
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Fundo
        g2d.setColor(new Color(40, 44, 52));
        g2d.fillRect(0, 0, getWidth(), getHeight());

        // Desenha a Grade
        for (int l = 0; l < LINHAS; l++) {
            for (int c = 0; c < COLUNAS; c++) {
                int x = c * TAMANHO_CELULA;
                int y = l * TAMANHO_CELULA;

                if (gradeNos[l][c].ehObstaculo) {
                    g2d.setColor(new Color(46, 117, 89)); // Bloco
                    g2d.fillRect(x, y, TAMANHO_CELULA, TAMANHO_CELULA);
                } else {
                    g2d.setColor(new Color(60, 64, 72)); // Linhas guia
                    g2d.drawRect(x, y, TAMANHO_CELULA, TAMANHO_CELULA);
                }
            }
        }

        // Desenha a Linha da Rota Automática (opcional, fica visualmente legal)
        if (currentPath != null) {
            g2d.setColor(new Color(255, 215, 0, 150)); // Amarelo translúcido
            g2d.setStroke(new BasicStroke(4));
            for (int i = 0; i < currentPath.size() - 1; i++) {
                No n1 = currentPath.get(i);
                No n2 = currentPath.get(i + 1);
                g2d.drawLine(n1.coluna * TAMANHO_CELULA + TAMANHO_CELULA/2, 
                             n1.linha * TAMANHO_CELULA + TAMANHO_CELULA/2,
                             n2.coluna * TAMANHO_CELULA + TAMANHO_CELULA/2, 
                             n2.linha * TAMANHO_CELULA + TAMANHO_CELULA/2);
            }
            g2d.setStroke(new BasicStroke(1));
        }

        // Desenha o Drone (usando coordenadas decimais para animação fluida)
        g2d.setColor(new Color(0, 150, 255));
        g2d.fillOval((int) droneX + (TAMANHO_CELULA / 4), 
                     (int) droneY + (TAMANHO_CELULA / 4), 
                     TAMANHO_CELULA / 2, TAMANHO_CELULA / 2);

        // Minimapa e Status
        desenharMinimapa(g2d);
        
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        g2d.drawString("Status: " + statusMessage, 20, ALTURA_MUNDO - 15);
    }

    private void desenharMinimapa(Graphics2D g) {
        double escalaX = (double) MINI_LARGURA / LARGURA_MUNDO;
        double escalaY = (double) MINI_ALTURA / ALTURA_MUNDO;

        g.setColor(new Color(0, 0, 0, 210));
        g.fillRect(MINI_X, MINI_Y, MINI_LARGURA, MINI_ALTURA);
        g.setColor(Color.WHITE);
        g.drawRect(MINI_X, MINI_Y, MINI_LARGURA, MINI_ALTURA);

        for (int l = 0; l < LINHAS; l++) {
            for (int c = 0; c < COLUNAS; c++) {
                if (gradeNos[l][c].ehObstaculo) {
                    int miniObsX = MINI_X + (int) ((c * TAMANHO_CELULA) * escalaX);
                    int miniObsY = MINI_Y + (int) ((l * TAMANHO_CELULA) * escalaY);
                    int miniObsL = (int) (TAMANHO_CELULA * escalaX) + 1;
                    int miniObsA = (int) (TAMANHO_CELULA * escalaY) + 1;

                    g.setColor(new Color(200, 50, 50, 180));
                    g.fillRect(miniObsX, miniObsY, miniObsL, miniObsA);
                }
            }
        }

        // Drone no radar refletindo a animação (droneX e droneY atualizados no timer)
        int miniDroneX = MINI_X + (int) ((droneX + TAMANHO_CELULA/2) * escalaX);
        int miniDroneY = MINI_Y + (int) ((droneY + TAMANHO_CELULA/2) * escalaY);

        g.setColor(Color.CYAN);
        g.fillOval(miniDroneX - 4, miniDroneY - 4, 8, 8);
    }

    // ==========================================
    // 7. MAIN E INTEGRAÇÃO DOS BOTÕES
    // ==========================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Simulador Híbrido: Grade, Minimapa e Automação");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new BorderLayout());

            SimuladorDroneGrid canvas = new SimuladorDroneGrid();
            canvas.setPreferredSize(new Dimension(canvas.LARGURA_MUNDO, canvas.ALTURA_MUNDO));
            frame.add(canvas, BorderLayout.CENTER);

            // Painel de Botões inferior
            JPanel panel = new JPanel();
            panel.setBackground(Color.DARK_GRAY);
            
            JButton btnGerarMapa = new JButton("Gerar Novo Mapa");
            JButton btnClima = new JButton("Mudar Clima (Obstáculos)");
            JButton btnEntrega = new JButton("Fazer Entrega Aleatória");

            btnGerarMapa.addActionListener(e -> canvas.gerarMapaAleatorio());
            btnClima.addActionListener(e -> canvas.aleatorizarClima());
            btnEntrega.addActionListener(e -> canvas.fazerEntregaAleatoria());

            panel.add(btnGerarMapa);
            panel.add(btnClima);
            panel.add(btnEntrega);
            
            frame.add(panel, BorderLayout.SOUTH);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            
            // Foca no canvas para garantir que as setas do teclado funcionem de primeira
            canvas.requestFocusInWindow();
        });
    }
}