package ao.co.isptec.aplm.sca.model;

import java.io.Serializable;
import java.util.Date;

public class Ocorrencia implements Serializable {
    private int id;
    private String descricao;
    private String localizacaoSimbolica;
    private double latitude;
    private double longitude;
    private Date dataHora;
    private boolean urgente;
    private int contadorPartilha;
    private String fotoPath;
    private String videoPath;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public String getLocalizacaoSimbolica() { return localizacaoSimbolica; }
    public void setLocalizacaoSimbolica(String localizacaoSimbolica) { this.localizacaoSimbolica = localizacaoSimbolica; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public Date getDataHora() { return dataHora; }
    public void setDataHora(Date dataHora) { this.dataHora = dataHora; }

    public boolean isUrgente() { return urgente; }
    public void setUrgente(boolean urgente) { this.urgente = urgente; }

    public int getContadorPartilha() { return contadorPartilha; }
    public void setContadorPartilha(int contadorPartilha) { this.contadorPartilha = contadorPartilha; }

    public String getFotoPath() { return fotoPath; }
    public void setFotoPath(String fotoPath) { this.fotoPath = fotoPath; }

    public String getVideoPath() { return videoPath; }
    public void setVideoPath(String videoPath) { this.videoPath = videoPath; }

    public String getUrgenciaTexto() {
        return urgente ? "URGENTE" : "Normal";
    }

    // Convenience method to increment share counter
    public void incrementarPartilha() {
        this.contadorPartilha = this.contadorPartilha + 1;
    }
}
