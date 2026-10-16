import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

// 1. Order Types for Market, Limit, and Stop-Loss executions
enum OrderType {
    MARKET_BUY("Market Buy"),
    MARKET_SELL("Market Sell"),
    LIMIT_BUY("Limit Buy (<= Target)"),
    LIMIT_SELL("Limit Sell (>= Target)"),
    STOP_LOSS("Stop-Loss Sell (<= Target)");

    private final String label;
    OrderType(String label) { this.label = label; }
    @Override public String toString() { return label; }
}

// 2. Stock Class: Manages live market price, fluctuations, and price history
class Stock {
    private final String symbol;
    private final String companyName;
    private double currentPrice;
    private double previousPrice;
    private final List<Double> priceHistory = new ArrayList<>();

    public Stock(String symbol, String companyName, double initialPrice) {
        this.symbol = symbol.toUpperCase();
        this.companyName = companyName;
        this.currentPrice = initialPrice;
        this.previousPrice = initialPrice;
        this.priceHistory.add(initialPrice);
    }

    public String getSymbol() { return symbol; }
    public String getCompanyName() { return companyName; }
    public double getCurrentPrice() { return currentPrice; }
    public List<Double> getPriceHistory() { return priceHistory; }

    public void fluctuatePrice(Random random) {
        this.previousPrice = this.currentPrice;
        double changePercent = (random.nextDouble() * 0.08) - 0.04; // -4% to +4%
        this.currentPrice = Math.max(1.00, Math.round(this.currentPrice * (1 + changePercent) * 100.0) / 100.0);
        
        priceHistory.add(currentPrice);
        if (priceHistory.size() > 40) { // Keep last 40 data points
            priceHistory.remove(0);
        }
    }

    public double getChangePercent() {
        return ((currentPrice - previousPrice) / previousPrice) * 100.0;
    }
}

// 3. Custom Swing Chart Component
class StockChartPanel extends JPanel {
    private Stock stock;

    public void setStock(Stock stock) {
        this.stock = stock;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        // Background
        g2.setColor(new Color(245, 247, 250));
        g2.fillRect(0, 0, width, height);

        if (stock == null || stock.getPriceHistory().isEmpty()) {
            g2.setColor(Color.GRAY);
            g2.setFont(new Font("SansSerif", Font.BOLD, 14));
            g2.drawString("Select a stock to view historical price chart", width / 2 - 140, height / 2);
            return;
        }

        List<Double> history = stock.getPriceHistory();
        int pad = 50;
        int graphWidth = width - (2 * pad);
        int graphHeight = height - (2 * pad);

        // Find Min & Max for scaling
        double minPrice = history.stream().min(Double::compare).orElse(0.0);
        double maxPrice = history.stream().max(Double::compare).orElse(1.0);
        if (minPrice == maxPrice) {
            maxPrice += 1.0;
            minPrice = Math.max(0.0, minPrice - 1.0);
        }
        double priceRange = maxPrice - minPrice;

        // Draw Grid lines & Y-axis labels
        g2.setColor(new Color(220, 225, 230));
        g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        int gridSteps = 5;
        for (int i = 0; i <= gridSteps; i++) {
            int y = pad + (graphHeight * i / gridSteps);
            g2.drawLine(pad, y, width - pad, y);
            double priceVal = maxPrice - (priceRange * i / gridSteps);
            g2.setColor(Color.DARK_GRAY);
            g2.drawString(String.format("$%.2f", priceVal), 5, y + 4);
            g2.setColor(new Color(220, 225, 230));
        }

        // Title Header
        g2.setColor(new Color(30, 41, 59));
        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2.drawString(stock.getSymbol() + " - " + stock.getCompanyName() + " (Live Price Trend)", pad, 25);

        if (history.size() < 2) return;

        // Plot Line Graph
        boolean isPositive = stock.getChangePercent() >= 0;
        Color lineColor = isPositive ? new Color(34, 139, 34) : new Color(220, 20, 60);
        g2.setColor(lineColor);
        g2.setStroke(new BasicStroke(2.5f));

        int[] xPoints = new int[history.size()];
        int[] yPoints = new int[history.size()];

        for (int i = 0; i < history.size(); i++) {
            double p = history.get(i);
            xPoints[i] = pad + (int) ((double) i / (history.size() - 1) * graphWidth);
            yPoints[i] = pad + graphHeight - (int) ((p - minPrice) / priceRange * graphHeight);
        }

        for (int i = 0; i < history.size() - 1; i++) {
            g2.drawLine(xPoints[i], yPoints[i], xPoints[i + 1], yPoints[i + 1]);
        }

        // Draw data points & highlight latest price
        for (int i = 0; i < history.size(); i++) {
            boolean isLast = (i == history.size() - 1);
            g2.setColor(isLast ? Color.BLUE : lineColor);
            int radius = isLast ? 6 : 4;
            g2.fillOval(xPoints[i] - radius / 2, yPoints[i] - radius / 2, radius, radius);
        }

        // Current Price Label
        double latest = stock.getCurrentPrice();
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2.drawString(String.format("Latest: $%.2f (%+.2f%%)", latest, stock.getChangePercent()), width - 180, 25);
    }
}

