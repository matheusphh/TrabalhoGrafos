import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.*;
import java.util.List;

public class SimuladorDroneGUI extends JPanel {

    // ==========================================
    // 1. MODELAGEM (Vértices, Arestas, Pesos)
    // ==========================================
    enum TipoLocal {
        BASE, HOSPITAL, ESCOLA, COMUNIDADE
    }

    static class Location {
        String name;
        int x, y;
        TipoLocal tipo;
        boolean urgente;

        public Location(String name, int x, int y, TipoLocal tipo) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.tipo = tipo;
            this.urgente = (tipo == TipoLocal.HOSPITAL);
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
        double risco;

        public Route(Location destination, boolean isBlocked, double risco) {
            this.destination = destination;
            this.isBlocked = isBlocked;
            this.risco = risco;
        }
    }

    // ==========================================
    // 2. ESTADO DO SIMULADOR
    // ==========================================
    private Map<Location, List<Route>> graph = new HashMap<>();
    private List<Location> bases = new ArrayList<>();
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
    private boolean exibirMST = false; 
    private List<Location[]> mstEdges = new ArrayList<>();
    
    private double bateriaMaxima = 2000.0; // Aumentado para dar conta de mais entregas
    private double bateriaAtual = bateriaMaxima;

    public SimuladorDroneGUI() {
        timer = new javax.swing.Timer(16, e -> animateDrone());
        gerarMapaAleatorio();
    }

    // ==========================================
    // 3. GERADOR DE GRAFO (Baseado em Melhor Rota)
    // ==========================================
    public void gerarMapaAleatorio() {
        graph.clear();
        allLocations.clear();
        bases.clear();
        mstEdges.clear();
        timer.stop();

        int width = 750;
        int height = 550;
        int tamanhoQuarteirao = 100;

        Location base1 = new Location("Base 1", 50, 300, TipoLocal.BASE);
        Location base2 = new Location("Base 2", 700, 300, TipoLocal.BASE);
        
        bases.add(base1);
        bases.add(base2);
        allLocations.add(base1);
        allLocations.add(base2);
        graph.put(base1, new ArrayList<>());
        graph.put(base2, new ArrayList<>());

        List<Point> cruzamentos = new ArrayList<>();
        for (int x = 150; x <= width - 150; x += tamanhoQuarteirao) {
            for (int y = 100; y <= height - 50; y += tamanhoQuarteirao) {
                cruzamentos.add(new Point(x, y));
            }
        }

        Collections.shuffle(cruzamentos, random);

        int numNodes = 8 + random.nextInt(5);
        for (int i = 1; i < numNodes && i <= cruzamentos.size(); i++) {
            Point p = cruzamentos.get(i - 1);
            
            TipoLocal tipo = TipoLocal.COMUNIDADE;
            if (i == 1 || i == 2) tipo = TipoLocal.HOSPITAL;
            else if (i == 3 || i == 4) tipo = TipoLocal.ESCOLA;
            
            Location loc = new Location(tipo.name() + " " + i, p.x, p.y, tipo);
            allLocations.add(loc);
            graph.put(loc, new ArrayList<>());
        }

        Set<Location> unvisited = new HashSet<>(allLocations);
        Set<Location> visited = new HashSet<>();
        Location startNode = allLocations.get(0);
        visited.add(startNode);
        unvisited.remove(startNode);

        while (!unvisited.isEmpty()) {
            Location bestFrom = null;
            Location bestTo = null;
            double minDist = Double.MAX_VALUE;

            for (Location v : visited) {
                for (Location u : unvisited) {
                    double dist = calcularDistancia(v, u);
                    if (dist < minDist) {
                        minDist = dist;
                        bestFrom = v;
                        bestTo = u;
                    }
                }
            }
            
            if (bestFrom != null && bestTo != null) {
                addBidirectionalRoute(bestFrom, bestTo, false);
                visited.add(bestTo);
                unvisited.remove(bestTo);
            }
        }

        for (Location a : allLocations) {
            Location closest = null;
            double minD = Double.MAX_VALUE;
            for (Location b : allLocations) {
                if (a != b && !routeExists(a, b)) {
                    double d = calcularDistancia(a, b);
                    if (d < minD) {
                        minD = d;
                        closest = b;
                    }
                }
            }
            if (closest != null && minD <= tamanhoQuarteirao * 2.2) { 
                addBidirectionalRoute(a, closest, false);
            }
        }

        bloquearUmaUnicaRota();
        calcularMST(); 

        resetDrone();
        statusMessage = "Mapa inteligente gerado com 2 Bases de Distribuição!";
        repaint();
    }

