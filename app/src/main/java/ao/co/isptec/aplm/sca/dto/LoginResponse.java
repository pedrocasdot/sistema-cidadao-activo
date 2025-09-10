package ao.co.isptec.aplm.sca.dto;

public class LoginResponse {
    private Long id;
    private String username;
    private String fullName;
    private String token;

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public String getToken() { return token; }
}