// 4. PendingOrder Class
class PendingOrder {
    private final int id;
    private final String symbol;
    private final OrderType orderType;
    private final int quantity;
    private final double targetPrice;
    private final String createdAt;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public PendingOrder(int id, String symbol, OrderType orderType, int quantity, double targetPrice) {
        this(id, symbol, orderType, quantity, targetPrice, LocalDateTime.now().format(FMT));
    }

    public PendingOrder(int id, String symbol, OrderType orderType, int quantity, double targetPrice, String createdAt) {
        this.id = id;
        this.symbol = symbol.toUpperCase();
        this.orderType = orderType;
        this.quantity = quantity;
        this.targetPrice = targetPrice;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public String getSymbol() { return symbol; }
    public OrderType getOrderType() { return orderType; }
    public int getQuantity() { return quantity; }
    public double getTargetPrice() { return targetPrice; }
    public String getCreatedAt() { return createdAt; }

    public boolean shouldTrigger(double marketPrice) {
        switch (orderType) {
            case LIMIT_BUY: return marketPrice <= targetPrice;
            case LIMIT_SELL: return marketPrice >= targetPrice;
            case STOP_LOSS: return marketPrice <= targetPrice;
            default: return false;
        }
    }
}

// 5. Transaction Class
class Transaction {
    private final String timestamp;
    private final String type;
    private final String symbol;
    private final int quantity;
    private final double pricePerShare;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Transaction(String type, String symbol, int quantity, double pricePerShare) {
        this(LocalDateTime.now().format(FMT), type, symbol, quantity, pricePerShare);
    }

    public Transaction(String timestamp, String type, String symbol, int quantity, double pricePerShare) {
        this.timestamp = timestamp;
        this.type = type;
        this.symbol = symbol;
        this.quantity = quantity;
        this.pricePerShare = pricePerShare;
    }

    public String getTimestamp() { return timestamp; }
    public String getType() { return type; }
    public String getSymbol() { return symbol; }
    public int getQuantity() { return quantity; }
    public double getPricePerShare() { return pricePerShare; }
    public double getTotalAmount() { return quantity * pricePerShare; }
}

// 6. Portfolio Class
class Portfolio {
    private final double initialCapital;
    private double cashBalance;
    private int nextOrderId = 1;

    private final Map<String, Integer> holdings = new LinkedHashMap<>();
    private final Map<String, Double> averageCostBasis = new HashMap<>();
    private final List<Transaction> transactions = new ArrayList<>();
    private final List<PendingOrder> pendingOrders = new ArrayList<>();

    public Portfolio(double initialCapital) {
        this.initialCapital = initialCapital;
        this.cashBalance = initialCapital;
    }

    public double getInitialCapital() { return initialCapital; }
    public double getCashBalance() { return cashBalance; }
    public void setCashBalance(double cashBalance) { this.cashBalance = cashBalance; }
    public Map<String, Integer> getHoldings() { return holdings; }
    public Map<String, Double> getAverageCostBasis() { return averageCostBasis; }
    public List<Transaction> getTransactions() { return transactions; }
    public List<PendingOrder> getPendingOrders() { return pendingOrders; }

    public void syncNextOrderId() {
        for (PendingOrder o : pendingOrders) {
            if (o.getId() >= nextOrderId) nextOrderId = o.getId() + 1;
        }
    }

