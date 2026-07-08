package minimap2;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

public class MiniMap2 extends JPanel {

    // 1. CONFIGURAÇÃO DA GRADE DO GRAFO
    private final int LINHAS = 16;
    private final int COLUNAS = 16;
    private final int TAMANHO_CELULA = 50; // Cada nó da grade tem 50x50 pixels no mundo real

    // Dimensões do mundo calculadas a partir da grade (16 * 50 = 800)
    private final int LARGURA_MUNDO = COLUNAS * TAMANHO_CELULA;
    private final int ALTURA_MUNDO = LINHAS * TAMANHO_CELULA;

    // 2. DIMENSÕES DO MINIMAPA
    private final int MINI_LARGURA = 200;
    private final int MINI_ALTURA = 200;
    private final int MINI_X = 20;
    private final int MINI_Y = 20;

    // 3. ESTRUTURA DO GRAFO (Lista de Adjacência)
    private No[][] gradeNos = new No[LINHAS][COLUNAS];

    // 4. POSIÇÃO DO DRONE (Agora ele aponta para um NÓ do grafo)
    private No noAtualDrone;

    // Classe que representa um Nó (Vértice) do Grafo
    class No {
        int linha, coluna;
        boolean ehObstaculo;
        List<No> vizinhos = new ArrayList<>(); // Arestas que conectam aos nós vizinhos

        No(int linha, int coluna, boolean ehObstaculo) {
            this.linha = linha;
            this.coluna = coluna;
            this.ehObstaculo = ehObstaculo;
        }
    }

