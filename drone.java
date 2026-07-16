import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class SimuladorDroneGUI extends JPanel {

    // ==========================================
    // 1. MODELAGEM
    // ==========================================
    static class Location {
        String name;
        int x, y;

        public Location(String name, int x, int y) {
            this.name = name;
            this.x = x;
            this.y = y;
        }
    }

    static class Route {
        Location destination;
        boolean isBlocked;

        public Route(Location destination, boolean isBlocked) {
            this.destination = destination;
            this.isBlocked = isBlocked;
        }
    }

    // ==========================================
    // 2. ESTADO DO SIMULADOR
    // ==========================================
    private Map<Location, List<Route>> graph = new HashMap<>();
    private Location base;
    private List<Location> allLocations = new ArrayList<>();
    private Random random = new Random();
    
    private double droneX, droneY;
    private List<Location> currentPath = null;
    private int pathIndex = 0;
    private javax.swing.Timer timer;
    private String statusMessage = "Clique em 'Gerar Novo Mapa' para começar.";

    // Variáveis de controle para entregas múltiplas
    private List<Location> entregasPendentes = new ArrayList<>();
    private boolean modoEntregasMultiplas = false;

    public SimuladorDroneGUI() {
        // Inicializa o Timer de animação
        timer = new javax.swing.Timer(16, e -> animateDrone());
        // Gera o mapa inicial
        gerarMapaAleatorio();
    }

    // ==========================================
    // 3. GERADOR DE GRAFO ALEATÓRIO
    // ==========================================
    public void gerarMapaAleatorio() {
        graph.clear();
        allLocations.clear();
        timer.stop();

        int width = 750;
        int height = 450;

        // 1. Cria a Base
        base = new Location("Base", 50, height / 2);
        allLocations.add(base);
        graph.put(base, new ArrayList<>());

        // 2. Cria Nós Aleatórios (entre 5 e 10 nós)
        int numNodes = 5 + random.nextInt(6);
        for (int i = 1; i < numNodes; i++) {
            Location loc = new Location("Local " + i, 150 + random.nextInt(width - 200), 50 + random.nextInt(height - 100));
            allLocations.add(loc);
            graph.put(loc, new ArrayList<>());
        }

        // 3. Conecta os nós garantindo que não haja "ilhas" isoladas
        for (int i = 1; i < numNodes; i++) {
            Location current = allLocations.get(i);
            Location connectTo = allLocations.get(random.nextInt(i));
            boolean hasObstacle = random.nextDouble() > 0.8; // 20% de chance de bloqueio inicial
            addBidirectionalRoute(current, connectTo, hasObstacle);
        }

        // 4. Adiciona algumas arestas extras para criar caminhos alternativos
        for (int i = 0; i < numNodes; i++) {
            Location a = allLocations.get(random.nextInt(numNodes));
            Location b = allLocations.get(random.nextInt(numNodes));
            if (a != b && !routeExists(a, b)) {
                boolean hasObstacle = random.nextDouble() > 0.8;
                addBidirectionalRoute(a, b, hasObstacle);
            }
        }

        resetDrone();
        statusMessage = "Novo mapa com " + numNodes + " vértices gerado!";
        repaint();
    }

    public void aleatorizarClima() {
        for (List<Route> routes : graph.values()) {
            for (Route r : routes) {
                // Sincroniza a ida e volta (grafo não direcionado)
                boolean novoEstado = random.nextDouble() > 0.7; // 30% de chance de estar bloqueado
                r.isBlocked = novoEstado;
                
                // Atualiza a aresta de volta
                for (Route backRoute : graph.get(r.destination)) {
                    if (backRoute.destination == findLocationByRoute(routes)) {
                        backRoute.isBlocked = novoEstado;
                    }
                }
            }
        }
        statusMessage = "Clima alterado! Rotas bloqueadas atualizadas.";
        resetDrone();
        repaint();
    }

    private void addBidirectionalRoute(Location a, Location b, boolean isBlocked) {
        graph.get(a).add(new Route(b, isBlocked));
        graph.get(b).add(new Route(a, isBlocked));
    }

    private boolean routeExists(Location a, Location b) {
        for (Route r : graph.get(a)) {
            if (r.destination == b) return true;
        }
        return false;
    }

    private Location findLocationByRoute(List<Route> routes) {
        for (Map.Entry<Location, List<Route>> entry : graph.entrySet()) {
            if (entry.getValue() == routes) return entry.getKey();
        }
        return null;
    }

    // ==========================================
    // 4. ALGORITMO A* E NAVEGAÇÃO EM FILA
    // ==========================================
    
    // Classe auxiliar para o algoritmo A*
    private class AStarNode implements Comparable<AStarNode> {
        Location location;
        List<Location> path;
        double g; // Custo do caminho até agora (distância percorrida)
        double f; // f = g + h (custo total estimado)

        public AStarNode(Location location, List<Location> path, double g, double f) {
            this.location = location;
            this.path = path;
            this.g = g;
            this.f = f;
        }

        @Override
        public int compareTo(AStarNode other) {
            return Double.compare(this.f, other.f);
        }
    }

    public void fazerTodasEntregas() {
        if (allLocations.size() <= 1) return;
        
        // Copia todos os locais (menos a base) para a fila
        entregasPendentes = new ArrayList<>(allLocations);
        entregasPendentes.remove(base);
        modoEntregasMultiplas = true;
        
        // O drone parte da base para a primeira entrega
        iniciarProximaEntrega(base);
    }

    private void iniciarProximaEntrega(Location pontoAtual) {
        if (entregasPendentes.isEmpty()) {
            statusMessage = "Missão cumprida: Todas as entregas acessíveis foram feitas!";
            modoEntregasMultiplas = false;
            repaint();
            return;
        }

        // Pega o próximo destino da fila
        Location proximoDestino = entregasPendentes.remove(0);
        
        // Tenta achar a rota usando o A*
        boolean sucesso = findAndStartRoute(pontoAtual, proximoDestino);
        
        // Se falhou (isolado pelos obstáculos), pula para o próximo da fila
        if (!sucesso) {
            iniciarProximaEntrega(pontoAtual);
        }
    }

    private boolean findAndStartRoute(Location start, Location end) {
        PriorityQueue<AStarNode> queue = new PriorityQueue<>();
        Map<Location, Double> minCost = new HashMap<>(); 

        List<Location> initialPath = new ArrayList<>();
        initialPath.add(start);
        
        queue.add(new AStarNode(start, initialPath, 0.0, calcularDistancia(start, end)));
        minCost.put(start, 0.0);

        while (!queue.isEmpty()) {
            AStarNode current = queue.poll();

            // Se chegou ao destino
            if (current.location.equals(end)) {
                currentPath = current.path;
                pathIndex = 0;
                
                // O drone começa a animação a partir do ponto inicial desta rota específica
                droneX = start.x;
                droneY = start.y;
                
                statusMessage = "Indo para: " + end.name + " (" + entregasPendentes.size() + " restantes)";
                timer.start();
                repaint();
                return true; // Rota encontrada com sucesso
            }

            for (Route edge : graph.get(current.location)) {
                if (!edge.isBlocked) {
                    Location neighbor = edge.destination;
                    double stepCost = calcularDistancia(current.location, neighbor);
                    double newG = current.g + stepCost;

                    if (!minCost.containsKey(neighbor) || newG < minCost.get(neighbor)) {
                        minCost.put(neighbor, newG);
                        
                        List<Location> newPath = new ArrayList<>(current.path);
                        newPath.add(neighbor);
                        
                        double h = calcularDistancia(neighbor, end);
                        queue.add(new AStarNode(neighbor, newPath, newG, newG + h));
                    }
                }
            }
        }
        
        // Não encontrou rota (bloqueado pelo clima)
        return false; 
    }

    private double calcularDistancia(Location a, Location b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private void resetDrone() {
        timer.stop();
        currentPath = null;
        modoEntregasMultiplas = false;
        entregasPendentes.clear();
        droneX = base.x;
        droneY = base.y;
    }

    private void animateDrone() {
        if (currentPath == null || pathIndex >= currentPath.size() - 1) {
            timer.stop();
            
            // Se estamos no modo de múltiplas entregas, engatilha a próxima
            if (modoEntregasMultiplas && currentPath != null) {
                Location localAlcancado = currentPath.get(currentPath.size() - 1);
                iniciarProximaEntrega(localAlcancado);
            } else {
                statusMessage = "Navegação parada.";
                repaint();
            }
            return;
        }

        Location target = currentPath.get(pathIndex + 1);
        double dx = target.x - droneX;
        double dy = target.y - droneY;
        double distance = Math.sqrt(dx * dx + dy * dy);

        double speed = 5.0; 

        if (distance <= speed) {
            droneX = target.x;
            droneY = target.y;
            pathIndex++;
        } else {
            droneX += (dx / distance) * speed;
            droneY += (dy / distance) * speed;
        }
        repaint();
    }

    // ==========================================
    // 5. RENDERIZAÇÃO GRÁFICA
    // ==========================================
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Fundo
        g2d.setColor(new Color(240, 248, 255)); // Azul clarinho
        g2d.fillRect(0, 0, getWidth(), getHeight());

        // Desenha Arestas
        Set<String> drawnEdges = new HashSet<>();
        for (Location loc : graph.keySet()) {
            for (Route route : graph.get(loc)) {
                String edgeId1 = loc.name + "-" + route.destination.name;
                String edgeId2 = route.destination.name + "-" + loc.name;
                if (drawnEdges.contains(edgeId1) || drawnEdges.contains(edgeId2)) continue;
                drawnEdges.add(edgeId1);

                if (route.isBlocked) {
                    g2d.setColor(Color.RED);
                    float[] dash = {10.0f};
                    g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, dash, 0.0f));
                } else {
                    g2d.setColor(Color.LIGHT_GRAY);
                    g2d.setStroke(new BasicStroke(2));
                }
                g2d.drawLine(loc.x, loc.y, route.destination.x, route.destination.y);
            }
        }

        // Desenha Vértices
        for (Location loc : graph.keySet()) {
            if (loc == base) {
                g2d.setColor(new Color(34, 139, 34)); // Verde para base
                g2d.fillRect(loc.x - 12, loc.y - 12, 24, 24); // Base quadrada
            } else {
                g2d.setColor(Color.DARK_GRAY);
                g2d.fillOval(loc.x - 10, loc.y - 10, 20, 20);
            }
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.BOLD, 12));
            g2d.drawString(loc.name, loc.x - 15, loc.y - 15);
        }

        // Desenha Drone
        if (currentPath != null || droneX == base.x) {
            g2d.setColor(Color.BLUE);
            g2d.fillOval((int) droneX - 8, (int) droneY - 8, 16, 16);
        }

        // Desenha Status
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        g2d.drawString("Status: " + statusMessage, 20, 30);
    }

    // ==========================================
    // 6. INICIALIZAÇÃO
    // ==========================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Simulador de Grafos: Drones (A-Star)");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(800, 600);
            frame.setLayout(new BorderLayout());

            SimuladorDroneGUI canvas = new SimuladorDroneGUI();
            frame.add(canvas, BorderLayout.CENTER);

            // Interface de Botões
            JPanel panel = new JPanel();
            panel.setBackground(Color.WHITE);
            
            JButton btnGerarMapa = new JButton("Gerar Novo Mapa");
            JButton btnClima = new JButton("Mudar Clima (Obstáculos)");
            JButton btnTodasEntregas = new JButton("Fazer Todas as Entregas");

            btnGerarMapa.addActionListener(e -> canvas.gerarMapaAleatorio());
            btnClima.addActionListener(e -> canvas.aleatorizarClima());
            btnTodasEntregas.addActionListener(e -> canvas.fazerTodasEntregas());

            panel.add(btnGerarMapa);
            panel.add(btnClima);
            panel.add(btnTodasEntregas);
            
            frame.add(panel, BorderLayout.SOUTH);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}