    public void aleatorizarClima() {
        bloquearUmaUnicaRota();
        calcularMST(); 
        statusMessage = "Clima alterado! Nova rota bloqueada.";
        repaint();
    }
    
    public void alternarMST() {
        exibirMST = !exibirMST;
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
        double riscoAresta = 1.0 + (random.nextDouble() * 0.8);
        graph.get(a).add(new Route(b, isBlocked, riscoAresta));
        graph.get(b).add(new Route(a, isBlocked, riscoAresta));
    }

    private boolean routeExists(Location a, Location b) {
        for (Route r : graph.get(a)) {
            if (r.destination.equals(b)) return true;
        }
        return false;
    }

    // ==========================================
    // 4. ALGORITMOS (A*, MST, TSP)
    // ==========================================
    
    private void calcularMST() {
        mstEdges.clear();
        if (allLocations.isEmpty()) return;

        Set<Location> visitados = new HashSet<>();
        PriorityQueue<EdgeNode> pq = new PriorityQueue<>(Comparator.comparingDouble(e -> e.cost));

        Location startNode = allLocations.get(0);
        visitados.add(startNode);

        for (Route r : graph.get(startNode)) {
            if (!r.isBlocked) pq.add(new EdgeNode(startNode, r.destination, calcularDistancia(startNode, r.destination) * r.risco));
        }

        while (!pq.isEmpty() && visitados.size() < allLocations.size()) {
            EdgeNode edge = pq.poll();
            if (!visitados.contains(edge.to)) {
                visitados.add(edge.to);
                mstEdges.add(new Location[]{edge.from, edge.to});
                
                for (Route r : graph.get(edge.to)) {
                    if (!r.isBlocked && !visitados.contains(r.destination)) {
                        pq.add(new EdgeNode(edge.to, r.destination, calcularDistancia(edge.to, r.destination) * r.risco));
                    }
                }
            }
        }
    }

