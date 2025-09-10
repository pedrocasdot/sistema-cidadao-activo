package ao.co.isptec.aplm.sca.service;

import android.content.Context;
import android.util.Log;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 * Serviço para envio de emails do Sistema Cidadão Activo
 */
public class EmailService {
    private static final String TAG = "EmailService";
    
    // Configurações do servidor SMTP
    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final String SMTP_PORT = "587";
    private static final String EMAIL_FROM = "bpter01@gmail.com";
    private static final String EMAIL_PASSWORD = "vcxbwuhusjxmjqjj"; 
    private static final String EMAIL_TO_GOVERNMENT = "incidentesgoverno@gmail.com";
    
    private final ExecutorService executorService;
    private final Context context;
    
    public EmailService(Context context) {
        this.context = context;
        this.executorService = Executors.newSingleThreadExecutor();
    }
    
    /**
     * Interface para callback do envio de email
     */
    public interface EmailCallback {
        void onSuccess();
        void onError(String error);
    }
    
    /**
     * Envia email de notificação de nova ocorrência para o Estado
     */
    public void enviarEmailNovaOcorrencia(String titulo, String descricao, String urgencia, 
                                         String dataHora, double latitude, double longitude, 
                                         String nomeUsuario, EmailCallback callback) {
        
        executorService.execute(() -> {
            try {
                Properties props = new Properties();
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.host", SMTP_HOST);
                props.put("mail.smtp.port", SMTP_PORT);
                props.put("mail.smtp.ssl.trust", SMTP_HOST);
                
                Session session = Session.getInstance(props, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(EMAIL_FROM, EMAIL_PASSWORD);
                    }
                });
                
                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(EMAIL_FROM));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(EMAIL_TO_GOVERNMENT));
                
                String assunto = "🚨 Nova Ocorrência Registrada - " + urgencia;
                if (urgencia.toLowerCase().contains("urg") || urgencia.toLowerCase().contains("alta")) {
                    assunto = "🔴 URGENTE - " + assunto;
                }
                message.setSubject(assunto);
                
                StringBuilder corpo = new StringBuilder();
                corpo.append("📋 NOVA OCORRÊNCIA REGISTRADA NO SISTEMA CIDADÃO ACTIVO\n");
                corpo.append("═══════════════════════════════════════════════════════\n\n");
                
                corpo.append("📝 TÍTULO: ").append(titulo).append("\n\n");
                corpo.append("📄 DESCRIÇÃO:\n").append(descricao).append("\n\n");
                corpo.append("⚠️  URGÊNCIA: ").append(urgencia).append("\n\n");
                corpo.append("📅 DATA/HORA: ").append(dataHora).append("\n\n");
                corpo.append("📍 LOCALIZAÇÃO:\n");
                corpo.append("   • Latitude: ").append(latitude).append("\n");
                corpo.append("   • Longitude: ").append(longitude).append("\n");
                corpo.append("   • Google Maps: https://maps.google.com/?q=").append(latitude).append(",").append(longitude).append("\n\n");
                corpo.append("👤 REPORTADO POR: ").append(nomeUsuario).append("\n\n");
                
                corpo.append("═══════════════════════════════════════════════════════\n");
                corpo.append("Este email foi enviado automaticamente pelo Sistema Cidadão Activo.\n");
                corpo.append("Por favor, tome as medidas necessárias para resolver esta ocorrência.\n");
                
                message.setText(corpo.toString());
                
                Transport.send(message);
                
                Log.d(TAG, "Email enviado com sucesso para: " + EMAIL_TO_GOVERNMENT);
                
                if (callback != null) {
                    callback.onSuccess();
                }
                
            } catch (MessagingException e) {
                Log.e(TAG, "Erro ao enviar email", e);
                if (callback != null) {
                    callback.onError("Erro ao enviar email: " + e.getMessage());
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro inesperado ao enviar email", e);
                if (callback != null) {
                    callback.onError("Erro inesperado: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Envia email de notificação de partilha de ocorrência
     */
    public void enviarEmailPartilhaOcorrencia(String titulo, String descricao, String nomeRemetente, 
                                            String nomeDestinatario, EmailCallback callback) {
        
        executorService.execute(() -> {
            try {
                Properties props = new Properties();
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.host", SMTP_HOST);
                props.put("mail.smtp.port", SMTP_PORT);
                props.put("mail.smtp.ssl.trust", SMTP_HOST);
                
                Session session = Session.getInstance(props, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(EMAIL_FROM, EMAIL_PASSWORD);
                    }
                });
                
                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(EMAIL_FROM));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(EMAIL_TO_GOVERNMENT));
                message.setSubject("📤 Ocorrência Partilhada - " + titulo);
                
                StringBuilder corpo = new StringBuilder();
                corpo.append("📤 OCORRÊNCIA PARTILHADA NO SISTEMA CIDADÃO ACTIVO\n");
                corpo.append("═══════════════════════════════════════════════════════\n\n");
                
                corpo.append("📝 TÍTULO: ").append(titulo).append("\n\n");
                corpo.append("📄 DESCRIÇÃO:\n").append(descricao).append("\n\n");
                corpo.append("👤 PARTILHADO POR: ").append(nomeRemetente).append("\n");
                corpo.append("👥 PARTILHADO COM: ").append(nomeDestinatario).append("\n\n");
                
                corpo.append("═══════════════════════════════════════════════════════\n");
                corpo.append("Esta ocorrência foi partilhada entre cidadãos via WiFi Direct.\n");
                corpo.append("Isso indica maior visibilidade e preocupação da comunidade.\n");
                
                message.setText(corpo.toString());
                
                Transport.send(message);
                
                Log.d(TAG, "Email de partilha enviado com sucesso");
                
                if (callback != null) {
                    callback.onSuccess();
                }
                
            } catch (MessagingException e) {
                Log.e(TAG, "Erro ao enviar email de partilha", e);
                if (callback != null) {
                    callback.onError("Erro ao enviar email: " + e.getMessage());
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro inesperado ao enviar email de partilha", e);
                if (callback != null) {
                    callback.onError("Erro inesperado: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Libera recursos do serviço
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}
