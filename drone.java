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

    public SimuladorDroneGUI() {
        // 1. PRIMEIRO: Inicializamos o Timer
        timer = new javax.swing.Timer(16, e -> animateDrone());

        // 2. DEPOIS: Geramos o mapa (agora o timer.stop() não vai quebrar)
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
            // Conecta com um nó já existente no grafo
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
        // Percorre todas as arestas e muda aleatoriamente seu estado de bloqueio
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
    // 4. ALGORITMO E NAVEGAÇÃO
    // ==========================================
    public void fazerEntregaAleatoria() {
        if (allLocations.size() <= 1) return;
        
        // Escolhe um destino aleatório diferente da base
        Location destino = allLocations.get(1 + random.nextInt(allLocations.size() - 1));
        findAndStartRoute(base, destino);
    }

    private void findAndStartRoute(Location start, Location end) {
        Queue<List<Location>> queue = new LinkedList<>();
        Set<Location> visited = new HashSet<>();

        List<Location> initialPath = new ArrayList<>();
        initialPath.add(start);
        queue.add(initialPath);

        while (!queue.isEmpty()) {
            List<Location> path = queue.poll();
            Location current = path.get(path.size() - 1);

            if (current.equals(end)) {
                currentPath = path;
                pathIndex = 0;
                droneX = base.x;
                droneY = base.y;
                statusMessage = "Enviando drone para: " + end.name;
                timer.start();
                repaint();
                return;
            }

            visited.add(current);

            for (Route edge : graph.get(current)) {
                if (!visited.contains(edge.destination) && !edge.isBlocked) {
                    List<Location> newPath = new ArrayList<>(path);
                    newPath.add(edge.destination);
                    queue.add(newPath);
                }
            }
        }
        statusMessage = "Erro: Rota para " + end.name + " está isolada pelo clima!";
        repaint();
    }

    private void resetDrone() {
        timer.stop();
        currentPath = null;
        droneX = base.x;
        droneY = base.y;
    }

    private void animateDrone() {
        if (currentPath == null || pathIndex >= currentPath.size() - 1) {
            timer.stop();
            statusMessage = "Entrega concluída!";
            repaint();
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
                // Evita desenhar a mesma linha duas vezes
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
            JFrame frame = new JFrame("Simulador de Grafos: Drones");
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
            JButton btnEntrega = new JButton("Fazer Entrega Aleatória");

            btnGerarMapa.addActionListener(e -> canvas.gerarMapaAleatorio());
            btnClima.addActionListener(e -> canvas.aleatorizarClima());
            btnEntrega.addActionListener(e -> canvas.fazerEntregaAleatoria());

            panel.add(btnGerarMapa);
            panel.add(btnClima);
            panel.add(btnEntrega);
            
            frame.add(panel, BorderLayout.SOUTH);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}