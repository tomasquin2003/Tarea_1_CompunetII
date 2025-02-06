import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Clase principal del servidor web que escucha en un puerto
 * y crea hilos para manejar cada solicitud HTTP entrante.
 * Esta versión implementa la Parte I (mostrar solicitud) y la Parte II (generar respuesta HTTP)
 * con el código segmentado en clases para mejor organización.
 */
public final class ServidorWeb {

    public static void main(String argv[]) throws Exception {
        // Establece el número de puerto.
        int puerto = 6789;

        // Estableciendo el socket de escucha.
        ServerSocket socketDeEscucha = new ServerSocket(puerto);
        System.out.println("Servidor Web Segmentado iniciado en el puerto: " + puerto);

        // Procesando las solicitudes HTTP en un ciclo infinito.
        while (true) {
            // Escuchando las solicitudes de conexión TCP.
            Socket socketDeConexion = socketDeEscucha.accept();

            // Construye un objeto para procesar el mensaje de solicitud HTTP.
            ClientRequestHandler requestHandler = new ClientRequestHandler(socketDeConexion);

            // Crea un nuevo hilo para procesar la solicitud.
            Thread hilo = new Thread(requestHandler);

            // Inicia el hilo.
            hilo.start();
        }
    }
}

/**
 * Clase para manejar una solicitud HTTP entrante en un hilo separado.
 * Actúa como controlador principal para cada conexión de cliente,
 * delegando el procesamiento de la solicitud y la respuesta a otras clases.
 */
class ClientRequestHandler implements Runnable {
    private final Socket socket;

    public ClientRequestHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            processRequest();
        } catch (Exception e) {
            System.err.println("Error al procesar la solicitud: " + e.getMessage());
        } finally {
            try {
                socket.close(); // Asegurar que el socket se cierre siempre
            } catch (IOException e) {
                System.err.println("Error al cerrar el socket: " + e.getMessage());
            }
        }
    }

    private void processRequest() throws Exception {
        InputStream is = socket.getInputStream();
        DataOutputStream os = new DataOutputStream(socket.getOutputStream());
        BufferedReader br = new BufferedReader(new InputStreamReader(is));

        // 1. Parsear la solicitud HTTP
        HttpRequest request = new HttpRequest(br);
        request.parseRequest(); // Analiza la solicitud desde el BufferedReader

        // Imprimir la solicitud en consola (Parte I)
        request.printRequestContent();

        // 2. Manejar la solicitud y generar la respuesta
        HttpResponse response = new HttpResponse(os);
        HttpFileHandler fileHandler = new HttpFileHandler();

        if (request.getMethod().equals("GET")) {
            String fileName = request.getFileName();
            if (fileName.equals("/")) {
                fileName = "/index.html"; // Servir index.html por defecto
            }
            File file = new File("." + fileName); // Archivos en el directorio actual

            if (file.exists() && file.isFile()) {
                fileHandler.serveFile(file, response); // Servir el archivo si existe
            } else {
                response.sendErrorResponse("404", "Not Found", "Archivo " + fileName + " no encontrado."); // 404 si no existe
            }
        } else {
            response.sendErrorResponse("400", "Bad Request", "Este servidor solo soporta el método GET."); // 400 para otros métodos
        }
    }
}


/**
 * Clase para representar y analizar una solicitud HTTP.
 * Responsable de parsear la línea de solicitud y los encabezados.
 */
class HttpRequest {
    private final BufferedReader bufferedReader;
    private String method;
    private String fileName;
    private String httpVersion;

    public HttpRequest(BufferedReader br) {
        this.bufferedReader = br;
    }

    public void parseRequest() throws IOException {
        String requestLine = bufferedReader.readLine();
        if (requestLine == null) {
            throw new IOException("Solicitud HTTP vacía o conexión cerrada prematuramente.");
        }
        System.out.println("\nLínea de Solicitud Recibida: " + requestLine); // Para debugging

        StringTokenizer tokenizer = new StringTokenizer(requestLine);
        if (tokenizer.countTokens() >= 2) {
            method = tokenizer.nextToken();
            fileName = tokenizer.nextToken();
            if (tokenizer.hasMoreTokens()) {
                httpVersion = tokenizer.nextToken();
            } else {
                httpVersion = "HTTP/1.0"; // Asumir HTTP/1.0 si no se especifica
            }
        } else {
            throw new IOException("Solicitud HTTP malformada: línea de solicitud incompleta.");
        }
    }

