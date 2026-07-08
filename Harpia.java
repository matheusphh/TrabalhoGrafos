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

    private final int LINHAS = 16;
    private final int COLUNAS = 16;
    private final int TAMANHO_CELULA = 50;

    private final int LARGURA_MUNDO = COLUNAS * TAMANHO_CELULA;
    private final int ALTURA_MUNDO = LINHAS * TAMANHO_CELULA;

    private final int MINI_LARGURA = 200;
    private final int MINI_ALTURA = 200;
    private final int MINI_X = 20;
    private final int MINI_Y = 20;

    enum TipoLocal {
        VAZIO, OBSTACULO, BASE, FARMACIA, HOSPITAL, RESIDENCIA
    }

    class No {
        int linha, coluna;
        TipoLocal tipo;
        List<No> vizinhos = new ArrayList<>();
        boolean entregue = false;

        No(int linha, int coluna, TipoLocal tipo) {
            this.linha = linha;
            this.coluna = coluna;
            this.tipo = tipo;
        }
    }

    private No[][] gradeNos = new No[LINHAS][COLUNAS];
    private No noAtualDrone;
    private No baseNo;
    
    private double droneX, droneY;
    private List<No> currentPath = null;
    private int pathIndex = 0;
    private javax.swing.Timer timer;
    private String statusMessage = "Modo Livre: Use as setas ou clique em uma ação.";
    private Random random = new Random();

    private List<No> entregasPendentes = new ArrayList<>();
    private No destinoAtual = null;
    private boolean retornandoParaBase = false;

    public SimuladorDroneGrid() {
        setFocusable(true);
        
        gerarMapaAleatorio();
        timer = new javax.swing.Timer(16, e -> animateDrone());

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (currentPath != null || !entregasPendentes.isEmpty() || retornandoParaBase) {
                    timer.stop();
                    currentPath = null;
                    entregasPendentes.clear();
                    destinoAtual = null;
                    retornandoParaBase = false;
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

                if (proximoNo != null && noAtualDrone.vizinhos.contains(proximoNo)) {
                    noAtualDrone = proximoNo;
                    droneX = noAtualDrone.coluna * TAMANHO_CELULA;
                    droneY = noAtualDrone.linha * TAMANHO_CELULA;
                }
                repaint();
            }
        });
    }

    public void gerarMapaAleatorio() {
        if (timer != null) timer.stop();
        currentPath = null;
        entregasPendentes.clear();
        destinoAtual = null;
        retornandoParaBase = false;

        for (int l = 0; l < LINHAS; l++) {
            for (int c = 0; c < COLUNAS; c++) {
                boolean obstaculo = random.nextDouble() > 0.75;
                gradeNos[l][c] = new No(l, c, obstaculo ? TipoLocal.OBSTACULO : TipoLocal.VAZIO);
            }
        }
        
        baseNo = gradeNos[0][0];
        baseNo.tipo = TipoLocal.BASE;
        baseNo.entregue = false;

        espalharPontosDeInteresse(TipoLocal.FARMACIA, 3);
        espalharPontosDeInteresse(TipoLocal.HOSPITAL, 2);
        espalharPontosDeInteresse(TipoLocal.RESIDENCIA, 6);

        atualizarArestas();
        
        noAtualDrone = baseNo;
        droneX = noAtualDrone.coluna * TAMANHO_CELULA;
        droneY = noAtualDrone.linha * TAMANHO_CELULA;
        statusMessage = "Novo mapa gerado com hospitais, farmácias e residências.";
        repaint();
    }

    private void espalharPontosDeInteresse(TipoLocal tipo, int quantidade) {
        int adicionados = 0;
        while (adicionados < quantidade) {
            int l = random.nextInt(LINHAS);
            int c = random.nextInt(COLUNAS);
            
            if (gradeNos[l][c].tipo == TipoLocal.VAZIO) {
                gradeNos[l][c].tipo = tipo;
                gradeNos[l][c].entregue = false;
                adicionados++;
            }
        }
    }

    public void aleatorizarClima() {
        if (timer != null && currentPath == null) timer.stop();

        for (int l = 0; l < LINHAS; l++) {
            for (int c = 0; c < COLUNAS; c++) {
                No atual = gradeNos[l][c];
                if (atual.tipo == TipoLocal.VAZIO || atual.tipo == TipoLocal.OBSTACULO) {
                    if (atual != noAtualDrone) {
                        atual.tipo = random.nextDouble() > 0.70 ? TipoLocal.OBSTACULO : TipoLocal.VAZIO;
                    }
                }
            }
        }
        atualizarArestas();
        
        if (currentPath == null) {
            droneX = noAtualDrone.coluna * TAMANHO_CELULA;
            droneY = noAtualDrone.linha * TAMANHO_CELULA;
            statusMessage = "O clima mudou! Novos obstáculos surgiram.";
        }
        repaint();
    }

    private void atualizarArestas() {
        for (int l = 0; l < LINHAS; l++) {
            for (int c = 0; c < COLUNAS; c++) {
                No atual = gradeNos[l][c];
                atual.vizinhos.clear();
                if (atual.tipo == TipoLocal.OBSTACULO) continue;

                if (l > 0 && gradeNos[l - 1][c].tipo != TipoLocal.OBSTACULO) atual.vizinhos.add(gradeNos[l - 1][c]);
                if (l < LINHAS - 1 && gradeNos[l + 1][c].tipo != TipoLocal.OBSTACULO) atual.vizinhos.add(gradeNos[l + 1][c]);
                if (c > 0 && gradeNos[l][c - 1].tipo != TipoLocal.OBSTACULO) atual.vizinhos.add(gradeNos[l][c - 1]);
                if (c < COLUNAS - 1 && gradeNos[l][c + 1].tipo != TipoLocal.OBSTACULO) atual.vizinhos.add(gradeNos[l][c + 1]);
            }
        }
    }

    public void fazerTodasAsEntregas() {
        entregasPendentes.clear();
        retornandoParaBase = false;
        
        for (int l = 0; l < LINHAS; l++) {
            for (int c = 0; c < COLUNAS; c++) {
                TipoLocal tipo = gradeNos[l][c].tipo;
                if ((tipo == TipoLocal.FARMACIA || tipo == TipoLocal.HOSPITAL || tipo == TipoLocal.RESIDENCIA) && !gradeNos[l][c].entregue) {
                    entregasPendentes.add(gradeNos[l][c]);
                }
            }
        }

        if (entregasPendentes.isEmpty() && noAtualDrone == baseNo) {
            statusMessage = "Todos os locais já receberam entregas e o drone está na base!";
            repaint();
            return;
        }

        iniciarProximaEntrega();
        requestFocusInWindow();
    }

    private void iniciarProximaEntrega() {
        if (entregasPendentes.isEmpty()) {
            if (noAtualDrone != baseNo && !retornandoParaBase) {
                retornandoParaBase = true;
                destinoAtual = baseNo;
                encontrarRota(noAtualDrone, destinoAtual);
            } else {
                currentPath = null;
                destinoAtual = null;
                retornandoParaBase = false;
                statusMessage = "Todas as entregas concluídas! O drone retornou à Base.";
                timer.stop();
                repaint();
            }
            return;
        }

        No destinoMaisProximo = null;
        double menorDistancia = Double.MAX_VALUE;

        for (No pendente : entregasPendentes) {
            double dist = Math.hypot(pendente.linha - noAtualDrone.linha, pendente.coluna - noAtualDrone.coluna);
            if (dist < menorDistancia) {
                menorDistancia = dist;
                destinoMaisProximo = pendente;
            }
        }

        if (destinoMaisProximo != null) {
            destinoAtual = destinoMaisProximo;
            encontrarRota(noAtualDrone, destinoAtual);
        }
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
                
                if (retornandoParaBase) {
                    statusMessage = "Entregas finalizadas. Retornando para a Base...";
                } else {
                    statusMessage = "Faltam " + entregasPendentes.size() + " locais. Rumo à " + fim.tipo + ".";
                }
                
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
        
        if (!retornandoParaBase) {
            entregasPendentes.remove(fim);
        }
        iniciarProximaEntrega();
    }

    private void animateDrone() {
        if (currentPath == null || pathIndex >= currentPath.size() - 1) {
            if (destinoAtual != null && !retornandoParaBase) {
                destinoAtual.entregue = true;
                entregasPendentes.remove(destinoAtual);
            }
            iniciarProximaEntrega();
            return;
        }

        No targetNode = currentPath.get(pathIndex + 1);
        double targetX = targetNode.coluna * TAMANHO_CELULA;
        double targetY = targetNode.linha * TAMANHO_CELULA;

        double dx = targetX - droneX;
        double dy = targetY - droneY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        double speed = 4.0;

        if (distance <= speed) {
            droneX = targetX;
            droneY = targetY;
            noAtualDrone = targetNode;
            pathIndex++;
        } else {
            droneX += (dx / distance) * speed;
            droneY += (dy / distance) * speed;
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.setColor(new Color(40, 44, 52));
        g2d.fillRect(0, 0, getWidth(), getHeight());

        for (int l = 0; l < LINHAS; l++) {
            for (int c = 0; c < COLUNAS; c++) {
                int x = c * TAMANHO_CELULA;
                int y = l * TAMANHO_CELULA;
                No no = gradeNos[l][c];

                if (no.tipo == TipoLocal.OBSTACULO) {
                    g2d.setColor(new Color(46, 117, 89));
                    g2d.fillRect(x, y, TAMANHO_CELULA, TAMANHO_CELULA);
                } else if (no.entregue) {
                    g2d.setColor(new Color(105, 105, 105)); 
                    g2d.fillRect(x, y, TAMANHO_CELULA, TAMANHO_CELULA);
                    desenharLetra(g2d, "✓", x, y, Color.GREEN);
                } else if (no.tipo == TipoLocal.BASE) {
                    g2d.setColor(new Color(100, 149, 237));
                    g2d.fillRect(x, y, TAMANHO_CELULA, TAMANHO_CELULA);
                    desenharLetra(g2d, "B", x, y, Color.WHITE);
                } else if (no.tipo == TipoLocal.FARMACIA) {
                    g2d.setColor(new Color(60, 179, 113));
                    g2d.fillRect(x, y, TAMANHO_CELULA, TAMANHO_CELULA);
                    desenharLetra(g2d, "F", x, y, Color.WHITE);
                } else if (no.tipo == TipoLocal.HOSPITAL) {
                    g2d.setColor(new Color(220, 20, 60));
                    g2d.fillRect(x, y, TAMANHO_CELULA, TAMANHO_CELULA);
                    desenharLetra(g2d, "H", x, y, Color.WHITE);
                } else if (no.tipo == TipoLocal.RESIDENCIA) {
                    g2d.setColor(new Color(218, 165, 32));
                    g2d.fillRect(x, y, TAMANHO_CELULA, TAMANHO_CELULA);
                    desenharLetra(g2d, "R", x, y, Color.BLACK);
                } else {
                    g2d.setColor(new Color(60, 64, 72));
                    g2d.drawRect(x, y, TAMANHO_CELULA, TAMANHO_CELULA);
                }
            }
        }

        if (currentPath != null) {
            g2d.setColor(new Color(255, 215, 0, 150));
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

        g2d.setColor(new Color(0, 150, 255));
        g2d.fillOval((int) droneX + (TAMANHO_CELULA / 4), 
                     (int) droneY + (TAMANHO_CELULA / 4), 
                     TAMANHO_CELULA / 2, TAMANHO_CELULA / 2);

        desenharMinimapa(g2d);
        
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        g2d.drawString("Status: " + statusMessage, 20, ALTURA_MUNDO - 15);
    }

    private void desenharLetra(Graphics2D g2d, String letra, int x, int y, Color cor) {
        g2d.setColor(cor);
        g2d.setFont(new Font("Arial", Font.BOLD, 20));
        FontMetrics fm = g2d.getFontMetrics();
        int textX = x + (TAMANHO_CELULA - fm.stringWidth(letra)) / 2;
        int textY = y + ((TAMANHO_CELULA - fm.getHeight()) / 2) + fm.getAscent();
        g2d.drawString(letra, textX, textY);
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
                No no = gradeNos[l][c];
                
                int miniX = MINI_X + (int) ((c * TAMANHO_CELULA) * escalaX);
                int miniY = MINI_Y + (int) ((l * TAMANHO_CELULA) * escalaY);
                int miniL = (int) (TAMANHO_CELULA * escalaX) + 1;
                int miniA = (int) (TAMANHO_CELULA * escalaY) + 1;

                if (no.tipo == TipoLocal.OBSTACULO) {
                    g.setColor(new Color(200, 50, 50, 180));
                    g.fillRect(miniX, miniY, miniL, miniA);
                } else if (no.entregue) {
                    g.setColor(Color.DARK_GRAY);
                    g.fillRect(miniX, miniY, miniL, miniA);
                } else if (no.tipo == TipoLocal.BASE) {
                    g.setColor(Color.BLUE);
                    g.fillRect(miniX, miniY, miniL, miniA);
                } else if (no.tipo == TipoLocal.FARMACIA) {
                    g.setColor(Color.GREEN);
                    g.fillRect(miniX, miniY, miniL, miniA);
                } else if (no.tipo == TipoLocal.HOSPITAL) {
                    g.setColor(Color.RED);
                    g.fillRect(miniX, miniY, miniL, miniA);
                } else if (no.tipo == TipoLocal.RESIDENCIA) {
                    g.setColor(Color.YELLOW);
                    g.fillRect(miniX, miniY, miniL, miniA);
                }
            }
        }

        int miniDroneX = MINI_X + (int) ((droneX + TAMANHO_CELULA/2) * escalaX);
        int miniDroneY = MINI_Y + (int) ((droneY + TAMANHO_CELULA/2) * escalaY);

        g.setColor(Color.CYAN);
        g.fillOval(miniDroneX - 4, miniDroneY - 4, 8, 8);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Simulador de Entregas: Rota Completa com Retorno");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new BorderLayout());

            SimuladorDroneGrid canvas = new SimuladorDroneGrid();
            canvas.setPreferredSize(new Dimension(canvas.LARGURA_MUNDO, canvas.ALTURA_MUNDO));
            frame.add(canvas, BorderLayout.CENTER);

            JPanel panel = new JPanel();
            panel.setBackground(Color.DARK_GRAY);
            
            JButton btnGerarMapa = new JButton("Gerar Novo Mapa");
            JButton btnClima = new JButton("Mudar Clima (Obstáculos)");
            JButton btnEntrega = new JButton("Fazer Todas as Entregas");

            btnGerarMapa.addActionListener(e -> canvas.gerarMapaAleatorio());
            btnClima.addActionListener(e -> canvas.aleatorizarClima());
            btnEntrega.addActionListener(e -> canvas.fazerTodasAsEntregas());

            panel.add(btnGerarMapa);
            panel.add(btnClima);
            panel.add(btnEntrega);
            
            frame.add(panel, BorderLayout.SOUTH);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            
            canvas.requestFocusInWindow();
        });
    }
}