import java.net.*;
import java.io.*;
 
/**
 * This program demonstrates a simple TCP/IP socket client that reads input
 * from the user and prints echoed message from the server.
 *
 * @author www.codejava.net
 */
public class JavaClient {
 
    public static void main(String[] args) {
        
 
        String hostname = "131.128.54.59";
        int port = 8888;
        String pos = null;

        byte[] bytes = new byte[10240];
        byte[] size_byte = new byte[16];
        int recv_size = 0;
        int pkt_num = 0;

        try (Socket socket = new Socket(hostname, port)) {
 
            OutputStream out = socket.getOutputStream();
            PrintWriter output = new PrintWriter(out);
            InputStream input = socket.getInputStream();
            DataInputStream dIn = new DataInputStream(input);

            Console console = System.console();
            String text = "run";

            do {
                pos = String.format("%5d",0)+","+String.format("%5d",0);
                output.print(pos);
                output.flush();

                
                
                int image_size = dIn.readInt(); 
                System.out.println("Image size: " + image_size);

                byte[] img_bytes = new byte[image_size];

                                   
                if(image_size>0) {
                    dIn.readFully(img_bytes, 0, img_bytes.length); // read the message
                }

 
            } while (!text.equals("bye"));
 
            socket.close();
 
        } catch (UnknownHostException ex) {
 
            System.out.println("Server not found: " + ex.getMessage());
 
        } catch (IOException ex) {
 
            System.out.println("I/O error: " + ex.getMessage());
        }
    }
}