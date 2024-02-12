import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private static final String CONFIG_FILE = "config.ini";
    private static int port;
    private static String root;
    private static String defaultPage;
    private static int maxThreads;
    private static ExecutorService threadPool;

    

    public static void main(String[] args) {
        
        try {
            loadConfig(); // Load server configuration
            threadPool = Executors.newFixedThreadPool(maxThreads); // Create thread pool
            startServer(); // Start server
        } catch (Exception ex) {
            System.out.println("Server startup failed: " + ex.getMessage()); // Print startup failure message
            ex.printStackTrace(); // Print exception stack trace
            if (threadPool != null) {
                threadPool.shutdown(); // Shutdown thread pool
            }
        }
    }

    private static void startServer() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Web Server is listening on port " + port); // Print listening message

            while (true) {
                Socket socket = serverSocket.accept(); // Accept incoming connection
                threadPool.execute(new ClientHandler(socket)); // Execute client handler in thread pool
            }
        }
    }

    private static void loadConfig() throws IOException {
        Properties prop = new Properties();
        try (InputStream input = new FileInputStream(CONFIG_FILE)) {
            prop.load(input);
            port = Integer.parseInt(prop.getProperty("port"));
            root = prop.getProperty("root").replace("~", System.getProperty("user.home"));
            defaultPage = prop.getProperty("defaultPage");
            maxThreads = Integer.parseInt(prop.getProperty("maxThreads"));
        }
    }

    private static class ClientHandler implements Runnable {
        private Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            PrintWriter out = null;
            try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                OutputStream binaryOut = socket.getOutputStream();
            ) {
                out = new PrintWriter(binaryOut, true);
                String requestLine = in.readLine();
                System.out.println("Received HTTP request: " + requestLine);
                if (requestLine == null) {
                    sendResponse(out, 400, "Bad Request", "text/plain", "Empty request.");
                    return;
                }
    
                String[] requestParts = requestLine.split(" ");
                if (requestParts.length != 3) {
                    sendResponse(out, 400, "Bad Request", "text/plain", "Malformed request.");
                    return;
                }
    
                String method = requestParts[0];
                String resourcePath = requestParts[1];
    
                if ("GET".equals(method)) {
                    handleGetRequest(resourcePath, out, binaryOut);
                } else if ("POST".equals(method)) {
                    // Debugging: Print received POST data
                    String requestBody = "";
                    try {
                        int contentLength = 0;
                        String contentLengthHeader = null;
    
                        // Read request headers to find Content-Length
                        while (true) {
                            String headerLine = in.readLine();
                            if (headerLine == null || headerLine.isEmpty()) {
                                break;
                            }
                            if (headerLine.startsWith("Content-Length: ")) {
                                contentLengthHeader = headerLine;
                            } else if (headerLine.equalsIgnoreCase("chunked: yes")) {
                                useChunked.set(true); // Set chunked flag to true if "chunked: yes" header is found
                                System.out.println("chunked: yes");
                            }
                        }
    
                        if (contentLengthHeader != null) {
                            contentLength = Integer.parseInt(contentLengthHeader.substring("Content-Length: ".length()));
                        }
    
                        // Read the POST request body
                        char[] buffer = new char[contentLength];
                        in.read(buffer, 0, contentLength);
                        requestBody = new String(buffer);
                    } catch (IOException ex) {
                        // Handle any exceptions
                    }
    
                    System.out.println("Received POST data:\n" + requestBody); // Debugging line
    
                    Map<String, String> parameters = parseParameters(requestBody);
                    handlePostRequest(parameters, out);
                } else {
                    sendResponse(out, 501, "Not Implemented", "text/plain", "Method not implemented.");
                }
    
            } catch (IOException ex) {
                if (out != null) {
                    sendResponse(out, 500, "Internal Server Error", "text/plain", "Internal server error.");
                }
                ex.printStackTrace();
            } finally {
                try {
                    if (out != null) {
                        out.close();
                    }
                    socket.close();
                    useChunked.remove(); // Remove the chunked flag after handling the request
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private static ThreadLocal<Boolean> useChunked = ThreadLocal.withInitial(() -> false);

        private static void sendResponse(PrintWriter out, int statusCode, String statusMessage, String contentType, String responseText) {
            boolean chunked = useChunked.get();
            if (chunked) {
                sendChunkedResponse(out, statusCode, statusMessage, contentType, responseText);
            } else {
                sendNormalResponse(out, statusCode, statusMessage, contentType, responseText);
            }
        }

        private static void sendBinaryResponse(OutputStream binaryOut, int statusCode, String statusMessage, String contentType, byte[] responseData) throws IOException {
            boolean chunked = useChunked.get();
            if (chunked) {
                sendChunkedBinaryResponse(binaryOut, statusCode, statusMessage, contentType, responseData);
            } else {
                sendNormalBinaryResponse(binaryOut, statusCode, statusMessage, contentType, responseData);
            }
        }

        private static void sendNormalResponse(PrintWriter out, int statusCode, String statusMessage, String contentType, String responseText) {
            String httpResponse = "HTTP/1.1 " + statusCode + " " + statusMessage + "\nContent-Type: " + contentType + "\nContent-Length: " + responseText.length() + "\n";
            System.out.println("Sending HTTP response: \n" + httpResponse);
            out.println(httpResponse);
            out.println();
            out.println(responseText);
            out.flush();
        }

        private static void sendChunkedResponse(PrintWriter out, int statusCode, String statusMessage, String contentType, String responseText) {
            String httpResponse = "HTTP/1.1 " + statusCode + " " + statusMessage + "\nContent-Type: " + contentType + "\nTransfer-Encoding: chunked\n";
            System.out.println("Sending HTTP response: \n" + httpResponse);
            out.println(httpResponse);
            out.println();
            out.flush();

            // Send response body in chunks
            int chunkSize = 1024;
            for (int i = 0; i < responseText.length(); i += chunkSize) {
                int endIndex = Math.min(i + chunkSize, responseText.length());
                String chunk = Integer.toHexString(endIndex - i) + "\r\n" + responseText.substring(i, endIndex) + "\r\n";
                out.print(chunk);
                out.flush();
            }
            // Send the last chunk to indicate end of response
            out.println("0\r\n");
            out.flush();
        }

        private static void sendNormalBinaryResponse(OutputStream binaryOut, int statusCode, String statusMessage, String contentType, byte[] responseData) throws IOException {
            PrintWriter headerOut = new PrintWriter(binaryOut, true);
            String httpResponse = "HTTP/1.1 " + statusCode + " " + statusMessage + "\nContent-Type: " + contentType + "\nContent-Length: " + responseData.length + "\n";
            System.out.println("Sending HTTP response: \n" + httpResponse);
            headerOut.println(httpResponse);
            headerOut.flush();

            binaryOut.write(responseData);
            binaryOut.flush();
        }

        private static void sendChunkedBinaryResponse(OutputStream binaryOut, int statusCode, String statusMessage, String contentType, byte[] responseData) throws IOException {
            PrintWriter headerOut = new PrintWriter(binaryOut, true);
            String httpResponse = "HTTP/1.1 " + statusCode + " " + statusMessage + "\nContent-Type: " + contentType + "\nTransfer-Encoding: chunked\n";
            System.out.println("Sending HTTP response: \n" + httpResponse);
            headerOut.println(httpResponse);
            headerOut.flush();

            // Send response body in chunks
            int chunkSize = 1024;
            for (int i = 0; i < responseData.length; i += chunkSize) {
                int endIndex = Math.min(i + chunkSize, responseData.length);
                String chunkSizeHeader = Integer.toHexString(endIndex - i) + "\r\n";
                binaryOut.write(chunkSizeHeader.getBytes());
                binaryOut.write(Arrays.copyOfRange(responseData, i, endIndex));
                binaryOut.write("\r\n".getBytes());
                binaryOut.flush();
            }
            // Send the last chunk to indicate end of response
            binaryOut.write("0\r\n\r\n".getBytes());
            binaryOut.flush();
}


        
    
        private String getContentType(String filePath) {
            if (filePath.endsWith(".html") || filePath.endsWith(".htm")) {
                return "text/html";
            } else if (filePath.endsWith(".bmp") || filePath.endsWith(".gif") || filePath.endsWith(".png") || filePath.endsWith(".jpg") || filePath.endsWith(".jpeg")) {
                return "image/" + filePath.substring(filePath.lastIndexOf('.') + 1);
            } else if (filePath.endsWith(".ico")) {
                return "image/x-icon";
            } else {
                return "application/octet-stream";
            }
        }

        private void handleGetRequest(String resourcePath, PrintWriter out, OutputStream binaryOut) {
            // Sanitize the resourcePath to prevent directory traversal
            resourcePath = sanitizeResourcePath(resourcePath);
        
            if ("/".equals(resourcePath)) {
                resourcePath = defaultPage;
            } else {
                resourcePath = root + resourcePath;
            }
        

            File resourceFile = new File(resourcePath);
            if (resourceFile.exists() && resourceFile.isFile()) {
                try {
                    String contentType = getContentType(resourcePath);
                    if (contentType.startsWith("text/") || contentType.equals("application/octet-stream")) {
                        sendResponse(out, 200, "OK", contentType, readFileContents(resourceFile));
                    } else { // for binary data like images
                        sendBinaryResponse(binaryOut, 200, "OK", contentType, readBinaryFileContents(resourceFile));
                    }
                } catch (IOException e) {
                    sendResponse(out, 500, "Internal Server Error", "text/plain", "Internal server error.");
                }
            } else {
                sendResponse(out, 404, "Not Found", "text/plain", "Resource not found.");
            }
        }

        private String readFileContents(File file) throws IOException {
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }
            return content.toString();
        }

        private byte[] readBinaryFileContents(File file) throws IOException {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            try (FileInputStream fileInput = new FileInputStream(file)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = fileInput.read(buffer)) != -1) {
                    byteStream.write(buffer, 0, bytesRead);
                }
            }
            return byteStream.toByteArray();
        }



        private Map<String, String> parseParameters(String requestBody) {
            Map<String, String> parameters = new HashMap<>();
            String[] parameterPairs = requestBody.split("&");
            for (String pair : parameterPairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    String paramName = keyValue[0];
                    try {
                        String paramValue = URLDecoder.decode(keyValue[1], "UTF-8");
                        parameters.put(paramName, paramValue);
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                        // Handle the exception or log an error message as needed
                    }
                }
            }
            return parameters;
        }

        private void handlePostRequest(Map<String, String> parameters, PrintWriter out) {
            // Generate the HTML page with parameter details
            StringBuilder htmlResponse = new StringBuilder();
            htmlResponse.append("<!DOCTYPE html>\n<html>\n<head>\n<title>Parameters Info</title>\n</head>\n<body>\n<h1>Parameters Info</h1>\n");
        
            // Define the order of parameters based on their names in the form
            String[] parameterOrder = {"sender", "receiver", "subject", "message", "confirm"};
        
            htmlResponse.append("<ul>");
            for (String paramName : parameterOrder) {
                String paramValue = parameters.get(paramName);
                if (paramValue != null) {
                    htmlResponse.append("<li>").append(paramName).append(": ").append(paramValue).append("</li>");
                }
            }
            htmlResponse.append("</ul>");
        
            htmlResponse.append("</body>\n</html>");
        
            // Send the HTML content as the response
            sendResponse(out, 200, "OK", "text/html", htmlResponse.toString());
        }

        private String sanitizeResourcePath(String resourcePath) {
            // Remove occurrences of '/../' to prevent directory traversal
            resourcePath = resourcePath.replaceAll("/\\.\\./", "/");
            return resourcePath;
        }
        
        private void handleHeadRequest(String resourcePath, PrintWriter out) {
            // Implement handling of HEAD request here
            // This method should behave similar to GET but without sending the actual content
            // You should print request and response headers as per the requirement
        
            resourcePath = sanitizeResourcePath(resourcePath);
        
            if ("/".equals(resourcePath)) {
                resourcePath = defaultPage;
            } else {
                resourcePath = root + resourcePath;
            }
        
            File resourceFile = new File(resourcePath);
            if (resourceFile.exists() && resourceFile.isFile()) {
                String contentType = getContentType(resourcePath);
                long contentLength = resourceFile.length(); // New line to get content length
                sendHeadResponse(out, 200, "OK", contentType, contentLength); // New line to send HEAD response
            } else {
                sendResponse(out, 404, "Not Found", "text/plain", "Resource not found.");
            }
        }
                
        private void sendHeadResponse(PrintWriter out, int statusCode, String statusMessage, String contentType, long contentLength) {
            out.println("HTTP/1.1 " + statusCode + " " + statusMessage);
            out.println("Content-Type: " + contentType);
            out.println("Content-Length: " + contentLength); // New line to include content length
            out.println(); // New line to indicate end of headers
            out.flush();
        }
        
        private void handleTraceRequest(BufferedReader in, PrintWriter out) throws IOException {
            // Implement handling of TRACE request here
            // This method should echo back the received request headers to the client
            StringBuilder requestHeaders = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                requestHeaders.append(line).append("\r\n");
            }
        
            // Print received request headers
            System.out.println("Received TRACE request headers:");
            System.out.println(requestHeaders.toString());
        
            // Send back the received request headers to the client
            sendResponse(out, 200, "OK", "message/http", requestHeaders.toString());

            
        }

        
    }
} 