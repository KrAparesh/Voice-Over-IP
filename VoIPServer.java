import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sound.sampled.*;



// TODO: ADD MESSAGING SUPPORT
//       HANDLE ON CLIENT CLOSE EVENT


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
            System.out.println(InetAddress.getLocalHost());
            System.out.println(sock);
            while (!sock.isClosed()) {
                Socket client = sock.accept();
                System.out.println("[client no. " + clientsOnServer.get() + " connected]");
                VoIPClientHandler handler = new VoIPClientHandler(this, client);
                add(handler);
                Thread thread = new Thread(handler);
                thread.start();
            }
        } catch (IOException e) {
            System.out.println("An IOException was encountered");
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
    

    public static void main(String[] args) {
        VoIPServer server = new VoIPServer(2728);
        server.start();
        System.out.println("Server started on host + " + server.getName());
    }

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
    }

    public void run() {
        // byte[] bytes = new byte[64];
        // try {
        //     while (this.in.read(bytes) != 0) {
        //         System.out.println(Arrays.toString(bytes));
        //         this.server.broadcast(bytes);
        //     }
        // } catch (IOException e) {
        //     shutdown();
        // }
        this.aout.start();
    }

    public void sendMessage(byte[] bytes) {
        try {
            this.out.write(bytes);
            // this.aout.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void shutdown() {
        this.server.remove(this);
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