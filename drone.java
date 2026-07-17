import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
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

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Location loc = (Location) obj;
            return name.equals(loc.name); 
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
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
    private double droneAngle = 0.0; 
    private List<Location> currentPath = null;
    private int pathIndex = 0;
    private javax.swing.Timer timer;
    private String statusMessage = "Clique em 'Gerar Novo Mapa' para começar.";

    private List<Location> entregasPendentes = new ArrayList<>();
    private Set<Location> entregasConcluidas = new HashSet<>();
    private boolean modoEntregasMultiplas = false;
    private boolean indoRecarregar = false;
    
    private double bateriaMaxima = 1500.0; 
    private double bateriaAtual = bateriaMaxima;

    public SimuladorDroneGUI() {
        timer = new javax.swing.Timer(16, e -> animateDrone());
        gerarMapaAleatorio();
    }

    // ==========================================
    // 3. GERADOR DE GRAFO (Estilo Cidade / Grid)
    // ==========================================
    public void gerarMapaAleatorio() {
        graph.clear();
        allLocations.clear();
        timer.stop();

        int width = 750;
        int height = 550; 
        int tamanhoQuarteirao = 100; 

        base = new Location("Base", 50, 300);
        allLocations.add(base);
        graph.put(base, new ArrayList<>());

        List<Point> cruzamentos = new ArrayList<>();
        for (int x = 150; x <= width - 50; x += tamanhoQuarteirao) {
            for (int y = 100; y <= height - 50; y += tamanhoQuarteirao) {
                cruzamentos.add(new Point(x, y));
            }
        }

        Collections.shuffle(cruzamentos, random);

        int numNodes = 6 + random.nextInt(7);
        for (int i = 1; i < numNodes && i <= cruzamentos.size(); i++) {
            Point p = cruzamentos.get(i - 1);
            Location loc = new Location("Local " + i, p.x, p.y);
            allLocations.add(loc);
            graph.put(loc, new ArrayList<>());
        }

        for (int i = 1; i < numNodes; i++) {
            Location current = allLocations.get(i);
            Location connectTo = allLocations.get(random.nextInt(i));
            addBidirectionalRoute(current, connectTo, false);
        }

        for (int i = 0; i < numNodes; i++) {
            Location a = allLocations.get(random.nextInt(numNodes));
            Location b = allLocations.get(random.nextInt(numNodes));
            if (a != b && !routeExists(a, b)) {
                if (calcularDistancia(a, b) <= tamanhoQuarteirao * 2.5) {
                    addBidirectionalRoute(a, b, false);
                }
            }
        }

        bloquearUmaUnicaRota();

        resetDrone();
        statusMessage = "Mapa da cidade gerado com " + numNodes + " locais!";
        repaint();
    }

    public void aleatorizarClima() {
        bloquearUmaUnicaRota();
        statusMessage = "Clima alterado! Uma única rota bloqueada.";
        repaint();
    }

    private void bloquearUmaUnicaRota() {
        List<Location[]> todasAsArestas = new ArrayList<>();
        Set<String> vistas = new HashSet<>();

        for (Map.Entry<Location, List<Route>> entry : graph.entrySet()) {
            Location a = entry.getKey();
            for (Route r : entry.getValue()) {
                r.isBlocked = false; 
                
                Location b = r.destination;
                String id1 = a.name + "-" + b.name;
                String id2 = b.name + "-" + a.name;
                
                if (!vistas.contains(id1) && !vistas.contains(id2)) {
                    todasAsArestas.add(new Location[]{a, b});
                    vistas.add(id1);
                }
            }
        }

        if (!todasAsArestas.isEmpty()) {
            Location[] escolhida = todasAsArestas.get(random.nextInt(todasAsArestas.size()));
            Location a = escolhida[0];
            Location b = escolhida[1];

            for (Route r : graph.get(a)) {
                if (r.destination.equals(b)) r.isBlocked = true;
            }
            for (Route r : graph.get(b)) {
                if (r.destination.equals(a)) r.isBlocked = true;
            }
        }
    }

    private void addBidirectionalRoute(Location a, Location b, boolean isBlocked) {
        graph.get(a).add(new Route(b, isBlocked));
        graph.get(b).add(new Route(a, isBlocked));
    }

    private boolean routeExists(Location a, Location b) {
        for (Route r : graph.get(a)) {
            if (r.destination.equals(b)) return true;
        }
        return false;
    }

    // ==========================================
    // 4. ALGORITMO A* E CAIXEIRO VIAJANTE (TSP)
    // ==========================================
    private class AStarNode implements Comparable<AStarNode> {
        Location location;
        List<Location> path;
        double g; 
        double f; 

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
        
        if (modoEntregasMultiplas) {
            statusMessage = "Entregas já estão em andamento!";
            repaint();
            return;
        }
        
        entregasPendentes.clear();
        for (Location loc : allLocations) {
            if (!loc.equals(base) && !entregasConcluidas.contains(loc)) {
                entregasPendentes.add(loc);
            }
        }
        
        if (entregasPendentes.isEmpty()) {
            statusMessage = "Todas as entregas acessíveis já estão prontas!";
            repaint();
            return;
        }
        
        modoEntregasMultiplas = true;
        
        Location partida = base;
        for (Location loc : allLocations) {
            if (Math.abs(droneX - loc.x) < 1 && Math.abs(droneY - loc.y) < 1) {
                partida = loc;
                break;
            }
        }
        
        otimizarFilaTSP(partida);
        iniciarProximaEntrega(partida);
    }

    private void otimizarFilaTSP(Location pontoDePartida) {
        if (entregasPendentes.isEmpty()) return;

        List<Location> rotaOtimizada = new ArrayList<>();
        Location pontoAtualTSP = pontoDePartida;

        while (!entregasPendentes.isEmpty()) {
            Location vizinhoMaisProximo = null;
            double menorDistancia = Double.MAX_VALUE;

            for (Location candidato : entregasPendentes) {
                double dist = calcularDistancia(pontoAtualTSP, candidato);
                if (dist < menorDistancia) {
                    menorDistancia = dist;
                    vizinhoMaisProximo = candidato;
                }
            }

            if (vizinhoMaisProximo != null) {
                rotaOtimizada.add(vizinhoMaisProximo);
                entregasPendentes.remove(vizinhoMaisProximo);
                pontoAtualTSP = vizinhoMaisProximo; 
            }
        }
        entregasPendentes = rotaOtimizada;
    }

    private void iniciarProximaEntrega(Location pontoAtual) {
        if (pontoAtual.equals(base) && indoRecarregar) {
            bateriaAtual = bateriaMaxima;
            indoRecarregar = false;
        }

        if (entregasPendentes.isEmpty()) {
            if (!pontoAtual.equals(base)) {
                List<Location> caminhoBase = findRoute(pontoAtual, base);
                if (caminhoBase != null) {
                    currentPath = caminhoBase;
                    pathIndex = 0;
                    droneX = pontoAtual.x;
                    droneY = pontoAtual.y;
                    statusMessage = "Fim das entregas. Retornando à base.";
                    timer.start();
                } else {
                    statusMessage = "Fim. Drone isolado da base!";
                }
            } else {
                statusMessage = "Todas as entregas feitas! Drone na base.";
                bateriaAtual = bateriaMaxima; 
            }
            modoEntregasMultiplas = false;
            repaint();
            return;
        }

        Location proximoDestino = entregasPendentes.get(0);
        List<Location> caminhoIda = findRoute(pontoAtual, proximoDestino);
        
        if (caminhoIda == null) {
            entregasPendentes.remove(0);
            iniciarProximaEntrega(pontoAtual);
            return;
        }

        double custoIda = calcularCustoCaminho(caminhoIda);
        List<Location> caminhoVolta = findRoute(proximoDestino, base);
        double custoVolta = (caminhoVolta != null) ? calcularCustoCaminho(caminhoVolta) : 0;

        if (bateriaAtual < (custoIda + custoVolta)) {
            if (pontoAtual.equals(base)) {
                statusMessage = "Pular: " + proximoDestino.name + " está muito longe!";
                entregasPendentes.remove(0);
                iniciarProximaEntrega(pontoAtual);
            } else {
                List<Location> caminhoBase = findRoute(pontoAtual, base);
                if (caminhoBase != null) {
                    currentPath = caminhoBase;
                    pathIndex = 0;
                    droneX = pontoAtual.x;
                    droneY = pontoAtual.y;
                    indoRecarregar = true;
                    statusMessage = "Bateria fraca! Retornando para recarregar.";
                    timer.start();
                } else {
                    statusMessage = "Emergência: Bateria fraca e isolado da base!";
                    modoEntregasMultiplas = false;
                }
            }
        } else {
            entregasPendentes.remove(0);
            currentPath = caminhoIda;
            pathIndex = 0;
            droneX = pontoAtual.x;
            droneY = pontoAtual.y;
            statusMessage = "TSP Otimizado -> Indo para: " + proximoDestino.name;
            timer.start();
        }
        repaint();
    }

    private List<Location> findRoute(Location start, Location end) {
        PriorityQueue<AStarNode> queue = new PriorityQueue<>();
        Map<Location, Double> minCost = new HashMap<>(); 

        List<Location> initialPath = new ArrayList<>();
        initialPath.add(start);
        
        queue.add(new AStarNode(start, initialPath, 0.0, calcularDistancia(start, end)));
        minCost.put(start, 0.0);

        while (!queue.isEmpty()) {
            AStarNode current = queue.poll();

            if (current.location.equals(end)) {
                return current.path;
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
        return null;
    }

    private double calcularCustoCaminho(List<Location> path) {
        double cost = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            cost += calcularDistancia(path.get(i), path.get(i + 1));
        }
        return cost;
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
        indoRecarregar = false;
        bateriaAtual = bateriaMaxima;
        droneAngle = 0.0;
        entregasPendentes.clear();
        entregasConcluidas.clear(); 
        droneX = base.x;
        droneY = base.y;
    }

    private void animateDrone() {
        if (currentPath == null || pathIndex >= currentPath.size() - 1) {
            timer.stop();
            if (modoEntregasMultiplas && currentPath != null) {
                Location localAlcancado = currentPath.get(currentPath.size() - 1);
                iniciarProximaEntrega(localAlcancado);
            } else {
                if (!modoEntregasMultiplas && !statusMessage.contains("Fim")) {
                    statusMessage = "Navegação parada.";
                }
                repaint();
            }
            return;
        }

        Location target = currentPath.get(pathIndex + 1);
        double dx = target.x - droneX;
        double dy = target.y - droneY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        double speed = 5.0; 

        if (distance > 0) {
            droneAngle = Math.atan2(dy, dx);
        }

        if (distance <= speed) {
            bateriaAtual -= distance; 
            droneX = target.x;
            droneY = target.y;
            pathIndex++;
            
            if (!target.equals(base) && !entregasConcluidas.contains(target)) {
                entregasConcluidas.add(target);      
                entregasPendentes.remove(target);
            }
        } else {
            bateriaAtual -= speed;
            droneX += (dx / distance) * speed;
            droneY += (dy / distance) * speed;
        }
        
        if (bateriaAtual < 0) bateriaAtual = 0;
        repaint();
    }

    // ==========================================
    // 5. RENDERIZAÇÃO GRÁFICA (Asfalto e Caminhos)
    // ==========================================
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.setColor(new Color(60, 60, 60)); 
        g2d.fillRect(0, 0, getWidth(), getHeight());

        int tamanhoQuarteirao = 100;

        for (int x = 50; x < getWidth() - 50; x += tamanhoQuarteirao) {
            for (int y = 100; y < getHeight() - 50; y += tamanhoQuarteirao) {
                g2d.setColor(new Color(200, 200, 200)); 
                g2d.fillRoundRect(x + 5, y + 5, tamanhoQuarteirao - 10, tamanhoQuarteirao - 10, 15, 15);
                g2d.setColor(new Color(160, 219, 142)); 
                g2d.fillRoundRect(x + 10, y + 10, tamanhoQuarteirao - 20, tamanhoQuarteirao - 20, 10, 10);
                
                g2d.setColor(new Color(178, 34, 34)); 
                g2d.fillRect(x + 20, y + 20, 25, 25);
                g2d.setColor(new Color(112, 128, 144)); 
                g2d.fillRect(x + 55, y + 50, 25, 30);
                g2d.setColor(new Color(244, 164, 96)); 
                g2d.fillRect(x + 55, y + 20, 25, 20);
            }
        }

        Set<String> drawnEdges = new HashSet<>();
        for (Location loc : graph.keySet()) {
            for (Route route : graph.get(loc)) {
                String edgeId1 = loc.name + "-" + route.destination.name;
                String edgeId2 = route.destination.name + "-" + loc.name;
                if (drawnEdges.contains(edgeId1) || drawnEdges.contains(edgeId2)) continue;
                drawnEdges.add(edgeId1);

                if (route.isBlocked) {
                    g2d.setColor(new Color(255, 69, 0)); 
                    float[] dash = {15.0f, 10.0f};
                    g2d.setStroke(new BasicStroke(4, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, dash, 0.0f));
                    g2d.drawLine(loc.x, loc.y, route.destination.x, route.destination.y);
                } else {
                    g2d.setColor(new Color(255, 215, 0, 150));
                    float[] dash = {5.0f, 15.0f};
                    g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, dash, 0.0f));
                    g2d.drawLine(loc.x, loc.y, route.destination.x, route.destination.y);
                }
            }
        }

        if (currentPath != null && pathIndex < currentPath.size() - 1) {
            g2d.setColor(Color.CYAN);
            float[] dashPath = {10.0f, 10.0f};
            g2d.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10.0f, dashPath, 0.0f));
            
            Location nextNode = currentPath.get(pathIndex + 1);
            g2d.drawLine((int)droneX, (int)droneY, nextNode.x, nextNode.y);
            
            for (int i = pathIndex + 1; i < currentPath.size() - 1; i++) {
                Location a = currentPath.get(i);
                Location b = currentPath.get(i + 1);
                g2d.drawLine(a.x, a.y, b.x, b.y);
            }
        }

        for (Location loc : graph.keySet()) {
            if (loc.equals(base)) {
                g2d.setColor(Color.WHITE);
                g2d.fillOval(loc.x - 16, loc.y - 16, 32, 32);
                g2d.setColor(new Color(34, 139, 34)); 
                g2d.fillOval(loc.x - 12, loc.y - 12, 24, 24); 
            } else {
                g2d.setColor(Color.WHITE);
                g2d.fillOval(loc.x - 12, loc.y - 12, 24, 24);
                
                if (entregasConcluidas.contains(loc)) {
                    g2d.setColor(new Color(50, 205, 50)); 
                } else {
                    g2d.setColor(Color.ORANGE); 
                }
                g2d.fillOval(loc.x - 8, loc.y - 8, 16, 16);
            }
            
            g2d.setColor(new Color(255, 255, 255, 210));
            g2d.fillRect(loc.x - 20, loc.y - 30, 48, 16);
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.BOLD, 11));
            g2d.drawString(loc.name, loc.x - 18, loc.y - 18);
        }

        if (currentPath != null || (droneX == base.x && droneY == base.y)) {
            AffineTransform oldTransform = g2d.getTransform();
            
            g2d.translate(droneX, droneY);
            g2d.rotate(droneAngle);

            g2d.setColor(Color.CYAN);
            g2d.fillOval(-10, -10, 20, 20);
            
            g2d.setColor(Color.BLACK);
            g2d.setStroke(new BasicStroke(2));
            g2d.drawLine(-12, -12, 12, 12);
            g2d.drawLine(-12, 12, 12, -12);
            
            g2d.setColor(Color.RED);
            g2d.fillOval(-16, -16, 8, 8); 
            g2d.fillOval(-16, 8, 8, 8);   
            
            g2d.setColor(new Color(50, 205, 50));
            g2d.fillOval(8, -16, 8, 8);  
            g2d.fillOval(8, 8, 8, 8);    
            
            g2d.setColor(Color.WHITE);
            g2d.fillPolygon(new int[]{8, 14, 8}, new int[]{-4, 0, 4}, 3);

            g2d.setTransform(oldTransform);
        }

        g2d.setColor(new Color(255, 255, 255, 235)); 
        g2d.fillRoundRect(15, 10, 520, 60, 15, 15); 
        
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        g2d.drawString("Status: " + statusMessage, 30, 30);
        
        g2d.drawString("Bateria:", 30, 53);
        g2d.setColor(Color.RED);
        g2d.fillRect(90, 43, 100, 12); 
        
        g2d.setColor(Color.GREEN);
        int larguraBateria = (int) ((bateriaAtual / bateriaMaxima) * 100);
        g2d.fillRect(90, 43, Math.max(0, larguraBateria), 12);
        
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(1));
        g2d.drawRect(90, 43, 100, 12);
    }

    // ==========================================
    // 6. INICIALIZAÇÃO
    // ==========================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Simulador de Drone: A* e TSP Otimizado");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(800, 600);
            frame.setLayout(new BorderLayout());

            SimuladorDroneGUI canvas = new SimuladorDroneGUI();
            frame.add(canvas, BorderLayout.CENTER);

            JPanel panel = new JPanel();
            panel.setBackground(Color.WHITE);
            
            JButton btnGerarMapa = new JButton("Gerar Novo Mapa");
            JButton btnClima = new JButton("Mudar Clima (Obstáculo)");
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