    public MiniMap2() {
        setFocusable(true);
        inicializarGrafo();

        // Posiciona o drone no primeiro nó livre encontrado (geralmente 0,0)
        noAtualDrone = gradeNos[0][0];

        // Controle do drone pelas conexões do grafo
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                No proximoNo = null;

                // Tenta encontrar o vizinho na direção desejada
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

                // VALIDAÇÃO POR GRAFO: O drone só se move se o nó destino for um vizinho conectado (aresta válida)
                if (proximoNo != null && noAtualDrone.vizinhos.contains(proximoNo)) {
                    noAtualDrone = proximoNo;
                }

                repaint();
            }
        });
    }

    private void inicializarGrafo() {
        // Passo 1: Criar os Vértices (Nós) e definir onde estão os obstáculos
        for (int l = 0; l < LINHAS; l++) {
            for (int c = 0; c < COLUNAS; c++) {
                // Criando alguns obstáculos fixos na grade para teste
                boolean obstaculo = (l == 0 && c > 2 && c < 16) ||
                                    (l == 4 && c >= 0 && c < 10) ||
                                    (l == 4 && c > 12 && c < 16) ||
                                    (l == 8 && c > 4 && c < 12) ||
                                    (l == 12 && c > 7 && c < 14) ||
                                    (l == 16 && c >= 0 && c < 10) || 
                                    (c == 7 && l > 7)|| 
                                    (c == 0 && l > 6)||
                                    (l == 2 && c > 10 && c < 12)||
                                    (l == 6 && c > 10 && c < 12)|| 
                                    (l == 6 && c > 2 && c < 6)||
                                    (l == 10 && c > 14 && c < 16)||
                                    (l == 14 && c > 2 && c < 4)||
                                    (l == 12 && c > 2 && c < 4);
                                     
                
                gradeNos[l][c] = new No(l, c, obstaculo);
            }
        }

        // Passo 2: Criar as Arestas (Conexões) entre nós vizinhos que NÃO são obstáculos
        for (int l = 0; l < LINHAS; l++) {
            for (int c = 0; c < COLUNAS; c++) {
                No atual = gradeNos[l][c];
                if (atual.ehObstaculo) continue; // Obstáculos não possuem caminhos de saída

                // Checa vizinho de cima
                if (l > 0 && !gradeNos[l - 1][c].ehObstaculo) atual.vizinhos.add(gradeNos[l - 1][c]);
                // Checa vizinho de baixo
                if (l < LINHAS - 1 && !gradeNos[l + 1][c].ehObstaculo) atual.vizinhos.add(gradeNos[l + 1][c]);
                // Checa vizinho da esquerda
                if (c > 0 && !gradeNos[l][c - 1].ehObstaculo) atual.vizinhos.add(gradeNos[l][c - 1]);
                // Checa vizinho da direita
                if (c < COLUNAS - 1 && !gradeNos[l][c + 1].ehObstaculo) atual.vizinhos.add(gradeNos[l][c + 1]);
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Desenha o cenário principal baseado na grade do grafo
        desenharCenarioPrincipal(g2d);

        // Desenha o minimapa
        desenharMinimapa(g2d);
    }

    private void desenharCenarioPrincipal(Graphics2D g) {
        // Fundo
        g.setColor(new Color(40, 44, 52));
        g.fillRect(0, 0, getWidth(), getHeight());

        // Desenha a grade (Grafo) na tela principal
        for (int l = 0; l < LINHAS; l++) {
            for (int c = 0; c < COLUNAS; c++) {
                No no = gradeNos[l][c];
                int x = c * TAMANHO_CELULA;
                int y = l * TAMANHO_CELULA;

                if (no.ehObstaculo) {
                    g.setColor(new Color(46, 117, 89)); // Bloco obstáculo
                    g.fillRect(x, y, TAMANHO_CELULA, TAMANHO_CELULA);
                } else {
                    g.setColor(new Color(60, 64, 72)); // Linhas de guia do grafo
                    g.drawRect(x, y, TAMANHO_CELULA, TAMANHO_CELULA);
                }
            }
        }

        // Desenha o Drone na posição do seu Nó atual
        int droneX = noAtualDrone.coluna * TAMANHO_CELULA + (TAMANHO_CELULA / 4);
        int droneY = noAtualDrone.linha * TAMANHO_CELULA + (TAMANHO_CELULA / 4);
        g.setColor(new Color(0, 150, 255));
        g.fillOval(droneX, droneY, TAMANHO_CELULA / 2, TAMANHO_CELULA / 2);
    }

    private void desenharMinimapa(Graphics2D g) {
        double escalaX = (double) MINI_LARGURA / LARGURA_MUNDO;
        double escalaY = (double) MINI_ALTURA / ALTURA_MUNDO;

        // Fundo do radar
        g.setColor(new Color(0, 0, 0, 210));
        g.fillRect(MINI_X, MINI_Y, MINI_LARGURA, MINI_ALTURA);
        g.setColor(Color.WHITE);
        g.drawRect(MINI_X, MINI_Y, MINI_LARGURA, MINI_ALTURA);

        // Desenha a representação reduzida do grafo no minimapa
        for (int l = 0; l < LINHAS; l++) {
            for (int c = 0; c < COLUNAS; c++) {
                No no = gradeNos[l][c];
                
                if (no.ehObstaculo) {
                    int miniObsX = MINI_X + (int) ((c * TAMANHO_CELULA) * escalaX);
                    int miniObsY = MINI_Y + (int) ((l * TAMANHO_CELULA) * escalaY);
                    int miniObsL = (int) (TAMANHO_CELULA * escalaX) + 1;
                    int miniObsA = (int) (TAMANHO_CELULA * escalaY) + 1;

                    g.setColor(new Color(200, 50, 50, 180));
                    g.fillRect(miniObsX, miniObsY, miniObsL, miniObsA);
                }
            }
        }

        // Posição do drone convertida para o radar
        int miniDroneX = MINI_X + (int) ((noAtualDrone.coluna * TAMANHO_CELULA + TAMANHO_CELULA/2) * escalaX);
        int miniDroneY = MINI_Y + (int) ((noAtualDrone.linha * TAMANHO_CELULA + TAMANHO_CELULA/2) * escalaY);

        g.setColor(Color.CYAN);
        g.fillOval(miniDroneX - 4, miniDroneY - 4, 8, 8);
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Drone Navegando por Grafos");
        MiniMap2 jogo = new MiniMap2();
        frame.add(jogo);
        frame.setSize(835, 840); // Ajustado para o tamanho exato da nossa grade + bordas
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}