    public String executeBuy(Stock stock, int quantity, String executionType) {
        if (quantity <= 0) return "Quantity must be greater than zero.";
        double totalCost = stock.getCurrentPrice() * quantity;
        if (totalCost > cashBalance) {
            return String.format("Insufficient funds! Need $%.2f, Available: $%.2f", totalCost, cashBalance);
        }

        String sym = stock.getSymbol();
        int curQty = holdings.getOrDefault(sym, 0);
        double curAvg = averageCostBasis.getOrDefault(sym, 0.0);
        double newAvg = ((curQty * curAvg) + totalCost) / (curQty + quantity);

        cashBalance -= totalCost;
        holdings.put(sym, curQty + quantity);
        averageCostBasis.put(sym, newAvg);
        transactions.add(new Transaction(executionType, sym, quantity, stock.getCurrentPrice()));

        return String.format("Executed %s: %d %s @ $%.2f ($%.2f)", executionType, quantity, sym, stock.getCurrentPrice(), totalCost);
    }

    public String executeSell(Stock stock, int quantity, String executionType) {
        if (quantity <= 0) return "Quantity must be greater than zero.";
        String sym = stock.getSymbol();
        int owned = holdings.getOrDefault(sym, 0);
        if (quantity > owned) {
            return String.format("Insufficient shares! You own %d shares of %s.", owned, sym);
        }

        double proceeds = stock.getCurrentPrice() * quantity;
        cashBalance += proceeds;

        int remaining = owned - quantity;
        if (remaining == 0) {
            holdings.remove(sym);
            averageCostBasis.remove(sym);
        } else {
            holdings.put(sym, remaining);
        }

        transactions.add(new Transaction(executionType, sym, quantity, stock.getCurrentPrice()));
        return String.format("Executed %s: %d %s @ $%.2f ($%.2f)", executionType, quantity, sym, stock.getCurrentPrice(), proceeds);
    }

    public String placePendingOrder(Stock stock, OrderType type, int quantity, double targetPrice) {
        if (quantity <= 0 || targetPrice <= 0) return "Quantity and Target Price must be greater than zero.";
        if ((type == OrderType.LIMIT_SELL || type == OrderType.STOP_LOSS)
                && holdings.getOrDefault(stock.getSymbol(), 0) < quantity) {
            return "Cannot place sell order: you do not own enough shares of " + stock.getSymbol();
        }
        PendingOrder order = new PendingOrder(nextOrderId++, stock.getSymbol(), type, quantity, targetPrice);
        pendingOrders.add(order);
        return String.format("Placed %s (#%d): %d %s @ trigger $%.2f", type.name(), order.getId(), quantity, stock.getSymbol(), targetPrice);
    }

    public List<String> evaluatePendingOrders(Map<String, Stock> market) {
        List<String> executionLogs = new ArrayList<>();
        Iterator<PendingOrder> it = pendingOrders.iterator();

        while (it.hasNext()) {
            PendingOrder order = it.next();
            Stock stock = market.get(order.getSymbol());
            if (stock == null || !order.shouldTrigger(stock.getCurrentPrice())) continue;

            if (order.getOrderType() == OrderType.LIMIT_BUY) {
                double cost = stock.getCurrentPrice() * order.getQuantity();
                if (cashBalance >= cost) {
                    String msg = executeBuy(stock, order.getQuantity(), "LIMIT_BUY");
                    executionLogs.add("[TRIGGERED #" + order.getId() + "] " + msg);
                    it.remove();
                }
            } else {
                int owned = holdings.getOrDefault(stock.getSymbol(), 0);
                if (owned >= order.getQuantity()) {
                    String msg = executeSell(stock, order.getQuantity(), order.getOrderType().name());
                    executionLogs.add("[TRIGGERED #" + order.getId() + "] " + msg);
                    it.remove();
                }
            }
        }
        return executionLogs;
    }
}

// 7. DatabaseManager Class
class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:trading_platform.db";
    private static final String FALLBACK_FILE = "portfolio_fallback.txt";
    private boolean jdbcAvailable = false;

    public DatabaseManager() {
        try {
            Class.forName("org.sqlite.JDBC");
            jdbcAvailable = true;
            initializeTables();
        } catch (ClassNotFoundException e) {
            jdbcAvailable = false;
        }
    }

