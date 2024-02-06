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

    public static void main(String[] args) {
        loadConfig();
        ExecutorService threadPool = Executors.newFixedThreadPool(maxThreads);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Web Server is listening on port " + port);

            while (true) {
                Socket socket = serverSocket.accept();
                threadPool.execute(new ClientHandler(socket));
            }
        } catch (IOException ex) {
            System.out.println("Server exception: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private static void loadConfig() {
        Properties prop = new Properties();
        try (InputStream input = new FileInputStream(CONFIG_FILE)) {
            prop.load(input);
            port = Integer.parseInt(prop.getProperty("port"));
            root = prop.getProperty("root");
            defaultPage = prop.getProperty("defaultPage");
            maxThreads = Integer.parseInt(prop.getProperty("maxThreads"));
        } catch (IOException ex) {
            ex.printStackTrace();
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
                    // Implement POST request handling
                    // ...
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
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
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

        private void sendResponse(PrintWriter out, int statusCode, String statusMessage, String contentType, String responseText) {
            out.println("HTTP/1.1 " + statusCode + " " + statusMessage);
            out.println("Content-Type: " + contentType);
            out.println();
            out.println(responseText);
            out.flush();
        }

        private void sendBinaryResponse(OutputStream binaryOut, int statusCode, String statusMessage, String contentType, byte[] responseData) throws IOException {
            PrintWriter headerOut = new PrintWriter(binaryOut, true);
            headerOut.println("HTTP/1.1 " + statusCode + " " + statusMessage);
            headerOut.println("Content-Type: " + contentType);
            headerOut.println("Content-Length: " + responseData.length);
            headerOut.println();
            headerOut.flush();

            binaryOut.write(responseData);
            binaryOut.flush();
        }
    }
}
