package dk.fishery.fisketegn.model;

public class License {
    private String licenseID;
    private String licenseNumber;
    private String type; //"d" = day, "w" = week, "y" = year, "l" = fritidsfisketegn
    private boolean highQuality;
    private String startDate;
    private String endDate;
    private String originalStartDate;
    private boolean status; //true = active, false = inactive
    private boolean deletedFlag = false;
    private boolean groupLicenseFlag;


    public String getLicenseID() {
        return licenseID;
    }

    public void setLicenseID(String licenseID) {
        this.licenseID = licenseID;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public void setLicenseNumber(String licenseNumber) {
        this.licenseNumber = licenseNumber;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isHighQuality() {
        return highQuality;
    }

    public void setHighQuality(boolean highQuality) {
        this.highQuality = highQuality;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getOriginalStartDate() {
        return originalStartDate;
    }

    public void setOriginalStartDate(String originalStartDate) {
        this.originalStartDate = originalStartDate;
    }

    public boolean isStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public boolean isDeletedFlag() {
        return deletedFlag;
    }

    public void setDeletedFlag(boolean deletedFlag) {
        this.deletedFlag = deletedFlag;
    }

    public boolean isGroupLicenseFlag() {
        return groupLicenseFlag;
    }

    public void setGroupLicenseFlag(boolean groupLicenseFlag) {
        this.groupLicenseFlag = groupLicenseFlag;
    }
    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

}