    private static class EdgeNode {
        Location from, to;
        double cost;
        EdgeNode(Location from, Location to, double cost) {
            this.from = from; this.to = to; this.cost = cost;
        }
    }

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
            if (loc.tipo != TipoLocal.BASE && !entregasConcluidas.contains(loc)) {
                entregasPendentes.add(loc);
            }
        }
        
        if (entregasPendentes.isEmpty()) {
            statusMessage = "Todas as entregas acessíveis já estão prontas!";
            repaint();
            return;
        }
        
        modoEntregasMultiplas = true;
        
        Location partida = bases.get(0); 
        for (Location loc : allLocations) {
            if (Math.abs(droneX - loc.x) < 1 && Math.abs(droneY - loc.y) < 1) {
                partida = loc;
                break;
            }
        }
        
        otimizarFilaTSPPrioridade(partida);
        iniciarProximaEntrega(partida);
    }

    private void otimizarFilaTSPPrioridade(Location pontoDePartida) {
        if (entregasPendentes.isEmpty()) return;

        List<Location> urgentes = new ArrayList<>();
        List<Location> normais = new ArrayList<>();

        for (Location loc : entregasPendentes) {
            if (loc.urgente) urgentes.add(loc);
            else normais.add(loc);
        }

        List<Location> rotaOtimizada = new ArrayList<>();
        Location pontoAtualTSP = pontoDePartida;

        while (!urgentes.isEmpty()) {
            Location vizinhoMaisProximo = null;
            double menorDistancia = Double.MAX_VALUE;
            for (Location candidato : urgentes) {
                double dist = calcularDistancia(pontoAtualTSP, candidato);
                if (dist < menorDistancia) {
                    menorDistancia = dist;
                    vizinhoMaisProximo = candidato;
                }
            }
            rotaOtimizada.add(vizinhoMaisProximo);
            urgentes.remove(vizinhoMaisProximo);
            pontoAtualTSP = vizinhoMaisProximo;
        }

        while (!normais.isEmpty()) {
            Location vizinhoMaisProximo = null;
            double menorDistancia = Double.MAX_VALUE;
            for (Location candidato : normais) {
                double dist = calcularDistancia(pontoAtualTSP, candidato);
                if (dist < menorDistancia) {
                    menorDistancia = dist;
                    vizinhoMaisProximo = candidato;
                }
            }
            rotaOtimizada.add(vizinhoMaisProximo);
            normais.remove(vizinhoMaisProximo);
            pontoAtualTSP = vizinhoMaisProximo;
        }

        entregasPendentes = rotaOtimizada;
    }

    // --- NOVA LÓGICA DE SEGURANÇA E BATERIA BLINDADA ---
    private void iniciarProximaEntrega(Location pontoAtual) {
        if (pontoAtual.tipo == TipoLocal.BASE && indoRecarregar) {
            bateriaAtual = bateriaMaxima;
            indoRecarregar = false;
        }

        if (entregasPendentes.isEmpty()) {
            if (pontoAtual.tipo != TipoLocal.BASE) {
                // Descobre a base acessível mais barata para o pouso final
                double menorCusto = Double.MAX_VALUE;
                List<Location> melhorCaminhoBase = null;
                Location melhorBase = null;

                for (Location b : bases) {
                    List<Location> cam = findRoute(pontoAtual, b);
                    if (cam != null) {
                        double c = calcularCustoCaminho(cam);
                        if (c < menorCusto) {
                            menorCusto = c;
                            melhorCaminhoBase = cam;
                            melhorBase = b;
                        }
                    }
                }

                if (melhorCaminhoBase != null) {
                    currentPath = melhorCaminhoBase;
                    pathIndex = 0;
                    droneX = pontoAtual.x;
                    droneY = pontoAtual.y;
                    statusMessage = "Fim das entregas. Retornando à " + melhorBase.name + ".";
                    timer.start();
                } else {
                    statusMessage = "Emergência: Rotas para bases bloqueadas!";
                }
            } else {
                statusMessage = "Todas as entregas feitas! Drone seguro na " + pontoAtual.name + ".";
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
        
        // Simula todos os cenários de retorno a partir do próximo destino
        double custoVolta = Double.MAX_VALUE;
        for (Location b : bases) {
            List<Location> cam = findRoute(proximoDestino, b);
            if (cam != null) {
                double c = calcularCustoCaminho(cam);
                if (c < custoVolta) {
                    custoVolta = c;
                }
            }
        }

        // MARGEM DE SEGURANÇA: Exige 20% a mais de bateria do que o necessário
        double margemSeguranca = 1.2;

        // Se for impossível voltar de lá (custo == MAX) ou bateria não der conta
        if (custoVolta == Double.MAX_VALUE || bateriaAtual < ((custoIda + custoVolta) * margemSeguranca)) {
            if (pontoAtual.tipo == TipoLocal.BASE) {
                statusMessage = "Pular: " + proximoDestino.name + " está muito arriscado!";
                entregasPendentes.remove(0);
                iniciarProximaEntrega(pontoAtual);
            } else {
                // Aborta e foge para a base acessível mais barata
                double menorCustoBase = Double.MAX_VALUE;
                List<Location> melhorCaminhoBase = null;
                Location melhorBase = null;

                for (Location b : bases) {
                    List<Location> cam = findRoute(pontoAtual, b);
                    if (cam != null) {
                        double c = calcularCustoCaminho(cam);
                        if (c < menorCustoBase) {
                            menorCustoBase = c;
                            melhorCaminhoBase = cam;
                            melhorBase = b;
                        }
                    }
                }

                if (melhorCaminhoBase != null) {
                    currentPath = melhorCaminhoBase;
                    pathIndex = 0;
                    droneX = pontoAtual.x;
                    droneY = pontoAtual.y;
                    indoRecarregar = true;
                    statusMessage = "Reserva acionada! Retornando para " + melhorBase.name + ".";
                    timer.start();
                } else {
                    statusMessage = "Emergência grave: Isolado das bases e sem bateria!";
                    modoEntregasMultiplas = false;
                }
            }
        } else {
            // Tudo seguro, missão aprovada.
            entregasPendentes.remove(0);
            currentPath = caminhoIda;
            pathIndex = 0;
            droneX = pontoAtual.x;
            droneY = pontoAtual.y;
            
            String urgenciaTag = proximoDestino.urgente ? "[URGENTE] " : "";
            statusMessage = urgenciaTag + "Indo para: " + proximoDestino.name;
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
                    double stepCost = calcularDistancia(current.location, neighbor) * edge.risco;
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
        droneX = bases.get(0).x; 
        droneY = bases.get(0).y;
    }

    private boolean isDroneAtAnyBase() {
        for (Location b : bases) {
            if (droneX == b.x && droneY == b.y) return true;
        }
        return false;
    }

    private void animateDrone() {
        if (currentPath == null || pathIndex >= currentPath.size() - 1) {
            timer.stop();
            if (modoEntregasMultiplas && currentPath != null) {
                Location localAlcancado = currentPath.get(currentPath.size() - 1);
                iniciarProximaEntrega(localAlcancado);
            } else {
                if (!modoEntregasMultiplas && !statusMessage.contains("Fim") && !statusMessage.contains("seguro")) {
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
            
            if (target.tipo != TipoLocal.BASE && !entregasConcluidas.contains(target)) {
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
    // 5. RENDERIZAÇÃO GRÁFICA
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

        if (exibirMST) {
            g2d.setColor(new Color(255, 20, 147, 180)); 
            g2d.setStroke(new BasicStroke(8, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (Location[] aresta : mstEdges) {
                g2d.drawLine(aresta[0].x, aresta[0].y, aresta[1].x, aresta[1].y);
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
            if (entregasConcluidas.contains(loc)) {
                g2d.setColor(new Color(50, 205, 50)); 
                g2d.fillOval(loc.x - 12, loc.y - 12, 24, 24);
            } else {
                switch (loc.tipo) {
                    case BASE:
                        g2d.setColor(Color.WHITE);
                        g2d.fillOval(loc.x - 16, loc.y - 16, 32, 32);
                        g2d.setColor(new Color(34, 139, 34)); 
                        g2d.fillOval(loc.x - 12, loc.y - 12, 24, 24); 
                        break;
                    case HOSPITAL:
                        g2d.setColor(Color.RED);
                        g2d.fillRect(loc.x - 12, loc.y - 12, 24, 24);
                        g2d.setColor(Color.WHITE); 
                        g2d.fillRect(loc.x - 2, loc.y - 8, 4, 16);
                        g2d.fillRect(loc.x - 8, loc.y - 2, 16, 4);
                        break;
                    case ESCOLA:
                        g2d.setColor(Color.BLUE);
                        g2d.fillOval(loc.x - 12, loc.y - 12, 24, 24);
                        break;
                    case COMUNIDADE:
                        g2d.setColor(Color.ORANGE);
                        g2d.fillOval(loc.x - 10, loc.y - 10, 20, 20);
                        break;
                }
            }
            
            g2d.setColor(new Color(255, 255, 255, 210));
            g2d.fillRect(loc.x - 30, loc.y - 30, 70, 16);
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.BOLD, 10));
            g2d.drawString(loc.name, loc.x - 28, loc.y - 18);
        }

        if (currentPath != null || isDroneAtAnyBase()) {
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
            JFrame frame = new JFrame("Simulador de Drone: Múltiplas Bases (Segurança Otimizada)");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(850, 600);
            frame.setLayout(new BorderLayout());

            SimuladorDroneGUI canvas = new SimuladorDroneGUI();
            frame.add(canvas, BorderLayout.CENTER);

            JPanel panel = new JPanel();
            panel.setBackground(Color.WHITE);
            
            JButton btnGerarMapa = new JButton("Gerar Novo Mapa");
            JButton btnClima = new JButton("Mudar Clima (Obstáculo)");
            JButton btnMST = new JButton("Exibir Rede MST");
            JButton btnTodasEntregas = new JButton("Fazer Todas as Entregas");

            btnGerarMapa.addActionListener(e -> canvas.gerarMapaAleatorio());
            btnClima.addActionListener(e -> canvas.aleatorizarClima());
            btnMST.addActionListener(e -> canvas.alternarMST());
            btnTodasEntregas.addActionListener(e -> canvas.fazerTodasEntregas());

            panel.add(btnGerarMapa);
            panel.add(btnClima);
            panel.add(btnMST);
            panel.add(btnTodasEntregas);
            
            frame.add(panel, BorderLayout.SOUTH);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}