package dk.fishery.fisketegn.model;

public class License {
    private String licenseID;
    private String licenseNumber;
    private String type; //"d" = day, "w" = week, "y" = year, "l" = fritidsfisketegn
    private boolean highQuality;
    private String startDate;
    private String originalStartDate;
    private boolean status; //true = active, false = inactive
    private boolean deletedFlag = false;
    private boolean groupLicenseFlag;
}