    private void initializeTables() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS account (id INTEGER PRIMARY KEY, cash REAL)");
            stmt.execute("CREATE TABLE IF NOT EXISTS holdings (symbol TEXT PRIMARY KEY, quantity INTEGER, avg_cost REAL)");
            stmt.execute("CREATE TABLE IF NOT EXISTS transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, timestamp TEXT, type TEXT, symbol TEXT, quantity INTEGER, price REAL)");
            stmt.execute("CREATE TABLE IF NOT EXISTS pending_orders (id INTEGER PRIMARY KEY, symbol TEXT, order_type TEXT, quantity INTEGER, target_price REAL, created_at TEXT)");
        } catch (SQLException e) {
            System.err.println("SQLite Init Error: " + e.getMessage());
        }
    }

    public String savePortfolio(Portfolio p) {
        if (!jdbcAvailable) return saveToFallbackFile(p);
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.setAutoCommit(false);
            try (Statement clear = conn.createStatement()) {
                clear.execute("DELETE FROM account");
                clear.execute("DELETE FROM holdings");
                clear.execute("DELETE FROM transactions");
                clear.execute("DELETE FROM pending_orders");
            }

            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO account (id, cash) VALUES (1, ?)")) {
                ps.setDouble(1, p.getCashBalance());
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO holdings VALUES (?, ?, ?)")) {
                for (Map.Entry<String, Integer> entry : p.getHoldings().entrySet()) {
                    ps.setString(1, entry.getKey());
                    ps.setInt(2, entry.getValue());
                    ps.setDouble(3, p.getAverageCostBasis().getOrDefault(entry.getKey(), 0.0));
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO transactions (timestamp, type, symbol, quantity, price) VALUES (?, ?, ?, ?, ?)")) {
                for (Transaction t : p.getTransactions()) {
                    ps.setString(1, t.getTimestamp());
                    ps.setString(2, t.getType());
                    ps.setString(3, t.getSymbol());
                    ps.setInt(4, t.getQuantity());
                    ps.setDouble(5, t.getPricePerShare());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO pending_orders VALUES (?, ?, ?, ?, ?, ?)")) {
                for (PendingOrder o : p.getPendingOrders()) {
                    ps.setInt(1, o.getId());
                    ps.setString(2, o.getSymbol());
                    ps.setString(3, o.getOrderType().name());
                    ps.setInt(4, o.getQuantity());
                    ps.setDouble(5, o.getTargetPrice());
                    ps.setString(6, o.getCreatedAt());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            conn.commit();
            return "Saved portfolio state to SQLite database.";
        } catch (SQLException e) {
            return "SQLite Save Error: " + e.getMessage();
        }
    }

    public String loadPortfolio(Portfolio p) {
        if (!jdbcAvailable) return loadFromFallbackFile(p);
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT cash FROM account WHERE id = 1")) {
                if (!rs.next()) return "SQLite connected (new portfolio session).";
                p.setCashBalance(rs.getDouble("cash"));
            }

            p.getHoldings().clear();
            p.getAverageCostBasis().clear();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT symbol, quantity, avg_cost FROM holdings")) {
                while (rs.next()) {
                    p.getHoldings().put(rs.getString("symbol"), rs.getInt("quantity"));
                    p.getAverageCostBasis().put(rs.getString("symbol"), rs.getDouble("avg_cost"));
                }
            }

            p.getTransactions().clear();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT timestamp, type, symbol, quantity, price FROM transactions ORDER BY id ASC")) {
                while (rs.next()) {
                    p.getTransactions().add(new Transaction(rs.getString("timestamp"), rs.getString("type"),
                            rs.getString("symbol"), rs.getInt("quantity"), rs.getDouble("price")));
                }
            }

            p.getPendingOrders().clear();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id, symbol, order_type, quantity, target_price, created_at FROM pending_orders")) {
                while (rs.next()) {
                    p.getPendingOrders().add(new PendingOrder(rs.getInt("id"), rs.getString("symbol"),
                            OrderType.valueOf(rs.getString("order_type")), rs.getInt("quantity"),
                            rs.getDouble("target_price"), rs.getString("created_at")));
                }
            }
            p.syncNextOrderId();
            return "Loaded portfolio from SQLite database.";
        } catch (SQLException e) {
            return "SQLite Load Error: " + e.getMessage();
        }
    }

    private String saveToFallbackFile(Portfolio p) {
        try (PrintWriter out = new PrintWriter(new FileWriter(FALLBACK_FILE))) {
            out.println("CASH|" + p.getCashBalance());
            for (String sym : p.getHoldings().keySet()) {
                out.println("HOLDING|" + sym + "|" + p.getHoldings().get(sym) + "|" + p.getAverageCostBasis().get(sym));
            }
            for (Transaction t : p.getTransactions()) {
                out.println("TX|" + t.getTimestamp() + "|" + t.getType() + "|" + t.getSymbol() + "|" + t.getQuantity() + "|" + t.getPricePerShare());
            }
            for (PendingOrder o : p.getPendingOrders()) {
                out.println("ORDER|" + o.getId() + "|" + o.getSymbol() + "|" + o.getOrderType().name() + "|" + o.getQuantity() + "|" + o.getTargetPrice() + "|" + o.getCreatedAt());
            }
            return "Saved to fallback file (add sqlite-jdbc.jar for SQLite).";
        } catch (IOException e) {
            return "File Save Error: " + e.getMessage();
        }
    }

    private String loadFromFallbackFile(Portfolio p) {
        File file = new File(FALLBACK_FILE);
        if (!file.exists()) return "Ready (File persistence mode).";
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            p.getHoldings().clear();
            p.getAverageCostBasis().clear();
            p.getTransactions().clear();
            p.getPendingOrders().clear();
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\|");
                switch (parts[0]) {
                    case "CASH": p.setCashBalance(Double.parseDouble(parts[1])); break;
                    case "HOLDING":
                        p.getHoldings().put(parts[1], Integer.parseInt(parts[2]));
                        p.getAverageCostBasis().put(parts[1], Double.parseDouble(parts[3]));
                        break;
                    case "TX":
                        p.getTransactions().add(new Transaction(parts[1], parts[2], parts[3],
                                Integer.parseInt(parts[4]), Double.parseDouble(parts[5])));
                        break;
                    case "ORDER":
                        p.getPendingOrders().add(new PendingOrder(Integer.parseInt(parts[1]), parts[2],
                                OrderType.valueOf(parts[3]), Integer.parseInt(parts[4]),
                                Double.parseDouble(parts[5]), parts[6]));
                        break;
                }
            }
            p.syncNextOrderId();
            return "Loaded portfolio from fallback file.";
        } catch (Exception e) {
            return "Fallback Load Error: " + e.getMessage();
        }
    }
}

