package dk.fishery.fisketegn.model;

import com.mongodb.BasicDBObject;
import org.bson.Document;

import java.util.ArrayList;

public class User {

  private String cpr;
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

  public User(){

  }

  public User(Document input){
    this.cpr = input.getString("cpr");
    this.firstName = input.getString("firstName");
    this.lastName = input.getString("lastName");
    this.email = input.getString("email");
    this.address = input.getString("address");
    this.zipCode = input.getString("zipCode");
    this.country = input.getString("country");
    this.password = input.getString("password");
    this.role = input.getString("role");
    this.licenses = (ArrayList<String>) input.get("licenses");
  }

  public BasicDBObject getDbObject(){
    BasicDBObject user = new BasicDBObject();
    user.put("cpr", this.cpr);
    user.put("firstName", this.firstName);
    user.put("lastName", this.lastName);
    user.put("email", this.email);
    user.put("address", this.address);
    user.put("zipCode", this.zipCode);
    user.put("country", this.country);
    user.put("password", this.password);
    user.put("role", this.role);
    return user;
  }
}
