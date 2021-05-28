package dk.fishery.fisketegn.processors;

import dk.fishery.fisketegn.model.User;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.util.json.JsonObject;

import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;

public class SendEmailProcessor implements Processor {
  @Override
  public void process(Exchange exchange) throws Exception {
    String to = (String) exchange.getProperty("userEmail");
    JsonObject license = (JsonObject) exchange.getProperty("license");
    User user = (User) exchange.getProperty("oldBody");
    String from = "fisketegndtu@gmail.com";
    String password = (String) exchange.getProperty("emailPass");
    Properties prop = new Properties();
    prop.put("mail.smtp.host", "smtp.gmail.com");
    prop.put("mail.smtp.post", "465");
    prop.put("mail.smtp.auth", "true");
    prop.put("mail.smtp.socketFactory.port", "465");
    prop.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");

    Session session = Session.getInstance(prop, new javax.mail.Authenticator(){
      protected PasswordAuthentication getPasswordAuthentication(){
        return new PasswordAuthentication(from, password);
      }
    });

    try {
      MimeMessage message = new MimeMessage(session);
      message.setFrom(new InternetAddress(from));
      message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
      message.setSubject("Tillykke med dit fisketegn");
      String text = "";
      if(license.getString("type").equals("f")){
        text = text + "Fritidsfisketegn\n";
      }else{
        text = text + "Lystfisketegn\n";
      }
      text = text + "Fiskerinummer   -   " + license.getString("licenseNumber") + "\n";
      text = text + "Fiskekorttype   -   ";
      switch (license.getString("type")) {
        case "f":
          text = text + "Fritidsfisker\n";
          break;
        case "y":
          text = text + "Lystfisker - 1 år\n";
          break;
        case "w":
          text = text + "Lystfisker - 1 uge\n";
          break;
        case "d":
          text = text + "Lystfisker - 1 day\n";
          break;
      }
      text = text + "Fornavn   -   " + user.getFirstName() + "\n";
      text = text + "Efternavn   -   " + user.getLastName() + "\n";
      text = text + "Adresse   -   " + user.getAddress() + "\n";
      text = text + "Postnummer   -    " + user.getZipCode() + "\n";
      text = text + "Land    -    " + user.getCountry() + "\n";
      text = text + "Startdate    -    " + license.getString("startDate") + "\n";
      text = text + "Gyldigt til    -    " + license.getString("endDate") + "\n";
      message.setText(text);
      Transport.send(message);
    }catch (MessagingException e){
      e.printStackTrace();
    }
  }



}
