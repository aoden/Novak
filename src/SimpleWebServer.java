/********************************************************************************
 * 2
 * 3 SimpleWebServer.java
 * 4
 * 5
 * 6 This toy web server is used to illustrate security vulnerabilities.
 * 7 This web server only supports extremely simple HTTP GET requests.
 * 8
 * 9 This file
 * 10
 * 11
 *******************************************************************************/

import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.StringTokenizer;

public class SimpleWebServer {

    /* Run the HTTP server on this TCP port. */
    private static final int PORT = 8080;
    /* The socket used to process incoming connections
        25 from web clients. */
    private static ServerSocket dServerSocket;
    // new - default log file
    private String logFileName = "log.log";
    //new - file size limit in KB
    private long sizeLimit = 10000;

    //new -username and password for authentication
    private String username = "admin";
    private String password = "admin";

    public SimpleWebServer() throws Exception {
        dServerSocket = new ServerSocket(PORT);
    }

    /* This method is called when the program is run from
        the command line.
    */
    public static void main(String argv[]) throws Exception {

         /* Create a SimpleWebServer object and run it. */
        SimpleWebServer sws = new SimpleWebServer();
        sws.run();
    }

    public void storeFile(BufferedReader br,
                          OutputStreamWriter osw,
                          String pathname) throws Exception {
        FileWriter fw = null;
        pathname = pathname.replace("/", "");
        try {
            fw = new FileWriter(pathname);
            String s = br.readLine();
            while (s != null) {
                fw.write(s);
                s = br.readLine();
            }
            fw.close();
            osw.write("HTTP/1.0 201 Created");
        } catch (Exception e) {
            osw.write("HTTP/1.0 500 Internal Server Error");
        }
    }

    public void logEntry(String filename, String record) throws IOException {
        FileWriter fw = new FileWriter(filename, true);
        fw.write(getTimestamp() + " " + record);
        fw.close();
    }

    public String getTimestamp() {
        return (new Date()).toString();
    }

    public void run() throws Exception {
        while (true) {
            /* Wait for a connection from a client. */
            Socket s = dServerSocket.accept();

             /* Then, process the client's request. */
            processRequest(s);
        }
    }

    /* Reads the HTTP request from the client and
        responds with the file the user requested or
        an HTTP error code. */
    public void processRequest(Socket s) throws Exception {
        /* Used to read data from the client. */
        BufferedReader br =
                new BufferedReader(
                        new InputStreamReader(s.getInputStream()));

        /* Used to write data to the client. */
        OutputStreamWriter osw =
                new OutputStreamWriter(s.getOutputStream());

                /* Read the HTTP request from the client. */
        String request = br.readLine();

        String temp = null;
        boolean valid = false;
        while ((temp = br.readLine()) != null) {

            if (temp.isEmpty()) {
                break;
            }
            StringTokenizer stringTokenizer = new StringTokenizer(temp, ":");
            if (stringTokenizer.hasMoreTokens()) {

                String key = stringTokenizer.nextToken().trim();
                String value = stringTokenizer.nextToken().trim();

                if (key.startsWith("Authorization") && checkLogin(value)) {
                    valid = true;
                }
            }
        }

        if (!valid) {
            osw.write("HTTP/1.0 401 Not Authorized\r\n");
            osw.write("WWW-Authenticate: Basic\r\n");
            osw.write("\r\n");
            osw.close();
            return;
        }

        String command = null;
        String pathname = null;
        // new - store message
        String msg = "";

        /* Parse the HTTP request. */
        StringTokenizer st =
                new StringTokenizer(request, " ");

        command = st.nextToken();
        pathname = st.nextToken();
        if (command.equals("GET")) {
             /* If the request is a GET,
                try to respond with the file
                the user is requesting.
             */
            //new - call new thread to do the job
            new Thread(new ServerWorker(osw, pathname, logFileName, br)).start();
        } else if (command.equals("PUT")) {

            storeFile(br, osw, pathname); // new - store the file
            osw.write("\r\n");
            osw.close();
            return;
        } else {
             /* If the request is a NOT a GET,
                 return an error saying this server
                 does not implement the requested command. */
            //new - log the request
            msg = "HTTP/1.0 501 Not Implemented\n\n";
            logEntry(msg, logFileName);
            osw.write(msg);
            osw.close();
        }
    }

    private boolean checkLogin(String value) {

        try {
            if (value == null) {

                return false;
            }
            if (value.isEmpty()) {

                return false;
            }
            value = value.substring(5).trim();
            byte[] credentialsData = Base64.decode(value);
            String temp = new String(credentialsData);
            String[] s = temp.split(":");
            return username.equals(s[0]) && password.equals(s[1]);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * New - check filesize with limit
     *
     * @param pathName path to file
     * @return true if file size is valid, otherwise false
     * @throws IOException
     */
    private boolean checkFileSize(String pathName) throws IOException {

        File file = new File(pathName);
        return file.length() <= sizeLimit;
    }

    public void serveFile(OutputStreamWriter osw,
                          String pathname) throws Exception {
        FileReader fr = null;
        int c = -1;
        StringBuffer sb = new StringBuffer();

         /* Remove the initial slash at the beginning
             of the pathname in the request. */
        if (pathname.charAt(0) == '/')
            pathname = pathname.substring(1);

         /* If there was no filename specified by the
             client, serve the "index.html" file. */
        if (pathname.equals(""))
            pathname = "index.html";

        //new - check file zize
        if (!checkFileSize(pathname)) {

            osw.write("HTTP/1.0 403 Forbidden\n\n");
            return;
        }
         /* Try to open file specified by pathname. */
        try {
            fr = new FileReader(pathname);
            c = fr.read();
        } catch (Exception e) {
             /* If the file is not found, return the
            appropriate HTTP response code. */

            osw.write("HTTP/1.0 404 Not Found\n\n");
            return;
        }

         /* If the requested file can be successfully opened
             and read, then return an OK response code and
             send the contents of the file. */
        osw.write("HTTP/1.0 200 OK\n\n");
        while (c != -1) {
            sb.append((char) c);
            c = fr.read();
        }
        osw.write(sb.toString());
    }

    /**
     * new - this class for implement multi-thread download
     */
    class ServerWorker implements Runnable {

        OutputStreamWriter osw;
        String pathname;
        String logFileName;
        BufferedReader br;

        public ServerWorker(OutputStreamWriter osw,
                            String pathname,
                            String logFileName,
                            BufferedReader br) {
            this.osw = osw;
            this.pathname = pathname;
            this.logFileName = logFileName;
            this.br = br;
        }

        @Override
        public void run() {
            try {
                serveFile(osw, pathname);
                /* Close the connection to the client. */
                osw.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}