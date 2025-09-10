package ao.co.isptec.aplm.sca;

import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.UnknownHostException;

import pt.inesc.termite.wifidirect.sockets.SimWifiP2pSocket;
import pt.inesc.termite.wifidirect.sockets.SimWifiP2pSocketServer;

/**
 * Gerenciador de comunicações P2P
 * Baseado no projeto Termite-WifiP2P-MsgSender
 */
public class P2PCommManager {
    
    private static final String TAG = "P2PCommManager";
    private static final int DEFAULT_PORT = 10001;
    
    private SimWifiP2pSocketServer mServerSocket;
    private SimWifiP2pSocket mClientSocket;
    private IncomingCommTask mIncomingTask;
    private P2PMessageListener mMessageListener;
    
    public interface P2PMessageListener {
        void onMessageReceived(String message);
        void onConnectionEstablished();
        void onConnectionFailed(String error);
        void onMessageSent();
        void onMessageSendFailed(String error);
    }
    
    public P2PCommManager(P2PMessageListener listener) {
        this.mMessageListener = listener;
    }
    
    /**
     * Inicia o servidor para receber mensagens
     */
    public void startServer() {
        if (mIncomingTask != null && !mIncomingTask.isCancelled()) {
            mIncomingTask.cancel(true);
        }
        
        mIncomingTask = new IncomingCommTask();
        mIncomingTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        Log.d(TAG, "Server started on port " + DEFAULT_PORT);
    }
    
    /**
     * Para o servidor
     */
    public void stopServer() {
        if (mIncomingTask != null && !mIncomingTask.isCancelled()) {
            mIncomingTask.cancel(true);
        }
        
        if (mServerSocket != null) {
            try {
                mServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing server socket", e);
            }
            mServerSocket = null;
        }
        Log.d(TAG, "Server stopped");
    }
    
    /**
     * Conecta a um dispositivo remoto
     */
    public void connectToDevice(String ipAddress) {
        new OutgoingCommTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, ipAddress);
    }
    
    /**
     * Envia uma mensagem para o dispositivo conectado
     */
    public void sendMessage(String message) {
        if (mClientSocket != null) {
            new SendCommTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, message);
        } else {
            if (mMessageListener != null) {
                mMessageListener.onMessageSendFailed("Não conectado a nenhum dispositivo");
            }
        }
    }
    
    /**
     * Desconecta do dispositivo atual
     */
    public void disconnect() {
        if (mClientSocket != null) {
            try {
                mClientSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing client socket", e);
            }
            mClientSocket = null;
        }
    }
    
    /**
     * Limpa todos os recursos
     */
    public void cleanup() {
        stopServer();
        disconnect();
    }
    
    /**
     * Task para receber mensagens (servidor)
     */
    private class IncomingCommTask extends AsyncTask<Void, String, Void> {
        
        @Override
        protected Void doInBackground(Void... params) {
            Log.d(TAG, "IncomingCommTask started");
            
            try {
                mServerSocket = new SimWifiP2pSocketServer(DEFAULT_PORT);
            } catch (IOException e) {
                Log.e(TAG, "Error creating server socket", e);
                return null;
            }
            
            while (!Thread.currentThread().isInterrupted() && !isCancelled()) {
                try {
                    SimWifiP2pSocket socket = mServerSocket.accept();
                    try {
                        BufferedReader sockIn = new BufferedReader(
                                new InputStreamReader(socket.getInputStream()));
                        String message = sockIn.readLine();
                        if (message != null) {
                            publishProgress(message);
                        }
                        socket.getOutputStream().write(("\n").getBytes());
                    } catch (IOException e) {
                        Log.d(TAG, "Error reading socket: " + e.getMessage());
                    } finally {
                        socket.close();
                    }
                } catch (IOException e) {
                    if (!isCancelled()) {
                        Log.d(TAG, "Error accepting socket: " + e.getMessage());
                    }
                    break;
                }
            }
            return null;
        }
        
        @Override
        protected void onProgressUpdate(String... values) {
            if (mMessageListener != null && values.length > 0) {
                mMessageListener.onMessageReceived(values[0]);
            }
        }
    }
    
    /**
     * Task para conectar a um dispositivo (cliente)
     */
    private class OutgoingCommTask extends AsyncTask<String, Void, String> {
        
        @Override
        protected String doInBackground(String... params) {
            try {
                mClientSocket = new SimWifiP2pSocket(params[0], DEFAULT_PORT);
                return null; // Success
            } catch (UnknownHostException e) {
                return "Host desconhecido: " + e.getMessage();
            } catch (IOException e) {
                return "Erro de IO: " + e.getMessage();
            }
        }
        
        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                // Connection failed
                if (mMessageListener != null) {
                    mMessageListener.onConnectionFailed(result);
                }
            } else {
                // Connection successful
                if (mMessageListener != null) {
                    mMessageListener.onConnectionEstablished();
                }
            }
        }
    }
    
    /**
     * Task para enviar mensagem
     */
    private class SendCommTask extends AsyncTask<String, Void, String> {
        
        @Override
        protected String doInBackground(String... messages) {
            try {
                if (mClientSocket != null) {
                    mClientSocket.getOutputStream().write((messages[0] + "\n").getBytes());
                    BufferedReader sockIn = new BufferedReader(
                            new InputStreamReader(mClientSocket.getInputStream()));
                    sockIn.readLine();
                    mClientSocket.close();
                    mClientSocket = null;
                    return null; // Success
                } else {
                    return "Socket não disponível";
                }
            } catch (IOException e) {
                return "Erro ao enviar: " + e.getMessage();
            }
        }
        
        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                // Send failed
                if (mMessageListener != null) {
                    mMessageListener.onMessageSendFailed(result);
                }
            } else {
                // Send successful
                if (mMessageListener != null) {
                    mMessageListener.onMessageSent();
                }
            }
        }
    }
}
