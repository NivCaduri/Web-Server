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
    }
}