    public void printRequestContent() throws IOException {
        System.out.println("=====================Solicitud HTTP Recibida=====================");
        System.out.println(getMethod() + " " + getFileName() + " " + getHttpVersion());

        String headerLine;
        while ((headerLine = bufferedReader.readLine()) != null && headerLine.length() != 0) {
            System.out.println(headerLine);
        }
        System.out.println("=========================Fin de Solicitud=========================");
    }


    public String getMethod() {
        return method;
    }

    public String getFileName() {
        return fileName;
    }

    public String getHttpVersion() {
        return httpVersion;
    }
}


/**
 * Clase para construir y enviar respuestas HTTP.
 * Encapsula la lógica para crear líneas de estado, encabezados y cuerpos de respuesta.
 */
class HttpResponse {
    private final DataOutputStream outputStream;
    private static final String CRLF = "\r\n";

    public HttpResponse(DataOutputStream os) {
        this.outputStream = os;
    }

    public void sendFileResponse(String contentType, byte[] fileData) throws IOException {
        sendStatusLine("200", "OK");
        sendContentTypeHeader(contentType);
        sendConnectionCloseHeader();
        sendContentLengthHeader(fileData.length);
        sendEndOfHeaders();
        sendResponseBody(fileData);
    }

    public void sendErrorResponse(String statusCode, String statusText, String messageBody) throws IOException {
        sendStatusLine(statusCode, statusText);
        sendContentTypeHeader("text/html");
        sendConnectionCloseHeader();
        sendEndOfHeaders();
        sendResponseBody(generateErrorHtml(statusCode, statusText, messageBody).getBytes());
    }


    private void sendStatusLine(String statusCode, String statusText) throws IOException {
        outputStream.writeBytes("HTTP/1.0 " + statusCode + " " + statusText + CRLF);
    }

    private void sendContentTypeHeader(String contentType) throws IOException {
        outputStream.writeBytes("Content-Type: " + contentType + CRLF);
    }

    private void sendContentLengthHeader(int contentLength) throws IOException {
        outputStream.writeBytes("Content-Length: " + contentLength + CRLF);
    }

    private void sendConnectionCloseHeader() throws IOException {
        outputStream.writeBytes("Connection: close" + CRLF);
    }

    private void sendEndOfHeaders() throws IOException {
        outputStream.writeBytes(CRLF);
    }

    private void sendResponseBody(byte[] body) throws IOException {
        outputStream.write(body, 0, body.length);
    }

    private String generateErrorHtml(String statusCode, String statusText, String message) {
        return "<HTML><HEAD><TITLE>" + statusCode + " " + statusText + "</TITLE></HEAD><BODY><H1>" + statusCode + " " + statusText + "</H1><P>" + message + "</P></BODY></HTML>";
    }
}


/**
 * Clase para manejar la lógica de servir archivos estáticos.
 * Incluye la determinación del tipo de contenido y el envío del archivo como respuesta.
 */
class HttpFileHandler {

    public void serveFile(File file, HttpResponse response) throws Exception {
        byte[] fileData = readFileData(file);
        String contentType = determineContentType(file.getName());
        response.sendFileResponse(contentType, fileData);
    }


    private byte[] readFileData(File file) throws IOException {
        return Files.readAllBytes(Paths.get(file.getPath()));
    }

    private String determineContentType(String fileName) {
        if (fileName.endsWith(".htm") || fileName.endsWith(".html")) {
            return "text/html";
        }
        if (fileName.endsWith(".gif")) {
            return "image/gif";
        }
        if (fileName.endsWith(".jpeg") || fileName.endsWith(".jpg")) {
            return "image/jpeg";
        }
        return "application/octet-stream"; // Tipo por defecto
    }
}