import java.util.*;
import java.io.*;
import java.net.*;
import javax.sound.sampled.*;

public class VoIPClient extends Thread {

    private Socket sock;
    private AudioInputDevice in;
    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;


    public VoIPClient(InetAddress host, int port) throws IOException {
        try {
            this.sock = new Socket(host, port);
            this.in = new AudioInputDevice(new BufferedOutputStream(sock.getOutputStream()));
            this.bufferedReader = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream()));
        } catch (Exception e) {
            closeEverything(sock, bufferedReader, bufferedWriter);
        }
    }

    
    public void run() {
        try {
            sock.setKeepAlive(true);
        } catch (SocketException e) {
            e.printStackTrace();
        }
        // this.out.start();
        this.in.start();
    }
    
    public static void main(String[] args) {
        Scanner sc = new Scanner (System.in);
        InetAddress host = null;
        try { 
            System.out.print("Enter the host name to connect to: ");
            // host = InetAddress.getByName("Inspiron-5320");
            host = InetAddress.getByName(sc.nextLine());
            System.out.println("Successfully connected to : " + host);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        try {
            VoIPClient client = new VoIPClient(host, 2728);
            client.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // Method to tackle Execptions on sending texts.
    public void closeEverything(Socket socket, BufferedReader bufferedReader, BufferedWriter bufferedWriter) {
        try {
            if(bufferedReader != null)
            bufferedReader.close();
            if(bufferedWriter != null)
            bufferedWriter.close();
            if(socket != null)
            socket.close();
            if(in != null) 
            this.in.shutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class AudioInputDevice extends Thread {

    private static AudioFormat defaultFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 8000.0f, 16, 2, 4, 8000.0f, true);

    private AudioFormat format;
    private TargetDataLine dataLine;
    private OutputStream stream;

    public AudioInputDevice(OutputStream stream) {
        this(stream, defaultFormat);
    }

    public AudioInputDevice(OutputStream stream, AudioFormat format) {
        this.stream = stream;
        this.format = format;
        this.dataLine = null;
    }

    public static Map<String, TargetDataLine.Info> listMicrophones() {
        Map<String, TargetDataLine.Info> mics = new HashMap<>();
        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        for (Mixer.Info mixerInfo : mixerInfos) {
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            Line.Info[] lineInfos = mixer.getTargetLineInfo();
            if (lineInfos.length >= 1 && lineInfos[0].getLineClass().equals(TargetDataLine.class)) {
                mics.put(mixerInfo.getName(), (TargetDataLine.Info) lineInfos[0]);
            }
        }
        return mics;
    }

    private boolean open() {
        if (dataLine != null) {
            return true;
        }
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        try {
            dataLine = (TargetDataLine) AudioSystem.getLine(info);
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
                    int read = dataLine.read(buffer, 0, buffer.length);
                    stream.write(buffer, 0, read);
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