// 8. Main Swing GUI Application with Custom Chart
public class StockTradingPlatformGUI extends JFrame {
    private final Map<String, Stock> market = new LinkedHashMap<>();
    private final Portfolio portfolio = new Portfolio(10000.00);
    private final DatabaseManager dbManager = new DatabaseManager();
    private final Random random = new Random();

    // Dashboard Metric Labels
    private final JLabel cashLabel = new JLabel();
    private final JLabel holdingsValLabel = new JLabel();
    private final JLabel netWorthLabel = new JLabel();
    private final JLabel totalPlLabel = new JLabel();
    private final JLabel statusLabel = new JLabel();

    // Table Models
    private final DefaultTableModel marketModel = new DefaultTableModel(
            new String[]{"Symbol", "Company", "Price ($)", "Change (%)"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final DefaultTableModel holdingsModel = new DefaultTableModel(
            new String[]{"Symbol", "Shares", "Avg Cost", "Mkt Price", "Mkt Value", "Unrealized P/L"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final DefaultTableModel ordersModel = new DefaultTableModel(
            new String[]{"ID", "Type", "Symbol", "Qty", "Trigger Price", "Current Price", "Created"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final DefaultTableModel txModel = new DefaultTableModel(
            new String[]{"Timestamp", "Type", "Symbol", "Qty", "Price", "Total"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };

    // Controls
    private JComboBox<String> symbolCombo;
    private JComboBox<OrderType> orderTypeCombo;
    private JTextField qtyField;
    private JTextField targetPriceField;
    private JTable ordersTable;
    private StockChartPanel chartPanel;
    private javax.swing.Timer marketTimer;

    public StockTradingPlatformGUI() {
        super("OOP Stock Trading Platform — Swing GUI, SQLite & Live Price Chart");
        initMarket();
        String loadStatus = dbManager.loadPortfolio(portfolio);
        initUI(loadStatus);
        refreshAllViews();
    }

    private void initMarket() {
        market.put("AAPL", new Stock("AAPL", "Apple Inc.", 185.50));
        market.put("GOOGL", new Stock("GOOGL", "Alphabet Inc.", 172.30));
        market.put("MSFT", new Stock("MSFT", "Microsoft Corp.", 420.00));
        market.put("NVDA", new Stock("NVDA", "NVIDIA Corp.", 118.75));
        market.put("TSLA", new Stock("TSLA", "Tesla Inc.", 210.40));
        market.put("AMZN", new Stock("AMZN", "Amazon.com Inc.", 198.10));
    }

    private void initUI(String initialStatus) {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1240, 740);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // 1. Top Performance Dashboard
        JPanel dashboard = new JPanel(new GridLayout(1, 4, 15, 0));
        dashboard.setBorder(new EmptyBorder(12, 15, 12, 15));
        dashboard.setBackground(new Color(28, 37, 54));

        styleMetricLabel(cashLabel);
        styleMetricLabel(holdingsValLabel);
        styleMetricLabel(netWorthLabel);
        styleMetricLabel(totalPlLabel);

        dashboard.add(cashLabel);
        dashboard.add(holdingsValLabel);
        dashboard.add(netWorthLabel);
        dashboard.add(totalPlLabel);
        add(dashboard, BorderLayout.NORTH);

        // 2. Center Split Pane: Left (Market + Order Ticket) | Right (Tabs including Chart)
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildLeftMarketPanel(), buildRightTabs());
        splitPane.setDividerLocation(470);
        splitPane.setBorder(new EmptyBorder(0, 10, 0, 10));
        add(splitPane, BorderLayout.CENTER);

        // 3. Bottom Status Bar
        JPanel bottomBar = new JPanel(new BorderLayout(10, 0));
        bottomBar.setBorder(new EmptyBorder(6, 15, 8, 15));
        statusLabel.setText(initialStatus);
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 12));

        JButton saveBtn = new JButton("Save Portfolio Now");
        saveBtn.addActionListener(e -> statusLabel.setText(dbManager.savePortfolio(portfolio)));

        bottomBar.add(statusLabel, BorderLayout.WEST);
        bottomBar.add(saveBtn, BorderLayout.EAST);
        add(bottomBar, BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { dbManager.savePortfolio(portfolio); }
        });

        marketTimer = new javax.swing.Timer(3000, e -> tickMarket());
    }

    private void styleMetricLabel(JLabel label) {
        label.setForeground(Color.WHITE);
        label.setFont(new Font("SansSerif", Font.BOLD, 15));
        label.setHorizontalAlignment(SwingConstants.CENTER);
    }

    private JPanel buildLeftMarketPanel() {
        JPanel leftPanel = new JPanel(new BorderLayout(8, 8));

        JTable marketTable = new JTable(marketModel);
        marketTable.setRowHeight(24);
        JScrollPane marketScroll = new JScrollPane(marketTable);
        marketScroll.setBorder(new TitledBorder("Live Market Feed"));

        JPanel simBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton tickBtn = new JButton("Simulate Market Tick");
        JToggleButton autoTickToggle = new JToggleButton("Auto-Tick (3s): OFF");

        tickBtn.addActionListener(e -> tickMarket());
        autoTickToggle.addActionListener(e -> {
            if (autoTickToggle.isSelected()) {
                marketTimer.start();
                autoTickToggle.setText("Auto-Tick (3s): ON");
            } else {
                marketTimer.stop();
                autoTickToggle.setText("Auto-Tick (3s): OFF");
            }
        });
        simBar.add(tickBtn);
        simBar.add(autoTickToggle);

        JPanel topMarketSection = new JPanel(new BorderLayout());
        topMarketSection.add(marketScroll, BorderLayout.CENTER);
        topMarketSection.add(simBar, BorderLayout.SOUTH);

        // Order Ticket
        JPanel orderForm = new JPanel(new GridLayout(5, 2, 8, 8));
        orderForm.setBorder(new TitledBorder("Place Order"));

        symbolCombo = new JComboBox<>(market.keySet().toArray(new String[0]));
        orderTypeCombo = new JComboBox<>(OrderType.values());
        qtyField = new JTextField("10");
        targetPriceField = new JTextField();
        targetPriceField.setEnabled(false);

        marketTable.getSelectionModel().addListSelectionListener(e -> {
            int row = marketTable.getSelectedRow();
            if (row >= 0) {
                String sym = (String) marketModel.getValueAt(row, 0);
                symbolCombo.setSelectedItem(sym);
                targetPriceField.setText(String.format("%.2f", market.get(sym).getCurrentPrice()));
                chartPanel.setStock(market.get(sym)); // Update chart view
            }
        });

        symbolCombo.addActionListener(e -> {
            String sym = (String) symbolCombo.getSelectedItem();
            if (sym != null && market.containsKey(sym)) {
                chartPanel.setStock(market.get(sym));
            }
        });

        orderTypeCombo.addActionListener(e -> {
            OrderType selected = (OrderType) orderTypeCombo.getSelectedItem();
            boolean needsTarget = selected != OrderType.MARKET_BUY && selected != OrderType.MARKET_SELL;
            targetPriceField.setEnabled(needsTarget);
            if (needsTarget && targetPriceField.getText().isEmpty()) {
                String sym = (String) symbolCombo.getSelectedItem();
                targetPriceField.setText(String.format("%.2f", market.get(sym).getCurrentPrice()));
            }
        });

        JButton submitOrderBtn = new JButton("Submit Order");
        submitOrderBtn.setBackground(new Color(34, 139, 34));
        submitOrderBtn.setForeground(Color.WHITE);
        submitOrderBtn.setFocusPainted(false);
        submitOrderBtn.addActionListener(e -> handleOrderSubmission());

        orderForm.add(new JLabel("Stock Symbol:")); orderForm.add(symbolCombo);
        orderForm.add(new JLabel("Order Type:")); orderForm.add(orderTypeCombo);
        orderForm.add(new JLabel("Share Quantity:")); orderForm.add(qtyField);
        orderForm.add(new JLabel("Trigger Price ($):")); orderForm.add(targetPriceField);
        orderForm.add(new JLabel("")); orderForm.add(submitOrderBtn);

        leftPanel.add(topMarketSection, BorderLayout.CENTER);
        leftPanel.add(orderForm, BorderLayout.SOUTH);
        return leftPanel;
    }

    private JTabbedPane buildRightTabs() {
        JTabbedPane tabs = new JTabbedPane();

        // Tab 1: Price Chart Component
        chartPanel = new StockChartPanel();
        if (!market.isEmpty()) {
            chartPanel.setStock(market.values().iterator().next());
        }
        tabs.addTab("📈 Price Chart", chartPanel);

        // Tab 2: Holdings
        JTable holdingsTable = new JTable(holdingsModel);
        holdingsTable.setRowHeight(24);
        tabs.addTab("💼 Portfolio Holdings", new JScrollPane(holdingsTable));

        // Tab 3: Pending Orders
        JPanel ordersPanel = new JPanel(new BorderLayout(5, 5));
        ordersTable = new JTable(ordersModel);
        ordersTable.setRowHeight(24);
        JButton cancelOrderBtn = new JButton("Cancel Selected Pending Order");
        cancelOrderBtn.addActionListener(e -> cancelSelectedOrder());
        ordersPanel.add(new JScrollPane(ordersTable), BorderLayout.CENTER);
        ordersPanel.add(cancelOrderBtn, BorderLayout.SOUTH);
        tabs.addTab("⏳ Pending Orders", ordersPanel);

        // Tab 4: Transaction History
        JTable txTable = new JTable(txModel);
        txTable.setRowHeight(24);
        tabs.addTab("📜 Trade History", new JScrollPane(txTable));

        return tabs;
    }

    private void handleOrderSubmission() {
        String sym = (String) symbolCombo.getSelectedItem();
        Stock stock = market.get(sym);
        OrderType type = (OrderType) orderTypeCombo.getSelectedItem();

        try {
            int qty = Integer.parseInt(qtyField.getText().trim());
            String result;
            if (type == OrderType.MARKET_BUY) {
                result = portfolio.executeBuy(stock, qty, "MARKET_BUY");
            } else if (type == OrderType.MARKET_SELL) {
                result = portfolio.executeSell(stock, qty, "MARKET_SELL");
            } else {
                double target = Double.parseDouble(targetPriceField.getText().trim());
                result = portfolio.placePendingOrder(stock, type, qty, target);
            }
            statusLabel.setText(result);
            dbManager.savePortfolio(portfolio);
            refreshAllViews();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Enter valid numbers for Quantity and Trigger Price.", "Error", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void cancelSelectedOrder() {
        int row = ordersTable.getSelectedRow();
        if (row < 0) {
            statusLabel.setText("Select a pending order to cancel.");
            return;
        }
        int orderId = (int) ordersModel.getValueAt(row, 0);
        portfolio.getPendingOrders().removeIf(o -> o.getId() == orderId);
        statusLabel.setText("Canceled Pending Order #" + orderId);
        dbManager.savePortfolio(portfolio);
        refreshAllViews();
    }

    private void tickMarket() {
        for (Stock s : market.values()) {
            s.fluctuatePrice(random);
        }
        List<String> triggered = portfolio.evaluatePendingOrders(market);
        if (!triggered.isEmpty()) {
            statusLabel.setText(String.join(" | ", triggered));
            dbManager.savePortfolio(portfolio);
        } else {
            statusLabel.setText("Market prices updated.");
        }
        refreshAllViews();
        chartPanel.repaint();
    }

    private void refreshAllViews() {
        marketModel.setRowCount(0);
        for (Stock s : market.values()) {
            marketModel.addRow(new Object[]{s.getSymbol(), s.getCompanyName(), String.format("$%.2f", s.getCurrentPrice()), String.format("%+.2f%%", s.getChangePercent())});
        }

        holdingsModel.setRowCount(0);
        double totalHoldingsValue = 0.0;
        for (Map.Entry<String, Integer> entry : portfolio.getHoldings().entrySet()) {
            String sym = entry.getKey();
            int qty = entry.getValue();
            double avgCost = portfolio.getAverageCostBasis().getOrDefault(sym, 0.0);
            double mktPrice = market.get(sym).getCurrentPrice();
            double mktVal = qty * mktPrice;
            double unrealizedPl = mktVal - (qty * avgCost);
            totalHoldingsValue += mktVal;

            holdingsModel.addRow(new Object[]{sym, qty, String.format("$%.2f", avgCost), String.format("$%.2f", mktPrice), String.format("$%.2f", mktVal), String.format("$%+.2f", unrealizedPl)});
        }

        ordersModel.setRowCount(0);
        for (PendingOrder o : portfolio.getPendingOrders()) {
            double curPrice = market.get(o.getSymbol()).getCurrentPrice();
            ordersModel.addRow(new Object[]{o.getId(), o.getOrderType().name(), o.getSymbol(), o.getQuantity(), String.format("$%.2f", o.getTargetPrice()), String.format("$%.2f", curPrice), o.getCreatedAt()});
        }

        txModel.setRowCount(0);
        List<Transaction> txs = portfolio.getTransactions();
        for (int i = txs.size() - 1; i >= 0; i--) {
            Transaction t = txs.get(i);
            txModel.addRow(new Object[]{t.getTimestamp(), t.getType(), t.getSymbol(), t.getQuantity(), String.format("$%.2f", t.getPricePerShare()), String.format("$%.2f", t.getTotalAmount())});
        }

        double netWorth = portfolio.getCashBalance() + totalHoldingsValue;
        double totalPl = netWorth - portfolio.getInitialCapital();
        double plPct = (totalPl / portfolio.getInitialCapital()) * 100.0;

        cashLabel.setText(String.format("Cash: $%.2f", portfolio.getCashBalance()));
        holdingsValLabel.setText(String.format("Holdings: $%.2f", totalHoldingsValue));
        netWorthLabel.setText(String.format("Net Worth: $%.2f", netWorth));
        totalPlLabel.setText(String.format("Total P/L: $%+.2f (%+.2f%%)", totalPl, plPct));
        totalPlLabel.setForeground(totalPl >= 0 ? new Color(102, 255, 153) : new Color(255, 102, 102));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new StockTradingPlatformGUI().setVisible(true));
    }
}