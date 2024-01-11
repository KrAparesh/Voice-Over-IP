import java.io.*;
import java.net.*;
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
        this.clientsOnServer = new AtomicInteger(1);
    }

    @Override
    public void run() {
        try {
            sock = new ServerSocket(port);
            while (!sock.isClosed()) {
                Socket client = sock.accept();
                System.out.println("Client " + (clientHandlers.size() + 1) + " connected: " + client.getInetAddress());
                VoIPClientHandler handler = new VoIPClientHandler(this, client);
                Thread thread = new Thread(handler);
                thread.start();
            }
        } catch (IOException e) {
            System.out.println("An IOException was encountered");
            shutdown();
        }
    }

    public void remove(VoIPClientHandler clientHandler) {
        if (this.clientHandlers.remove(clientHandler)) {
            this.clientsOnServer.getAndDecrement(); // Fix: Use decrementAndGet() to decrement the counter
        }
    }

    public void add(VoIPClientHandler clientHandler) {
        if (this.clientHandlers.add(clientHandler)) {
            this.clientsOnServer.getAndIncrement(); // Fix: Use incrementAndGet() to increment the counter
        }
    }

    public void shutdown() {
        try {
            System.out.println("The shutdown method was called from server.");
            if (sock != null && !sock.isClosed()) {
                sock.close();
            }
            // Remove all client handlers
            for (VoIPClientHandler handler : clientHandlers) {
                handler.shutdown();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.exit(0);
    }
    

    public static void main(String[] args)throws IOException {
        VoIPServer server = new VoIPServer(2728);
        System.out.println("Server started on address: " + InetAddress.getLocalHost());
        server.start();
    }

    class VoIPClientHandler extends Thread {
    
        private final VoIPServer server;
        private final InputStream in;
        private final OutputStream out;
        private final AudioOutputDevice aout;
    
        public VoIPClientHandler(VoIPServer server, Socket sock) throws IOException {
            this.server = server;
            this.in = new BufferedInputStream(sock.getInputStream());
            this.out = new BufferedOutputStream(sock.getOutputStream());
            this.aout = new AudioOutputDevice(new BufferedInputStream(sock.getInputStream()));
            clientHandlers.add(this);
        }
    
        public void run() {           
            this.aout.start();
        }
    
        public void shutdown() {
            this.server.remove(this);
        }
    }

}


class AudioOutputDevice extends Thread {

    private static AudioFormat defaultFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 8000.0f, 16, 2, 4, 8000.0f, true);

    private SourceDataLine dataLine;
    private AudioFormat format;
    private InputStream stream;

    public AudioOutputDevice(InputStream stream) {
        this(stream, defaultFormat);
    }

    public AudioOutputDevice(InputStream stream, AudioFormat format) {
        this.stream = stream;
        this.format = format;
        this.dataLine = null;
    }

    private boolean open() {
        if (dataLine != null) {
            return true;
        }
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        try {
            dataLine = (SourceDataLine) AudioSystem.getLine(info);
            dataLine.open(format);
            return true;
        } catch (LineUnavailableException e) {
            return false;
        }
    }

    @Override
    public void run() {
        if (this.open()) {
            dataLine.start();
            try {
                while (true) {
                    byte[] buffer = new byte[dataLine.getBufferSize() / 5];
                    int read = stream.read(buffer, 0, buffer.length);
                    dataLine.write(buffer, 0, read);
                }
            } catch (IOException e) {
                shutdown();
            }
        }
    }

    public void shutdown() {
        dataLine.stop();
        dataLine.flush();
        try {
            stream.close();
        } catch (IOException e) {

        }
    }

}