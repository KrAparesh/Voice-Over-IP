import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sound.sampled.*;


public class VoIPServer extends Thread {

    protected ServerSocket sock;
    private int port;
    private List<VoIPClientHandler> clientHandlers;
    private AtomicInteger clientsOnServer;

    public VoIPServer(int port) {
        this.port = port;
        this.clientHandlers = new ArrayList<>();
        this.clientsOnServer = new AtomicInteger(0);
    }

    public static void main(String[] args) {
        VoIPServer server = new VoIPServer(2728);
        server.start();
        System.out.println("Server started on host + " + server.getName() );
    }

    @Override
    public void run() {
        VoIPClientHandler handler;
        try {
            sock = new ServerSocket(port);
            System.out.println(InetAddress.getLocalHost());
            System.out.println(sock);
            while (true) {
                Socket client = sock.accept();
                handler = new VoIPClientHandler(this, client);
                add(handler);
                System.out.println("[client no. " + clientsOnServer.get() + " connected.]"); // Fix: Use clientsOnServer.get() to get the current value
                handler.start();
            }
        } catch (IOException e) {
            shutdown();
        }
    }

    public void broadcast(byte[] bytes) {
        for (int i = 0; i < this.clientHandlers.size(); i++) {
            this.clientHandlers.get(i).sendMessage(bytes);
        }
    }

    public void remove(VoIPClientHandler clientHandler) {
        if (this.clientHandlers.remove(clientHandler)) {
            this.clientsOnServer.decrementAndGet(); // Fix: Use decrementAndGet() to decrement the counter
        }
    }

    public void add(VoIPClientHandler clientHandler) {
        if (this.clientHandlers.add(clientHandler)) {
            this.clientsOnServer.incrementAndGet(); // Fix: Use incrementAndGet() to increment the counter
        }
    }

    public void shutdown() {
        try {
            sock.close();
        } catch (IOException e) {
        }
        System.exit(0);
    }

}

class VoIPClientHandler extends Thread {

    private final VoIPServer server;
    private final InputStream in;
    private final OutputStream out;

    public VoIPClientHandler(VoIPServer server, Socket sock) throws IOException {
        this.server = server;
        this.in = new BufferedInputStream(sock.getInputStream());
        this.out = new BufferedOutputStream(sock.getOutputStream());
    }

    public void run() {
        byte[] bytes = new byte[64];
        try {
            AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 8000.0f, 16, 2, 4, 8000.0f, true);
            SourceDataLine speakers = AudioSystem.getSourceDataLine(format);
            speakers.open(format);
            speakers.start();

            while (this.in.read(bytes) != -1) {
                // System.out.print(Arrays.toString(bytes));
                this.server.broadcast(bytes);
                speakers.write(bytes, 0, bytes.length);
            }
            speakers.drain();
            speakers.close();
        } catch (IOException | LineUnavailableException e) {
            shutdown();
        }
    }

    public void sendMessage(byte[] bytes) {
        try {
            this.out.write(bytes);
            this.out.flush();
    
        } catch (IOException e) {
            e.printStackTrace();
            shutdown();
        }
    }

    public void shutdown() {
        this.server.remove(this);
    }

    
}
