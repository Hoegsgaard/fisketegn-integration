package dk.fishery.fisketegn.model;

import java.util.ArrayList;

public class User {

  private String cpr;
  private String birthDay;
  private String birthMonth;
  private String birthYear;
  private String firstName;
  private String lastName;
  private String email;
  private String address;
  private String zipCode;
  private String country;
  private boolean highQuality;
  private String startDate;
  private String password;
  private String role;
  private String type;
  private ArrayList<String> licenses = new ArrayList<>();
  private String oldEmail;

  public String getCpr() {return cpr;}
  public void setCpr(String cpr) {this.cpr = cpr;}
  public String getBirthDay() {return birthDay;}
  public void setBirthDay(String birthDay) {this.birthDay = birthDay;}
  public String getBirthMonth() {return birthMonth;}
  public void setBirthMonth(String birthMonth) {this.birthMonth = birthMonth;}
  public String getBirthYear() {return birthYear;}
  public void setBirthYear(String birthYear) {this.birthYear = birthYear;}
  public String getFirstName() {return firstName;}
  public void setFirstName(String firstName) {this.firstName = firstName;}
  public String getLastName() {return lastName;}
  public void setLastName(String lastName) {this.lastName = lastName;}
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getAddress() {return address;}
  public void setAddress(String address) {this.address = address;}
  public String getZipCode() {return zipCode;}
  public void setZipCode(String zipCode) {this.zipCode = zipCode;}
  public String getCountry() {return country;}
  public void setCountry(String country) {this.country = country;}
  public boolean isHighQuality() {return highQuality;}
  public void setHighQuality(boolean highQuality) {this.highQuality = highQuality; }
  public String getStartDate() {return startDate;}
  public void setStartDate(String startDate) {this.startDate = startDate; }
  public String getPassword() { return password; }
  public void setPassword(String password) { this.password = password; }
  public String getRole() {return role; }
  public void setRole(String role) {this.role = role; }
  public ArrayList<String> getLicenses() {return licenses;}
  public void addLicense(String license) {this.licenses.add(license);}
  public String getType() {return type;}
  public void setType(String type) {this.type = type;}
  public String getOldEmail() {return oldEmail;}
  public void setOldEmail(String oldEmail) {this.oldEmail = oldEmail;}
}
