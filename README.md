# Stock Trading Platform GUI

A feature-rich Java desktop stock trading platform equipped with an interactive Graphical User Interface (GUI). Designed to simulate real-world market operations, this application allows users to execute trades, manage portfolios, monitor live stock trends, and track pending orders[cite: 2].

## Features
- **Interactive GUI Dashboard:** Built using Java Swing/AWT to provide a user-friendly interface for seamless trading[cite: 2].
- **Portfolio Management:** Track holdings, view cash balances, and monitor overall investment performance[cite: 2].
- **Order Execution & Tracking:** Handle market and pending orders dynamically through dedicated classes like `PendingOrder` and `OrderType`[cite: 2].
- **Market Charts & Data:** Integrated chart panels (`StockChartPanel`) and database managers (`DatabaseManager`) to visualize price movements and store transaction history[cite: 2].

## Project Structure
- `Task 2/StockTradingPlatformGUI.java`: Main application entry point and GUI controller[cite: 2].
- `Task 2/Stock.java`: Defines stock attributes, pricing, and market data structures[cite: 2].
- `Task 2/Portfolio.java`: Manages user assets, holdings, and valuations[cite: 2].
- `Task 2/DatabaseManager.java`: Handles data persistence and record storage[cite: 2].
- `Task 2/portfolio_fallback.txt`: Fallback flat file for data storage[cite: 2].
- `Task 2/PendingOrder.java`: Manages queueing and processing of pending trades[cite: 2].
- `Task 2/OrderType.java`: Defines categories of trading orders[cite: 2].
- `Task 2/StockChartPanel.java`: Renders visual charts for stock market trends[cite: 2].
- `Task 2/Transaction.java`: Logs individual trading activity details[cite: 2].

## Prerequisites
- Java Development Kit (JDK) installed on your system[cite: 2].

## How to Run
1. Clone the repository:
```bash
git clone https://github.com/NotJarvis09/Stock-Trading-Platform-GUI.git
```
2. Navigate to the project directory.
3. Compile the Java source file:
```bash
cd "Task 2"
javac *.java
```
4. Run the application:
```bash
cd ..
java Task 2.StockTradingPlatformGUI
```
