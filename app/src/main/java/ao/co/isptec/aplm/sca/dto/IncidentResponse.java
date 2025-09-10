package ao.co.isptec.aplm.sca.dto;

public class IncidentResponse {
    private Long id;
    private String description;
    private Double latitude;
    private Double longitude;
    private Boolean urgency;
    private String datetime;
    private String dbPhotoFilename;
    private String dbVideoFilename;
    private Long userId;
    private String title;

    public Long getId() { return id; }
    public String getDescription() { return description; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Boolean getUrgency() { return urgency; }
    public String getDatetime() { return datetime; }
    public String getDbPhotoFilename() { return dbPhotoFilename; }
    public String getDbVideoFilename() { return dbVideoFilename; }
    public Long getUserId() { return userId; }
    public String getTitle() { return title; }
}
