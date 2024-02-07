# Web Server README

## Overview:

This Java program implements a basic multi-threaded web server. It includes functionality for handling HTTP GET, POST, HEAD, and TRACE requests. The server is configured through a `config.ini` file, and it serves static content from a specified root directory. The server supports dynamic content generation for POST requests, echoing TRACE request headers, and responding to HEAD requests.

## Classes:

### 1. Server:

The main class representing the web server. It initializes the server configuration, creates a thread pool, and listens for incoming connections. The `Server` class includes the `main` method for starting the server.

### 2. ClientHandler:

Implements the `Runnable` interface and represents a thread tasked with handling client requests. It processes HTTP requests, delegates to appropriate methods based on the request method, and sends responses back to clients.

### 3. Configuration Loader (loadConfig):

A utility class responsible for loading server configuration parameters from the `config.ini` file. It utilizes the `Properties` class to read port, root directory, default page, and maximum threads.

### 4. HTTP Request Handlers (handleGetRequest, handlePostRequest, handleHeadRequest, handleTraceRequest):

Methods within the `ClientHandler` class that handle specific types of HTTP requests. They process the requests, generate appropriate responses, and perform necessary actions based on the request type.

### 5. File Operations (readFileContents, readBinaryFileContents):

Utility methods for reading the content of text and binary files, respectively. Used to serve content in HTTP responses.

### 6. Response Sender Methods (sendResponse, sendBinaryResponse, sendHeadResponse):

Methods responsible for sending HTTP responses to clients. They format and send appropriate headers along with content.

### 7. Parameter Parser (parseParameters):

A utility method within `ClientHandler` to parse parameters from the body of POST requests. It decodes parameter values and returns them as a map.

### 8. Security Sanitizer (sanitizeResourcePath):

A method to sanitize resource paths, preventing directory traversal attacks by removing occurrences of '/../' in the path.

## Design:

The server follows a multi-threaded design to handle concurrent client connections efficiently. Each incoming connection is assigned to a separate thread (`ClientHandler`), allowing the server to process multiple requests simultaneously. The server uses a fixed-size thread pool (`ExecutorService`) to manage the concurrency. Configuration is read from a file (`config.ini`), enabling easy adjustment of server parameters. The server supports various HTTP methods, handles different types of requests, and includes basic security measures to prevent directory traversal. Overall, the design emphasizes simplicity, modularity, and efficient handling of client requests.