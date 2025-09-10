package ao.co.isptec.aplm.sca.dto;

public class LoginRequest {
    private String username;
    private String password;
    private String deviceInfo;
    private String ipAddress;

    public LoginRequest(String username, String password, String deviceInfo, String ipAddress) {
        this.username = username;
        this.password = password;
        this.deviceInfo = deviceInfo;
        this.ipAddress = ipAddress;
    }

    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getDeviceInfo() { return deviceInfo; }
    public String getIpAddress() { return ipAddress; }
}
