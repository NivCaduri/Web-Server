# Web Server README

## Overview:

This project implements a basic HTTP server in Java. The server handles GET and POST requests, serves static files, supports chunked encoding, and provides a simple web interface. The primary classes include `Server`, `ClientHandler`, and `ServerSocket`. Additionally, the `README.txt` file explains the purpose of each class and provides insights into the design choices made during implementation.

## Classes:

### 1. `Server`:
- **Role**: The main class responsible for initializing and starting the HTTP server. It loads server configuration from the `config.ini` file, creates a thread pool using `ExecutorService`, and listens for incoming client requests using a `ServerSocket`.

### 2. `ClientHandler`:
- **Role**: Handles individual client connections in separate threads. It processes incoming HTTP requests, manages request and response headers, and delegates handling of GET and POST requests. It also handles chunked encoding for both text and binary data.

### 3. `ServerSocket`:
- **Role**: Represents the server socket that listens for incoming connections. It accepts incoming client connections and delegates each connection to a `ClientHandler` thread.

## Design:

The server follows a multi-threaded design to handle concurrent client connections efficiently. The `ExecutorService` manages a fixed-size thread pool to process incoming requests concurrently. The use of separate `ClientHandler` threads ensures that each client connection is handled independently, preventing one slow request from affecting the server's responsiveness to other requests.

The server supports both GET and POST requests, serving static files and processing form submissions. It includes basic error handling and responds with appropriate HTTP status codes. The implementation also supports chunked encoding for text and binary data, enhancing the efficiency of data transfer.

The project maintains modularity and readability by encapsulating related functionalities within classes. The use of a configuration file (`config.ini`) allows easy customization of server settings. Overall, the design prioritizes simplicity, concurrency, and flexibility in handling HTTP requests.
