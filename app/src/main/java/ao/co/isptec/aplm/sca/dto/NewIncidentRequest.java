package ao.co.isptec.aplm.sca.dto;

import com.google.gson.Gson;

public class NewIncidentRequest {
    private String title;
    private String description;
    private Double latitude;
    private Double longitude;
    private String datetime; // ISO-8601 string
    private Boolean urgency;  // boolean as backend expects
    private Long userId;

    public NewIncidentRequest(String title, String description, Double latitude, Double longitude,
                              String datetime, Boolean urgency, Long userId) {
        this.title = title;
        this.description = description;
        this.latitude = latitude;
        this.longitude = longitude;
        this.datetime = datetime;
        this.urgency = urgency;
        this.userId = userId;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    // Getters
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getDatetime() { return datetime; }
    public Boolean getUrgency() { return urgency; }
    public Long getUserId() { return userId; }
}
